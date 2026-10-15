package com.frandm.justenoughbackups.backup.storage.remote;

import com.frandm.justenoughbackups.config.BackupConfig;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class S3ObjectStore implements RemoteObjectStore {
    private final String bucket;
    private final S3AsyncClient client;
    private final ExecutorService streamExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "JEB-S3-stream");
        thread.setDaemon(true);
        return thread;
    });

    S3ObjectStore(BackupConfig.RemoteStorage config) throws IOException {
        bucket = config.s3Bucket.trim();
        if (bucket.isBlank()) throw new IOException("Remote S3 bucket is not configured.");
        String accessKey = envOrConfig("JEB_S3_ACCESS_KEY_ID", config.s3AccessKey);
        String secretKey = envOrConfig("JEB_S3_SECRET_ACCESS_KEY", config.s3SecretKey);
        if (accessKey.isBlank() || secretKey.isBlank()) throw new IOException("S3 credentials are missing.");
        var builder = S3AsyncClient.builder()
                .region(Region.of(config.s3Region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .multipartEnabled(true);
        if (!config.s3Endpoint.isBlank()) {
            try {
                URI endpoint = URI.create(config.s3Endpoint.trim());
                if (!"https".equalsIgnoreCase(endpoint.getScheme())
                        && !("http".equalsIgnoreCase(endpoint.getScheme()) && isLoopback(endpoint.getHost()))) {
                    throw new IOException("S3 endpoint must use HTTPS (HTTP is allowed only for localhost). ");
                }
                builder.endpointOverride(endpoint);
            } catch (IllegalArgumentException exception) {
                throw new IOException("Invalid S3 endpoint URL.", exception);
            }
        }
        client = builder.build();
    }

    @Override
    public List<ObjectInfo> list(String prefix) throws IOException {
        List<ObjectInfo> result = new ArrayList<>();
        String token = null;
        try {
            do {
                var response = client.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(bucket).prefix(prefix).continuationToken(token).build()).join();
                response.contents().forEach(object -> result.add(new ObjectInfo(object.key(), object.size(), object.lastModified())));
                token = response.nextContinuationToken();
            } while (token != null && !token.isBlank());
            return result;
        } catch (CompletionException exception) {
            throw ioFailure("Unable to list S3 backups", exception);
        }
    }

    @Override
    public void put(String key, InputStream input) throws IOException {
        try {
            AsyncRequestBody body = AsyncRequestBody.fromInputStream(input, null, streamExecutor);
            client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(), body).join();
        } catch (RuntimeException exception) {
            throw ioFailure("Unable to upload S3 object", exception);
        }
    }

    @Override
    public InputStream get(String key) throws IOException {
        try {
            return client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build(),
                    software.amazon.awssdk.core.async.AsyncResponseTransformer.toBlockingInputStream()).join();
        } catch (CompletionException exception) {
            throw ioFailure("Unable to download S3 object", exception);
        }
    }

    @Override
    public void delete(String key) throws IOException {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build()).join();
        } catch (CompletionException exception) {
            throw ioFailure("Unable to delete S3 object", exception);
        }
    }

    @Override
    public void move(String sourceKey, String targetKey) throws IOException {
        String source = bucket + "/" + sourceKey.replace("%", "%25").replace(" ", "%20");
        try {
            client.copyObject(CopyObjectRequest.builder().sourceBucket(bucket).sourceKey(sourceKey)
                    .destinationBucket(bucket).destinationKey(targetKey).build()).join();
            delete(sourceKey);
        } catch (RuntimeException exception) {
            throw ioFailure("Unable to publish S3 object", exception);
        }
    }

    @Override
    public void close() {
        streamExecutor.shutdownNow();
        client.close();
    }

    private static String envOrConfig(String environmentKey, String configured) {
        String environmentValue = System.getenv(environmentKey);
        return environmentValue == null || environmentValue.isBlank() ? configured.trim() : environmentValue.trim();
    }

    private static boolean isLoopback(String host) {
        return host != null && (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("::1"));
    }

    private static IOException ioFailure(String action, Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) cause = cause.getCause();
        String detail = cause instanceof S3Exception s3 ? s3.awsErrorDetails().errorMessage() : cause.getMessage();
        return new IOException(action + (detail == null || detail.isBlank() ? "." : ": " + detail), cause);
    }
}