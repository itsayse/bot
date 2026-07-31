package dev.observer.keyauth.net;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Talks to the Discord bot's small key-verification API (utils/mc_api.py).
 * Hand-rolled JSON parsing/building to avoid pulling in an external
 * dependency — the response shape from the bot is flat and fully controlled
 * by us, so a couple of regexes are enough and safer than assuming a JSON
 * library is on the server's classpath.
 */
public class ApiClient {
    private final String baseUrl;
    private final String secret;
    private final HttpClient http;

    private static final Pattern OK_PATTERN = Pattern.compile("\"ok\"\\s*:\\s*(true|false)");
    private static final Pattern REASON_PATTERN = Pattern.compile("\"reason\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern EXPIRES_PATTERN = Pattern.compile("\"expires_at\"\\s*:\\s*\"([^\"]*)\"");

    public ApiClient(String baseUrl, String secret) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.secret = secret;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public static class ApiResult {
        public final boolean ok;
        public final String reason;
        public final String expiresAt;
        public final boolean networkError;

        ApiResult(boolean ok, String reason, String expiresAt, boolean networkError) {
            this.ok = ok;
            this.reason = reason;
            this.expiresAt = expiresAt;
            this.networkError = networkError;
        }

        static ApiResult networkError() {
            return new ApiResult(false, "network_error", null, true);
        }

        static ApiResult parse(String body) {
            Matcher okM = OK_PATTERN.matcher(body);
            Matcher reasonM = REASON_PATTERN.matcher(body);
            Matcher expiresM = EXPIRES_PATTERN.matcher(body);
            boolean ok = okM.find() && "true".equals(okM.group(1));
            String reason = reasonM.find() ? reasonM.group(1) : "unknown";
            String expiresAt = expiresM.find() ? expiresM.group(1) : null;
            return new ApiResult(ok, reason, expiresAt, false);
        }
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public CompletableFuture<ApiResult> redeem(String key, String uuid, String name, String discordId) {
        String json = "{\"key\":\"" + esc(key) + "\",\"uuid\":\"" + esc(uuid) + "\",\"name\":\"" + esc(name)
                + "\",\"discord_id\":\"" + esc(discordId == null ? "" : discordId) + "\"}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/redeem"))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .header("X-API-Key", secret)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return send(req);
    }

    public CompletableFuture<ApiResult> renew(String uuid, String newKey, String name) {
        String json = "{\"uuid\":\"" + esc(uuid) + "\",\"new_key\":\"" + esc(newKey)
                + "\",\"name\":\"" + esc(name == null ? "" : name) + "\"}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/renew"))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .header("X-API-Key", secret)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return send(req);
    }

    public CompletableFuture<ApiResult> changeUser(String uuid, String newDiscordId) {
        String json = "{\"uuid\":\"" + esc(uuid) + "\",\"new_discord_id\":\"" + esc(newDiscordId) + "\"}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/changeuser"))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .header("X-API-Key", secret)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return send(req);
    }

    public CompletableFuture<ApiResult> check(String uuid) {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/check?uuid=" + uuid))
                .timeout(Duration.ofSeconds(8))
                .header("X-API-Key", secret)
                .GET()
                .build();
        return send(req);
    }

    private CompletableFuture<ApiResult> send(HttpRequest req) {
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> ApiResult.parse(resp.body()))
                .exceptionally(ex -> ApiResult.networkError());
    }
}
