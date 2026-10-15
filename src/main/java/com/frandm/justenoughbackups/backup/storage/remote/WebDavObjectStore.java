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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.w3c.dom.Element;

final class WebDavObjectStore implements RemoteObjectStore {
    private static final String PROPFIND_BODY = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>"
            + "<d:propfind xmlns:d=\"DAV:\"><d:prop><d:getcontentlength/><d:resourcetype/></d:prop></d:propfind>";
    private final URI root;
    private final String authorization;
    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    WebDavObjectStore(BackupConfig.RemoteStorage config) throws IOException {
        try {
            root = URI.create(config.webDavUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid WebDAV URL.", exception);
        }
        if (root.getHost() == null || !"https".equalsIgnoreCase(root.getScheme())
                && !("http".equalsIgnoreCase(root.getScheme()) && isLoopback(root.getHost()))) {
            throw new IOException("WebDAV URL must use HTTPS (HTTP is allowed only for localhost).");
        }
        String username = envOrConfig("JEB_WEBDAV_USERNAME", config.webDavUsername);
        String password = envOrConfig("JEB_WEBDAV_PASSWORD", config.webDavPassword);
        authorization = username.isBlank() && password.isBlank() ? ""
                : "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public List<ObjectInfo> list(String prefix) throws IOException {
        URI uri = objectUri(prefix);
        HttpRequest request = authorized(HttpRequest.newBuilder(uri)
                .header("Depth", "1")
                .header("Content-Type", "application/xml; charset=utf-8")
                .method("PROPFIND", HttpRequest.BodyPublishers.ofString(PROPFIND_BODY))).build();
        HttpResponse<InputStream> response = send(request, HttpResponse.BodyHandlers.ofInputStream());
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
                URI itemUri = root.resolve(hrefs.item(0).getTextContent());
                String key = relativeKey(itemUri);
                if (!key.isBlank() && key.startsWith(prefix) && lengths.getLength() > 0) {
                    long size;
                    try {
                        size = Long.parseLong(lengths.item(0).getTextContent().trim());
                    } catch (NumberFormatException exception) {
                        size = 0L;
                    }
                    Instant lastModified = Instant.EPOCH;
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
                .PUT(HttpRequest.BodyPublishers.ofInputStream(() -> input))).build();
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
            HttpRequest request = authorized(HttpRequest.newBuilder(objectUri(path + "/"))
                    .method("MKCOL", HttpRequest.BodyPublishers.noBody())).build();
            HttpResponse<Void> response = send(request, HttpResponse.BodyHandlers.discarding());
            if (!success(response.statusCode()) && response.statusCode() != 405 && response.statusCode() != 301) {
                throw statusError("create WebDAV collection", response.statusCode());
            }
        }
    }

    @Override
    public InputStream get(String key) throws IOException {
        HttpResponse<InputStream> response = send(authorized(HttpRequest.newBuilder(objectUri(key)).GET()).build(),
                HttpResponse.BodyHandlers.ofInputStream());
        if (!success(response.statusCode())) {
            response.body().close();
            throw statusError("download WebDAV object", response.statusCode());
        }
        return response.body();
    }

    @Override
    public void delete(String key) throws IOException {
        HttpResponse<Void> response = send(authorized(HttpRequest.newBuilder(objectUri(key)).DELETE()).build(),
                HttpResponse.BodyHandlers.discarding());
        if (!success(response.statusCode()) && response.statusCode() != 404) {
            throw statusError("delete WebDAV object", response.statusCode());
        }
    }

    @Override
    public void move(String sourceKey, String targetKey) throws IOException {
        HttpRequest request = authorized(HttpRequest.newBuilder(objectUri(sourceKey))
                .header("Destination", objectUri(targetKey).toString())
                .header("Overwrite", "F")
                .method("MOVE", HttpRequest.BodyPublishers.noBody())).build();
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
        String basePath = root.getPath() == null ? "/" : root.getPath();
        String path = object.getPath() == null ? "" : object.getPath();
        if (!path.startsWith(basePath)) return "";
        return java.net.URLDecoder.decode(path.substring(basePath.length()), StandardCharsets.UTF_8);
    }

    private HttpRequest.Builder authorized(HttpRequest.Builder builder) {
        return authorization.isBlank() ? builder : builder.header("Authorization", authorization);
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException {
        try {
            return client.send(request, handler);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("WebDAV request interrupted.", exception);
        }
    }

    private static IOException statusError(String action, int status) {
        return new IOException("Unable to " + action + " (HTTP " + status + ").");
    }

    private static boolean success(int status) {
        return status >= 200 && status < 300;
    }

    private static String envOrConfig(String key, String configured) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? configured.trim() : value.trim();
    }

    private static boolean isLoopback(String host) {
        return host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("::1");
    }
}