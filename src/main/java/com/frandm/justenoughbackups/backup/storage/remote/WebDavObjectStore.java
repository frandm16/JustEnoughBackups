package com.frandm.justenoughbackups.backup.storage.remote;

import com.frandm.justenoughbackups.config.BackupConfig;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.time.Instant;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.w3c.dom.Element;

final class WebDavObjectStore implements RemoteObjectStore {
    private static final Duration CONTROL_REQUEST_TIMEOUT = Duration.ofSeconds(45);
    private static final Duration TRANSFER_REQUEST_TIMEOUT = Duration.ofHours(6);
    private static final String PROPFIND_BODY = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>"
            + "<d:propfind xmlns:d=\"DAV:\"><d:prop><d:getcontentlength/><d:getlastmodified/><d:resourcetype/></d:prop></d:propfind>";
    private final URI root;
    private final String authorization;
    private final int maxRetries;
    private final Duration controlRequestTimeout;
    private final java.util.Set<String> createdCollections = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    WebDavObjectStore(BackupConfig.RemoteStorage config) throws IOException {
        this(config, CONTROL_REQUEST_TIMEOUT);
    }

    WebDavObjectStore(BackupConfig.RemoteStorage config, Duration controlRequestTimeout) throws IOException {
        this.controlRequestTimeout = controlRequestTimeout;
        try {
            URI parsed = URI.create(config.webDavUrl.trim());
            if (parsed.getUserInfo() != null || parsed.getQuery() != null || parsed.getFragment() != null)
                throw new IOException("WebDAV URL must not include credentials, a query, or a fragment.");
            String normalized = parsed.toString();
            if (!normalized.endsWith("/")) normalized += "/";
            root = URI.create(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid WebDAV URL.", exception);
        }
        if (root.getHost() == null || !"https".equalsIgnoreCase(root.getScheme())
                && !("http".equalsIgnoreCase(root.getScheme()) && isLoopback(root.getHost()))) {
            throw new IOException("WebDAV URL must use HTTPS (HTTP is allowed only for localhost).");
        }
        String username = envOrConfig("JEB_WEBDAV_USERNAME", config.webDavUsername);
        String password = envOrConfig("JEB_WEBDAV_PASSWORD", config.webDavPassword);
        maxRetries = Math.clamp(config.maxRetries, 0, 10);
        authorization = username.isBlank() && password.isBlank() ? ""
                : "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public List<ObjectInfo> list(String prefix) throws IOException {
        URI uri = objectUri(prefix);
        HttpRequest request = authorized(HttpRequest.newBuilder(uri)
                .header("Depth", "1")
                .header("Content-Type", "application/xml; charset=utf-8")
                .method("PROPFIND", HttpRequest.BodyPublishers.ofString(PROPFIND_BODY)), controlRequestTimeout).build();
        HttpResponse<InputStream> response = sendRetryable(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() == 404) {
            response.body().close();
            return List.of();
        }
        if (response.statusCode() != 207 && response.statusCode() != 200) {
            response.body().close();
            throw statusError("list WebDAV objects", response.statusCode());
        }
        try (InputStream body = response.body()) {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var document = factory.newDocumentBuilder().parse(body);
            var responses = document.getElementsByTagNameNS("DAV:", "response");
            List<ObjectInfo> objects = new ArrayList<>();
            for (int i = 0; i < responses.getLength(); i++) {
                Element item = (Element) responses.item(i);
                var hrefs = item.getElementsByTagNameNS("DAV:", "href");
                var lengths = item.getElementsByTagNameNS("DAV:", "getcontentlength");
                var modified = item.getElementsByTagNameNS("DAV:", "getlastmodified");
                var collections = item.getElementsByTagNameNS("DAV:", "collection");
                if (hrefs.getLength() == 0 || collections.getLength() > 0) continue;
                URI itemUri = uri.resolve(hrefs.item(0).getTextContent().trim());
                String key = relativeKey(itemUri);
                if (!key.isBlank() && key.startsWith(prefix) && lengths.getLength() > 0) {
                    long size;
                    try {
                        size = Long.parseLong(lengths.item(0).getTextContent().trim());
                    } catch (NumberFormatException exception) {
                        size = 0L;
                    }
                    Instant lastModified = null;
                    if (modified.getLength() > 0) {
                        try { lastModified = ZonedDateTime.parse(modified.item(0).getTextContent().trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(); }
                        catch (RuntimeException ignored) { }
                    }
                    objects.add(new ObjectInfo(key, Math.max(0L, size), lastModified));
                }
            }
            return objects;
        } catch (Exception exception) {
            if (exception instanceof IOException ioException) throw ioException;
            throw new IOException("Unable to parse WebDAV directory response.", exception);
        }
    }

    @Override
    public void put(String key, InputStream input) throws IOException {
        ensureParentCollections(key);
        HttpRequest request = authorized(HttpRequest.newBuilder(objectUri(key))
                .header("Content-Type", "application/octet-stream")
                .PUT(HttpRequest.BodyPublishers.ofInputStream(() -> input)), TRANSFER_REQUEST_TIMEOUT).build();
        HttpResponse<Void> response = send(request, HttpResponse.BodyHandlers.discarding());
        if (!success(response.statusCode())) throw statusError("upload WebDAV object", response.statusCode());
    }

    private void ensureParentCollections(String key) throws IOException {
        String[] parts = key.split("/");
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (parts[i].isBlank()) continue;
            if (!path.isEmpty()) path.append('/');
            path.append(parts[i]);
            String current = path.toString();
            if (createdCollections.contains(current)) continue;
            HttpRequest request = authorized(HttpRequest.newBuilder(objectUri(current + "/"))
                    .method("MKCOL", HttpRequest.BodyPublishers.noBody()), controlRequestTimeout).build();
            HttpResponse<Void> response = send(request, HttpResponse.BodyHandlers.discarding());
            if (!success(response.statusCode()) && response.statusCode() != 405 && response.statusCode() != 301) {
                throw statusError("create WebDAV collection", response.statusCode());
            }
            createdCollections.add(current);
        }
    }

    @Override
    public InputStream get(String key) throws IOException {
        HttpResponse<InputStream> response = sendRetryable(authorized(HttpRequest.newBuilder(objectUri(key)).GET(), TRANSFER_REQUEST_TIMEOUT).build(),
                HttpResponse.BodyHandlers.ofInputStream());
        if (!success(response.statusCode())) {
            response.body().close();
            throw statusError("download WebDAV object", response.statusCode());
        }
        return response.body();
    }

    @Override
    public void delete(String key) throws IOException {
        HttpResponse<Void> response = sendRetryable(authorized(HttpRequest.newBuilder(objectUri(key)).DELETE(), controlRequestTimeout).build(),
                HttpResponse.BodyHandlers.discarding());
        if (!success(response.statusCode()) && response.statusCode() != 404) {
            throw statusError("delete WebDAV object", response.statusCode());
        }
    }

    @Override
    public void move(String sourceKey, String targetKey) throws IOException {
        ensureParentCollections(targetKey);
        HttpRequest request = authorized(HttpRequest.newBuilder(objectUri(sourceKey))
                .header("Destination", objectUri(targetKey).toString())
                .header("Overwrite", "T")
                .method("MOVE", HttpRequest.BodyPublishers.noBody()), controlRequestTimeout).build();
        HttpResponse<Void> response = send(request, HttpResponse.BodyHandlers.discarding());
        if (!success(response.statusCode())) throw statusError("move WebDAV object", response.statusCode());
    }

    private URI objectUri(String key) throws IOException {
        if (key == null || key.startsWith("/") || key.contains("..") || key.indexOf('\\') >= 0) {
            throw new IOException("Invalid remote object key.");
        }
        String encoded = java.util.Arrays.stream(key.split("/", -1))
                .map(segment -> java.net.URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(java.util.stream.Collectors.joining("/"));
        String base = root.toString().endsWith("/") ? root.toString() : root + "/";
        return URI.create(base + encoded);
    }

    private String relativeKey(URI object) {
        String basePath = root.getRawPath() == null ? "/" : root.getRawPath();
        String path = object.getRawPath() == null ? "" : object.getRawPath();
        if (!path.startsWith(basePath)) return "";
        return java.net.URLDecoder.decode(path.substring(basePath.length()).replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private HttpRequest.Builder authorized(HttpRequest.Builder builder, Duration timeout) {
        builder.timeout(timeout);
        return authorization.isBlank() ? builder : builder.header("Authorization", authorization);
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException {
        try {
            return client.send(request, handler);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RemoteStorageException("WebDAV request interrupted.", false, exception);
        } catch (IOException exception) {
            throw new RemoteStorageException("WebDAV request failed: " + exception.getMessage(),
                    RemoteStorageException.isTransientNetworkFailure(exception), exception);
        }
    }

    private <T> HttpResponse<T> sendRetryable(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try { return send(request, handler); }
            catch (IOException exception) {
                last = exception;
                if (!RemoteStorageException.isRetryable(exception)) break;
                if (attempt == maxRetries) break;
                try { Thread.sleep(Math.min(200L << attempt, 3000L)); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("WebDAV retry interrupted.", interrupted);
                }
            }
        }
        if (last != null) throw last;
        throw new IOException("WebDAV request failed without an attempt.");
    }

    private static IOException statusError(String action, int status) {
        return new RemoteStorageException("Unable to " + action + " (HTTP " + status + ").",
                status == 408 || status == 429 || status >= 500, null);
    }

    private static boolean success(int status) {
        return status >= 200 && status < 300;
    }

    private static String envOrConfig(String key, String configured) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? configured.trim() : value.trim();
    }

    private static boolean isLoopback(String host) {
        String normalized = host == null ? "" : host.replace("[", "").replace("]", "");
        return normalized.equalsIgnoreCase("localhost") || normalized.equals("127.0.0.1") || normalized.equals("::1");
    }

    @Override
    public void close() {
        client.close();
    }
}