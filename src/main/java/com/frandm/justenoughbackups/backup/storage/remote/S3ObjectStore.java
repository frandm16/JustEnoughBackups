package com.frandm.justenoughbackups.backup.storage.remote;

import com.frandm.justenoughbackups.WorldBackupMod;
import com.frandm.justenoughbackups.config.BackupConfig;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.client.config.SdkAdvancedAsyncClientOption;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.SdkEventLoopGroup;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.UploadPartCopyRequest;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.UploadPartCopyResponse;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.core.exception.SdkClientException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class S3ObjectStore implements RemoteObjectStore {
    private static final long SINGLE_COPY_LIMIT_BYTES = 5_000_000_000L;
    private static final long COPY_PART_SIZE_BYTES    =   512_000_000L; // 512 MB por parte (límite S3: 5 MB–5 GiB)
    private final String bucket;
    private final S3AsyncClient client;
    private final ExecutorService streamExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "JEB-S3-stream");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService completionExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "JEB-S3-completion");
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
                .requestChecksumCalculation(config.s3Endpoint.isBlank()
                        ? RequestChecksumCalculation.WHEN_SUPPORTED : RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(config.s3Endpoint.isBlank()
                        ? ResponseChecksumValidation.WHEN_SUPPORTED : ResponseChecksumValidation.WHEN_REQUIRED)
                .overrideConfiguration(software.amazon.awssdk.core.client.config.ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofHours(6)).build())
                .httpClientBuilder(NettyNioAsyncHttpClient.builder()
                        .eventLoopGroupBuilder(SdkEventLoopGroup.builder().numberOfThreads(2))
                        .maxConcurrency(16)
                        .maxPendingConnectionAcquires(64)
                        .connectionTimeout(Duration.ofSeconds(15))
                        .connectionAcquisitionTimeout(Duration.ofSeconds(60))
                        .readTimeout(Duration.ofSeconds(45))
                        .writeTimeout(Duration.ofSeconds(45)))
                .asyncConfiguration(configuration -> configuration.advancedOption(
                        SdkAdvancedAsyncClientOption.FUTURE_COMPLETION_EXECUTOR, completionExecutor))
                .multipartEnabled(true);
        if (!config.s3Endpoint.isBlank()) {
            try {
                URI endpoint = URI.create(config.s3Endpoint.trim());
                if (endpoint.getHost() == null || endpoint.getUserInfo() != null
                        || endpoint.getQuery() != null || endpoint.getFragment() != null) {
                    throw new IOException("S3 endpoint must be a URL without credentials, query, or fragment.");
                }
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
            // AWS SDK 2.31.x applies its default CRC32 request checksum when supported by the endpoint.
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
        try {
            long size = client.headObject(HeadObjectRequest.builder().bucket(bucket).key(sourceKey).build())
                    .join().contentLength();
            if (size > SINGLE_COPY_LIMIT_BYTES) {
                multipartCopy(sourceKey, targetKey, size);
            } else {
                client.copyObject(CopyObjectRequest.builder().sourceBucket(bucket).sourceKey(sourceKey)
                        .destinationBucket(bucket).destinationKey(targetKey).build()).join();
            }
            try {
                delete(sourceKey);
            } catch (IOException cleanupFailure) {
                WorldBackupMod.LOGGER.warn("S3 object was copied but its source could not be removed; leaving an orphan at {}.", sourceKey);
            }
        } catch (RuntimeException exception) {
            throw ioFailure("Unable to publish S3 object", exception);
        }
    }

    private void multipartCopy(String sourceKey, String targetKey, long size) {
        if (size > COPY_PART_SIZE_BYTES * 10_000L) {
            throw new IllegalArgumentException("S3 object exceeds the 10,000-part multipart copy limit.");
        }
        String uploadId = client.createMultipartUpload(CreateMultipartUploadRequest.builder()
                .bucket(bucket).key(targetKey).build()).join().uploadId();
        List<CompletedPart> completedParts = new ArrayList<>();
        try {
            String encodedSource = encodeCopySource(bucket + "/" + sourceKey);
            int partNumber = 1;
            for (long firstByte = 0; firstByte < size; firstByte += COPY_PART_SIZE_BYTES) {
                long lastByte = Math.min(size - 1L, firstByte + COPY_PART_SIZE_BYTES - 1L);
                UploadPartCopyResponse response = client.uploadPartCopy(UploadPartCopyRequest.builder()
                        .sourceBucket(bucket).sourceKey(sourceKey)
                        .destinationBucket(bucket).destinationKey(targetKey).uploadId(uploadId)
                        .partNumber(partNumber)
                        .copySource(encodedSource)
                        .copySourceRange("bytes=" + firstByte + "-" + lastByte)
                        .build()).join();
                completedParts.add(CompletedPart.builder().partNumber(partNumber)
                        .eTag(response.copyPartResult().eTag()).build());
                partNumber++;
            }
            client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                    .bucket(bucket).key(targetKey).uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
                    .build()).join();
        } catch (RuntimeException failure) {
            try {
                client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                        .bucket(bucket).key(targetKey).uploadId(uploadId).build()).join();
            } catch (RuntimeException abortFailure) {
                failure.addSuppressed(abortFailure);
            }
            throw failure;
        }
    }

    private static String encodeCopySource(String source) {
        return java.net.URLEncoder.encode(source, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20").replace("%2F", "/");
    }

    @Override
    public void close() {
        try {
            client.close();
        } finally {
            streamExecutor.shutdownNow();
            completionExecutor.shutdownNow();
        }
    }

    private static String envOrConfig(String environmentKey, String configured) {
        String environmentValue = System.getenv(environmentKey);
        return environmentValue == null || environmentValue.isBlank() ? configured.trim() : environmentValue.trim();
    }

    private static boolean isLoopback(String host) {
        String normalized = host == null ? "" : host.replace("[", "").replace("]", "");
        return normalized.equalsIgnoreCase("localhost") || normalized.equals("127.0.0.1") || normalized.equals("::1");
    }

    private static IOException ioFailure(String action, Throwable throwable) {
        S3Exception serviceFailure = findCause(throwable, S3Exception.class);
        Throwable detailCause = throwable;
        while (detailCause.getCause() != null) detailCause = detailCause.getCause();
        String detail = serviceFailure != null && serviceFailure.awsErrorDetails() != null
                ? serviceFailure.awsErrorDetails().errorMessage() : detailCause.getMessage();
        return new RemoteStorageException(action + (detail == null || detail.isBlank() ? "." : ": " + detail),
                isTransient(throwable), throwable);
    }

    private static boolean isTransient(Throwable failure) {
        S3Exception serviceFailure = findCause(failure, S3Exception.class);
        if (serviceFailure != null) {
            int status = serviceFailure.statusCode();
            return status == 429 || status >= 500;
        }
        // Errores del cliente como pool de conexiones agotado son transitorios
        if (findCause(failure, SdkClientException.class) != null) {
            return true;
        }
        return RemoteStorageException.isTransientNetworkFailure(failure);
    }

    private static <T extends Throwable> T findCause(Throwable failure, Class<T> type) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (type.isInstance(current)) return type.cast(current);
        }
        return null;
    }
}
