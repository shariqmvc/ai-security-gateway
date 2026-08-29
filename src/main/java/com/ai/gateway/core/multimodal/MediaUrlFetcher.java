package com.ai.gateway.core.multimodal;

import com.ai.gateway.config.RemoteMediaProperties;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Securely resolves public media URLs into bounded in-memory bytes for provider adapters.
 * Redirects are disabled and private/link-local destinations are rejected before the request.
 */
@Service
public class MediaUrlFetcher {

    private final RemoteMediaProperties properties;
    private final HttpClient httpClient;

    public MediaUrlFetcher(RemoteMediaProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public ResolvedMedia fetch(MediaContent media) {
        if (media == null || media.getUrl() == null || media.getUrl().isBlank()) {
            throw new MediaInputException("Media URL is required for URL media.");
        }

        URI uri = parseAndValidateUri(media.getUrl());
        validateResolvedAddresses(uri.getHost());

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(properties.getReadTimeout())
                .header("Accept", media.getMimeType())
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                closeQuietly(response.body());
                throw new MediaInputException(
                        "Unable to fetch media URL: HTTP " + response.statusCode() + ".");
            }

            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (contentLength > properties.getMaxBytes()) {
                closeQuietly(response.body());
                throw new MediaInputException("Remote media exceeds the gateway size limit.");
            }

            byte[] bytes = readBounded(response.body(), properties.getMaxBytes());
            if (bytes.length == 0) {
                throw new MediaInputException("Remote media is empty.");
            }

            String responseMime = response.headers()
                    .firstValue("Content-Type")
                    .map(value -> value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT))
                    .orElse("");

            validateMime(media.getMimeType(), responseMime);
            return new ResolvedMedia(media.getMimeType(), bytes, Base64.getEncoder().encodeToString(bytes));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MediaInputException("Media URL fetch was interrupted.", ex);
        } catch (IOException ex) {
            throw new MediaInputException("Unable to fetch media URL: " + ex.getMessage(), ex);
        }
    }

    private URI parseAndValidateUri(String raw) {
        final URI uri;
        try {
            uri = URI.create(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new MediaInputException("Invalid media URL.", ex);
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!"https".equalsIgnoreCase(scheme)
                && !(properties.isAllowHttp() && "http".equalsIgnoreCase(scheme)))) {
            throw new MediaInputException("Only HTTPS media URLs are allowed.");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new MediaInputException("Media URL must contain a valid host.");
        }
        if (uri.getUserInfo() != null) {
            throw new MediaInputException("Media URL user-info is not allowed.");
        }
        int port = uri.getPort();
        int defaultPort = "https".equalsIgnoreCase(scheme) ? 443 : 80;
        if (port != -1 && port != defaultPort) {
            throw new MediaInputException("Non-default media URL ports are not allowed.");
        }
        return uri;
    }

    private void validateResolvedAddresses(String host) {
        try {
            List<InetAddress> addresses = List.of(InetAddress.getAllByName(host));
            if (addresses.isEmpty()) {
                throw new MediaInputException("Media URL host did not resolve.");
            }
            for (InetAddress address : addresses) {
                if (isBlockedAddress(address)) {
                    throw new MediaInputException("Media URL resolves to a private or local network address.");
                }
            }
        } catch (UnknownHostException ex) {
            throw new MediaInputException("Media URL host could not be resolved.", ex);
        }
    }

    private boolean isBlockedAddress(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || isUniqueLocalIpv6(address)
                || isCarrierGradeNat(address)
                || isDocumentationNetwork(address);
    }

    private boolean isUniqueLocalIpv6(InetAddress address) {
        byte[] b = address.getAddress();
        return b.length == 16 && (b[0] & 0xfe) == 0xfc;
    }

    private boolean isCarrierGradeNat(InetAddress address) {
        byte[] b = address.getAddress();
        if (b.length != 4) return false;
        int first = b[0] & 0xff;
        int second = b[1] & 0xff;
        return first == 100 && second >= 64 && second <= 127;
    }

    private boolean isDocumentationNetwork(InetAddress address) {
        byte[] b = address.getAddress();
        if (b.length != 4) return false;
        int first = b[0] & 0xff;
        int second = b[1] & 0xff;
        int third = b[2] & 0xff;
        return (first == 192 && second == 0 && third == 2)
                || (first == 198 && second == 51 && third == 100)
                || (first == 203 && second == 0 && third == 113);
    }

    private byte[] readBounded(InputStream inputStream, long maxBytes) throws IOException {
        try (InputStream in = inputStream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new MediaInputException("Remote media exceeds the gateway size limit.");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private void validateMime(String declaredMime, String responseMime) {
        String declared = declaredMime == null ? "" : declaredMime.toLowerCase(Locale.ROOT);
        if (!declared.startsWith("image/") && !declared.startsWith("audio/")) {
            throw new MediaInputException("Unsupported remote media MIME type: " + declaredMime);
        }
        if (!responseMime.isBlank()
                && !responseMime.equals("application/octet-stream")
                && !responseMime.equals(declared)) {
            throw new MediaInputException(
                    "Remote media Content-Type does not match the requested MIME type.");
        }
    }

    private void closeQuietly(InputStream inputStream) {
        try {
            inputStream.close();
        } catch (IOException ignored) {
            // best effort
        }
    }

    public record ResolvedMedia(String mimeType, byte[] bytes, String base64Data) {}
}
