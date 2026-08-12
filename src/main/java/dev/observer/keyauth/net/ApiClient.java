package dev.observer.keyauth.net;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
      this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5L)).build();
   }

   private static String esc(String s) {
      return s.replace("\\", "\\\\").replace("\"", "\\\"");
   }

   public CompletableFuture<ApiResult> redeem(String key, String uuid, String name, String discordId) {
      String var10000 = esc(key);
      String json = "{\"key\":\"" + var10000 + "\",\"uuid\":\"" + esc(uuid) + "\",\"name\":\"" + esc(name) + "\",\"discord_id\":\"" + esc(discordId == null ? "" : discordId) + "\"}";
      HttpRequest req = HttpRequest.newBuilder().uri(URI.create(this.baseUrl + "/redeem")).timeout(Duration.ofSeconds(8L)).header("Content-Type", "application/json").header("X-API-Key", this.secret).POST(BodyPublishers.ofString(json)).build();
      return this.send(req);
   }

   public CompletableFuture<ApiResult> renew(String uuid, String newKey, String name) {
      String var10000 = esc(uuid);
      String json = "{\"uuid\":\"" + var10000 + "\",\"new_key\":\"" + esc(newKey) + "\",\"name\":\"" + esc(name == null ? "" : name) + "\"}";
      HttpRequest req = HttpRequest.newBuilder().uri(URI.create(this.baseUrl + "/renew")).timeout(Duration.ofSeconds(8L)).header("Content-Type", "application/json").header("X-API-Key", this.secret).POST(BodyPublishers.ofString(json)).build();
      return this.send(req);
   }

   public CompletableFuture<ApiResult> changeUser(String uuid, String newDiscordId) {
      String var10000 = esc(uuid);
      String json = "{\"uuid\":\"" + var10000 + "\",\"new_discord_id\":\"" + esc(newDiscordId) + "\"}";
      HttpRequest req = HttpRequest.newBuilder().uri(URI.create(this.baseUrl + "/changeuser")).timeout(Duration.ofSeconds(8L)).header("Content-Type", "application/json").header("X-API-Key", this.secret).POST(BodyPublishers.ofString(json)).build();
      return this.send(req);
   }

   public CompletableFuture<ApiResult> check(String uuid) {
      HttpRequest req = HttpRequest.newBuilder().uri(URI.create(this.baseUrl + "/check?uuid=" + uuid)).timeout(Duration.ofSeconds(8L)).header("X-API-Key", this.secret).GET().build();
      return this.send(req);
   }

   private CompletableFuture<ApiResult> send(HttpRequest req) {
      return this.http.sendAsync(req, BodyHandlers.ofString()).thenApply((resp) -> ApiClient.ApiResult.parse((String)resp.body())).exceptionally((ex) -> ApiClient.ApiResult.networkError());
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
         return new ApiResult(false, "network_error", (String)null, true);
      }

      static ApiResult parse(String body) {
         Matcher okM = ApiClient.OK_PATTERN.matcher(body);
         Matcher reasonM = ApiClient.REASON_PATTERN.matcher(body);
         Matcher expiresM = ApiClient.EXPIRES_PATTERN.matcher(body);
         boolean ok = okM.find() && "true".equals(okM.group(1));
         String reason = reasonM.find() ? reasonM.group(1) : "unknown";
         String expiresAt = expiresM.find() ? expiresM.group(1) : null;
         return new ApiResult(ok, reason, expiresAt, false);
      }
   }
}
