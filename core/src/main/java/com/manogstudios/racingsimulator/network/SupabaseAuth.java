package com.manogstudios.racingsimulator.network;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.json.JSONArray;

public class SupabaseAuth {

    public static boolean isLoggedIn = false;

    // Tokens
    public static String accessToken = null;
    public static String refreshToken = null;

    // Identity
    public static String userId = null;

    // Expiry tracking
    // accessTokenExpiresAtMillis is absolute time in millis when access token expires
    private static long accessTokenExpiresAtMillis = 0L;

    public static String lastErrorCode = null;
    public static String lastErrorMessage = null;

    // project values
    static final String SUPABASE_URL = "https://trfecuqpkrjobgxrmgwm.supabase.co";
    static final String API_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRyZmVjdXFwa3Jqb2JneHJtZ3dtIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTIwNzc0MjgsImV4cCI6MjA2NzY1MzQyOH0._sByi5zdyPnViPIzTsidac2REfnq5GCC89_zD7TLyEs";

    private static final HttpClient client = HttpClient.newHttpClient();

    // Persist session locally (so user stays logged in between app restarts)
    private static final Preferences prefs = Gdx.app.getPreferences("supabase_session");

    // Refresh a little before expiry to avoid race conditions mid-request
    private static final long REFRESH_SAFETY_WINDOW_MS = 60_000L; // 60s



    // ---------------------------
    // Public API
    // ---------------------------


    public static void restoreSessionFromPrefs() {
        String at = prefs.getString("accessToken", null);
        String rt = prefs.getString("refreshToken", null);
        String uid = prefs.getString("userId", null);
        long exp = prefs.getLong("accessTokenExpiresAtMillis", 0L);

        accessToken = (at != null && !at.isEmpty()) ? at : null;
        refreshToken = (rt != null && !rt.isEmpty()) ? rt : null;
        userId = (uid != null && !uid.isEmpty()) ? uid : null;
        accessTokenExpiresAtMillis = exp;

        isLoggedIn = (accessToken != null && refreshToken != null && userId != null);
    }

    // Ensures accessToken is valid (refreshes if needed). Callback runs on LibGDX thread.
    public static void ensureValidSession(Consumer<Boolean> callback) {
        // Not logged in
        if (!isLoggedIn || refreshToken == null || refreshToken.isEmpty()) {
            if (callback != null) Gdx.app.postRunnable(() -> callback.accept(false));
            return;
        }

        long now = System.currentTimeMillis();

        // If token is still valid and not near expiry, its good to be re-used
        if (accessToken != null && (accessTokenExpiresAtMillis - now) > REFRESH_SAFETY_WINDOW_MS) {
            if (callback != null) Gdx.app.postRunnable(() -> callback.accept(true));
            return;
        }

        // otherwise refresh
        refreshAccessToken(callback);
    }

    public static void login(String email, String password, Consumer<Boolean> callback) {
        new Thread(() -> {
            try {
                lastErrorCode = null;
                lastErrorMessage = null;

                JSONObject body = new JSONObject();
                body.put("email", email);
                body.put("password", password);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SUPABASE_URL + "/auth/v1/token?grant_type=password"))
                    .header("Content-Type", "application/json")
                    .header("apikey", API_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() / 100 == 2) {
                    JSONObject json = new JSONObject(response.body());

                    applySessionFromAuthResponse(json);

                    persistSession();

                    isLoggedIn = (accessToken != null && refreshToken != null && userId != null);

                    if (callback != null) Gdx.app.postRunnable(() -> callback.accept(true));
                } else {
                    isLoggedIn = false;

                    String bodyStr = response.body();
                    System.out.println("Supabase login error: " + response.statusCode() + " body=" + bodyStr);

                    try {
                        if (bodyStr != null && !bodyStr.isEmpty()) {
                            JSONObject err = new JSONObject(bodyStr);
                            lastErrorCode = err.optString("error", null);
                            lastErrorMessage = err.optString("error_description", null);
                        }
                    } catch (Exception ignored) {}

                    clearSessionInMemory();
                    clearSessionInPrefs();

                    if (callback != null) Gdx.app.postRunnable(() -> callback.accept(false));
                }

            } catch (Exception e) {
                e.printStackTrace();
                isLoggedIn = false;
                lastErrorCode = "exception";
                lastErrorMessage = e.getMessage();

                clearSessionInMemory();
                clearSessionInPrefs();

                if (callback != null) Gdx.app.postRunnable(() -> callback.accept(false));
            }
        }).start();
    }

    public static void logout() {
        isLoggedIn = false;
        clearSessionInMemory();
        clearSessionInPrefs();
    }

    // ---------------------------
    // Refresh implementation
    // ---------------------------

    private static void refreshAccessToken(Consumer<Boolean> callback) {
        new Thread(() -> {
            try {
                lastErrorCode = null;
                lastErrorMessage = null;

                if (refreshToken == null || refreshToken.isEmpty()) {
                    if (callback != null) Gdx.app.postRunnable(() -> callback.accept(false));
                    return;
                }

                JSONObject body = new JSONObject();
                body.put("refresh_token", refreshToken);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SUPABASE_URL + "/auth/v1/token?grant_type=refresh_token"))
                    .header("Content-Type", "application/json")
                    .header("apikey", API_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() / 100 == 2) {
                    JSONObject json = new JSONObject(response.body());

                    applySessionFromAuthResponse(json);
                    persistSession();

                    isLoggedIn = (accessToken != null && refreshToken != null && userId != null);

                    if (callback != null) Gdx.app.postRunnable(() -> callback.accept(true));
                } else {
                    System.out.println("Supabase refresh error: " + response.statusCode() + " body=" + response.body());

                    // If refresh fails, session is no longer trustworthy
                    isLoggedIn = false;

                    try {
                        String bodyStr = response.body();
                        if (bodyStr != null && !bodyStr.isEmpty()) {
                            JSONObject err = new JSONObject(bodyStr);
                            lastErrorCode = err.optString("error", null);
                            lastErrorMessage = err.optString("error_description", null);
                        }
                    } catch (Exception ignored) {}

                    clearSessionInMemory();
                    clearSessionInPrefs();

                    if (callback != null) Gdx.app.postRunnable(() -> callback.accept(false));
                }

            } catch (Exception e) {
                e.printStackTrace();
                isLoggedIn = false;
                lastErrorCode = "exception";
                lastErrorMessage = e.getMessage();

                clearSessionInMemory();
                clearSessionInPrefs();

                if (callback != null) Gdx.app.postRunnable(() -> callback.accept(false));
            }
        }).start();
    }

    // ---------------------------
    // Session parsing + persistence
    // ---------------------------

    private static void applySessionFromAuthResponse(JSONObject json) {
        // Successful login/refresh responses have an access_token
        String at = json.optString("access_token", null);
        if (at != null && !at.isEmpty()) {
            accessToken = at;
        }

        // Successful login/refresh responses have a refresh_token
        String rt = json.optString("refresh_token", null);
        if (rt != null && !rt.isEmpty()) {
            refreshToken = rt;
        }

        // expires_in is seconds
        long expiresInSec = 0L;
        try {
            expiresInSec = json.optLong("expires_in", 0L);
        } catch (Exception ignored) {}

        if (expiresInSec > 0) {
            accessTokenExpiresAtMillis = System.currentTimeMillis() + (expiresInSec * 1000L);
        } else {
            accessTokenExpiresAtMillis = System.currentTimeMillis() + 60_000L;
        }

        // user object includes id
        if (json.has("user") && !json.isNull("user")) {
            JSONObject userObj = json.optJSONObject("user");
            if (userObj != null) {
                String id = userObj.optString("id", null);
                if (id != null && !id.isEmpty()) {
                    userId = id;
                }
            }
        }
    }



    public static boolean registerUser(String email, String password) {
        try {
            URL url = new URL(SUPABASE_URL + "/auth/v1/signup");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("apikey", API_KEY);
            conn.setDoOutput(true);

            String jsonInput = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonInput.getBytes("utf-8"));
            }

            int status = conn.getResponseCode();
            conn.disconnect();
            return status == 200 || status == 201;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void logoutAllDevices(java.util.function.Consumer<Boolean> callback) {
        ensureValidSession(ok -> {
            if (!ok || accessToken == null || accessToken.isEmpty()) {
                // still clear local session on this device
                isLoggedIn = false;
                clearSessionInMemory();
                clearSessionInPrefs();

                if (callback != null) {
                    Gdx.app.postRunnable(() -> callback.accept(false));
                }
                return;
            }

            final String token = accessToken;

            new Thread(() -> {
                boolean success = false;

                try {
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(SUPABASE_URL + "/auth/v1/logout?scope=global"))
                        .header("apikey", API_KEY)
                        .header("Authorization", "Bearer " + token)
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build();

                    HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());

                    System.out.println("logoutAllDevices: " + response.statusCode() + " body=" + response.body());

                    success = (response.statusCode() / 100 == 2);

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    // clear THIS device either way
                    isLoggedIn = false;
                    clearSessionInMemory();
                    clearSessionInPrefs();

                    boolean finalSuccess = success;
                    if (callback != null) {
                        Gdx.app.postRunnable(() -> callback.accept(finalSuccess));
                    }
                }
            }).start();
        });
    }


    private static void persistSession() {
        prefs.putString("accessToken", accessToken != null ? accessToken : "");
        prefs.putString("refreshToken", refreshToken != null ? refreshToken : "");
        prefs.putString("userId", userId != null ? userId : "");
        prefs.putLong("accessTokenExpiresAtMillis", accessTokenExpiresAtMillis);
        prefs.flush();
    }

    private static void clearSessionInPrefs() {
        prefs.remove("accessToken");
        prefs.remove("refreshToken");
        prefs.remove("userId");
        prefs.remove("accessTokenExpiresAtMillis");
        prefs.flush();
    }

    private static void clearSessionInMemory() {
        accessToken = null;
        refreshToken = null;
        userId = null;
        accessTokenExpiresAtMillis = 0L;
    }

    public static void updatePassword(String accessToken,
                                      String newPassword,
                                      java.util.function.Consumer<Boolean> callback) {
        if (accessToken == null || accessToken.isEmpty()
            || newPassword == null || newPassword.isEmpty()) {

            if (callback != null) {
                Gdx.app.postRunnable(() -> callback.accept(false));
            }
            return;
        }

        new Thread(() -> {
            boolean success = false;
            try {
                JSONObject body = new JSONObject();
                body.put("password", newPassword);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SUPABASE_URL + "/auth/v1/user"))
                    .header("Content-Type", "application/json")
                    .header("apikey", API_KEY)
                    .header("Authorization", "Bearer " + accessToken)
                    .method("PUT", HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

                HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() / 100 == 2) {
                    success = true;
                    System.out.println("updatePassword: success");
                } else {
                    System.out.println("updatePassword: non-2xx: " +
                        response.statusCode() + " body=" + response.body());
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                boolean finalSuccess = success;
                if (callback != null) {
                    Gdx.app.postRunnable(() -> callback.accept(finalSuccess));
                }
            }
        }).start();
    }

    public static class MfaFactor {
        public String id;
        public String factorType;
        public String status;
        public String friendlyName;
    }

    public static class MfaFactorsResult {
        public boolean success;
        public String error;
        public List<MfaFactor> factors = new ArrayList<>();
    }

    public static class MfaEnrollResult {
        public boolean success;
        public String error;
        public String factorId;
        public String factorType;
        public String friendlyName;
        public String secret;
        public String uri;
        public String qrCode;
    }

    public static class MfaChallengeResult {
        public boolean success;
        public String error;
        public String challengeId;
    }

    public static class MfaVerifyResult {
        public boolean success;
        public String error;
    }

    public static class MfaUnenrollResult {
        public boolean success;
        public String error;
    }

    public static class MfaAalResult {
        public boolean success;
        public String error;
        public String currentLevel;
        public String nextLevel;
    }

    public static void listMfaFactors(java.util.function.Consumer<MfaFactorsResult> callback) {
        ensureValidSession(ok -> {
            if (!ok || accessToken == null) {
                MfaFactorsResult r = new MfaFactorsResult();
                r.success = false;
                r.error = "not_logged_in_or_expired";
                if (callback != null) Gdx.app.postRunnable(() -> callback.accept(r));
                return;
            }

            final String token = accessToken;

            new Thread(() -> {
                MfaFactorsResult result = new MfaFactorsResult();

                try {
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(SUPABASE_URL + "/rest/v1/auth/factors?select=id,friendly_name,factor_type,status"))
                        .header("apikey", API_KEY)
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                    HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());

                    System.out.println("listMfaFactors: " + response.statusCode() + " body=" + response.body());

                    if (response.statusCode() / 100 == 2) {
                        JSONArray arr = new JSONArray(response.body());

                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.getJSONObject(i);

                            MfaFactor factor = new MfaFactor();
                            factor.id = o.optString("id", null);
                            factor.factorType = o.optString("factor_type", null);
                            factor.status = o.optString("status", null);
                            factor.friendlyName = o.optString("friendly_name", null);

                            result.factors.add(factor);
                        }

                        result.success = true;
                    } else {
                        result.success = false;
                        result.error = extractError(response.body(), "list_factors_failed");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    result.success = false;
                    result.error = "exception";
                }

                if (callback != null) Gdx.app.postRunnable(() -> callback.accept(result));
            }).start();
        });
    }

    public static class MfaStatusResult {
        public boolean success;
        public String error;
        public String currentLevel;
        public String nextLevel;
        public boolean hasVerifiedFactor;
        public int factorCount;
        public List<String> verifiedFactorIds = new ArrayList<>();
    }

    public static void fetchMfaStatus(java.util.function.Consumer<MfaStatusResult> callback) {
        ensureValidSession(ok -> {
            if (!ok || accessToken == null || accessToken.isEmpty()) {
                MfaStatusResult r = new MfaStatusResult();
                r.success = false;
                r.error = "not_logged_in_or_expired";
                if (callback != null) Gdx.app.postRunnable(() -> callback.accept(r));
                return;
            }

            final String token = accessToken;

            new Thread(() -> {
                MfaStatusResult result = new MfaStatusResult();

                try {
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(SUPABASE_URL + "/functions/v1/mfa-status"))
                        .header("Content-Type", "application/json")
                        .header("apikey", API_KEY)
                        .header("Authorization", "Bearer " + token)
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build();

                    HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());

                    System.out.println("fetchMfaStatus: " + response.statusCode() + " body=" + response.body());

                    if (response.statusCode() / 100 == 2) {
                        JSONObject json = new JSONObject(response.body());

                        result.success = json.optBoolean("ok", false);
                        result.hasVerifiedFactor = json.optBoolean("hasVerifiedFactor", false);
                        result.factorCount = json.optInt("factorCount", 0);

                        JSONArray ids = json.optJSONArray("verifiedFactorIds");
                        if (ids != null) {
                            for (int i = 0; i < ids.length(); i++) {
                                result.verifiedFactorIds.add(ids.optString(i));
                            }
                        }

                        String current = decodeAalFromJwt(accessToken);
                        result.currentLevel = (current != null && !current.isEmpty()) ? current : "aal1";

                        if ("aal2".equalsIgnoreCase(result.currentLevel)) {
                            result.nextLevel = "aal2";
                        } else {
                            result.nextLevel = result.hasVerifiedFactor ? "aal2" : "aal1";
                        }

                        if (!result.success) {
                            result.error = json.optString("error", "mfa_status_failed");
                        }
                    } else {
                        result.success = false;
                        result.error = extractError(response.body(), "mfa_status_failed");
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    result.success = false;
                    result.error = "exception";
                }

                if (callback != null) {
                    Gdx.app.postRunnable(() -> callback.accept(result));
                }
            }).start();
        });
    }

    public static void enrollTotpFactor(String friendlyName,
                                        java.util.function.Consumer<MfaEnrollResult> callback) {
        ensureValidSession(ok -> {
            if (!ok || accessToken == null) {
                MfaEnrollResult r = new MfaEnrollResult();
                r.success = false;
                r.error = "not_logged_in_or_expired";
                if (callback != null) Gdx.app.postRunnable(() -> callback.accept(r));
                return;
            }

            final String token = accessToken;

            new Thread(() -> {
                MfaEnrollResult result = new MfaEnrollResult();

                try {
                    JSONObject body = new JSONObject();
                    body.put("factor_type", "totp");
                    body.put("friendly_name", friendlyName);

                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(SUPABASE_URL + "/auth/v1/factors"))
                        .header("Content-Type", "application/json")
                        .header("apikey", API_KEY)
                        .header("Authorization", "Bearer " + token)
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();

                    HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());

                    System.out.println("enrollTotpFactor: " + response.statusCode());

                    if (response.statusCode() / 100 == 2) {
                        JSONObject json = new JSONObject(response.body());

                        result.success = true;
                        result.factorId = json.optString("id", null);
                        result.factorType = json.optString("factor_type", json.optString("factorType", null));
                        result.friendlyName = json.optString("friendly_name", json.optString("friendlyName", null));

                        JSONObject totp = json.optJSONObject("totp");
                        if (totp != null) {
                            result.secret = totp.optString("secret", null);
                            result.uri = totp.optString("uri", null);
                            result.qrCode = totp.optString("qr_code", null);
                        }
                    } else {
                        result.success = false;
                        result.error = extractError(response.body(), "mfa_enroll_failed");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    result.success = false;
                    result.error = "exception";
                }

                if (callback != null) Gdx.app.postRunnable(() -> callback.accept(result));
            }).start();
        });
    }

    public static void createMfaChallenge(String factorId,
                                          java.util.function.Consumer<MfaChallengeResult> callback) {
        ensureValidSession(ok -> {
            if (!ok || accessToken == null || factorId == null || factorId.isEmpty()) {
                MfaChallengeResult r = new MfaChallengeResult();
                r.success = false;
                r.error = "invalid_session_or_factor";
                if (callback != null) Gdx.app.postRunnable(() -> callback.accept(r));
                return;
            }

            final String token = accessToken;

            new Thread(() -> {
                MfaChallengeResult result = new MfaChallengeResult();

                try {
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(SUPABASE_URL + "/auth/v1/factors/" + factorId + "/challenge"))
                        .header("Content-Type", "application/json")
                        .header("apikey", API_KEY)
                        .header("Authorization", "Bearer " + token)
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build();

                    HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());

                    System.out.println("createMfaChallenge: " + response.statusCode() + " body=" + response.body());

                    if (response.statusCode() / 100 == 2) {
                        JSONObject json = new JSONObject(response.body());
                        result.success = true;
                        result.challengeId = json.optString("id", null);
                    } else {
                        result.success = false;
                        result.error = extractError(response.body(), "mfa_challenge_failed");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    result.success = false;
                    result.error = "exception";
                }

                if (callback != null) Gdx.app.postRunnable(() -> callback.accept(result));
            }).start();
        });
    }

    public static void verifyMfaChallenge(String factorId,
                                          String challengeId,
                                          String code,
                                          java.util.function.Consumer<MfaVerifyResult> callback) {
        ensureValidSession(ok -> {
            if (!ok || accessToken == null || factorId == null || factorId.isEmpty()
                || challengeId == null || challengeId.isEmpty()
                || code == null || code.isEmpty()) {

                MfaVerifyResult r = new MfaVerifyResult();
                r.success = false;
                r.error = "invalid_verify_request";
                if (callback != null) Gdx.app.postRunnable(() -> callback.accept(r));
                return;
            }

            final String token = accessToken;

            new Thread(() -> {
                MfaVerifyResult result = new MfaVerifyResult();

                try {
                    JSONObject body = new JSONObject();
                    body.put("challenge_id", challengeId);
                    body.put("code", code);

                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(SUPABASE_URL + "/auth/v1/factors/" + factorId + "/verify"))
                        .header("Content-Type", "application/json")
                        .header("apikey", API_KEY)
                        .header("Authorization", "Bearer " + token)
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();

                    HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() / 100 == 2) {
                        result.success = true;

                        // verification can rotate session data, refresh local tokens if returned
                        try {
                            JSONObject json = new JSONObject(response.body());
                            if (json.has("access_token")) {
                                applySessionFromAuthResponse(json);
                                persistSession();
                                isLoggedIn = (accessToken != null && refreshToken != null && userId != null);
                            }
                        } catch (Exception ignored) {}
                    } else {
                        result.success = false;
                        result.error = extractError(response.body(), "mfa_verify_failed");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    result.success = false;
                    result.error = "exception";
                }

                if (callback != null) Gdx.app.postRunnable(() -> callback.accept(result));
            }).start();
        });
    }

    public static void unenrollMfaFactor(String factorId,
                                         java.util.function.Consumer<MfaUnenrollResult> callback) {
        ensureValidSession(ok -> {
            if (!ok || accessToken == null || factorId == null || factorId.isEmpty()) {
                MfaUnenrollResult r = new MfaUnenrollResult();
                r.success = false;
                r.error = "invalid_session_or_factor";
                if (callback != null) Gdx.app.postRunnable(() -> callback.accept(r));
                return;
            }

            final String token = accessToken;

            new Thread(() -> {
                MfaUnenrollResult result = new MfaUnenrollResult();

                try {
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(SUPABASE_URL + "/auth/v1/factors/" + factorId))
                        .header("apikey", API_KEY)
                        .header("Authorization", "Bearer " + token)
                        .method("DELETE", HttpRequest.BodyPublishers.noBody())
                        .build();

                    HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());

                    System.out.println("unenrollMfaFactor: " + response.statusCode() + " body=" + response.body());

                    if (response.statusCode() / 100 == 2) {
                        result.success = true;
                    } else {
                        result.success = false;
                        result.error = extractError(response.body(), "mfa_unenroll_failed");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    result.success = false;
                    result.error = "exception";
                }

                if (callback != null) Gdx.app.postRunnable(() -> callback.accept(result));
            }).start();
        });
    }

    public static void getAuthenticatorAssuranceLevel(java.util.function.Consumer<MfaAalResult> callback) {
        MfaAalResult result = new MfaAalResult();

        try {
            String current = decodeAalFromJwt(accessToken);
            result.currentLevel = current != null ? current : "aal1";

            listMfaFactors(factorsResult -> {
                if (!factorsResult.success) {
                    MfaAalResult r = new MfaAalResult();
                    r.success = false;
                    r.error = factorsResult.error;
                    r.currentLevel = result.currentLevel;

                    System.out.println("getAuthenticatorAssuranceLevel FAILED: " + factorsResult.error);

                    if (callback != null) callback.accept(r);
                    return;
                }

                boolean hasVerified = false;
                for (MfaFactor factor : factorsResult.factors) {
                    if ("verified".equalsIgnoreCase(factor.status)) {
                        hasVerified = true;
                        break;
                    }
                }

                result.success = true;
                result.nextLevel = hasVerified ? "aal2" : "aal1";

                if (callback != null) callback.accept(result);
            });

        } catch (Exception e) {
            result.success = false;
            result.error = "exception";
            if (callback != null) Gdx.app.postRunnable(() -> callback.accept(result));
        }
    }

    public static String getCurrentAal() {
        return decodeAalFromJwt(accessToken);
    }

    private static String decodeAalFromJwt(String jwt) {
        if (jwt == null || jwt.isEmpty()) return null;

        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) return null;

            byte[] decoded = java.util.Base64.getUrlDecoder().decode(parts[1]);
            String payload = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(payload);

            String aal = json.optString("aal", null);
            return (aal == null || aal.isEmpty()) ? null : aal;
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractError(String body, String fallback) {
        try {
            if (body != null && !body.isEmpty()) {
                JSONObject err = new JSONObject(body);
                return err.optString("error",
                    err.optString("message",
                        err.optString("msg",
                            err.optString("error_description", fallback))));
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    public static void resetPassword(String email, Consumer<Boolean> callback) {
        new Thread(() -> {
            boolean success = false;
            try {
                JSONObject body = new JSONObject();
                body.put("email", email);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SUPABASE_URL + "/auth/v1/recover"))
                    .header("Content-Type", "application/json")
                    .header("apikey", API_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

                HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

                System.out.println("Supabase resetPassword response: " + response.statusCode() + " body=" + response.body());
                success = (response.statusCode() / 100 == 2) || response.statusCode() == 204;

            } catch (Exception e) {
                e.printStackTrace();
            }

            boolean finalSuccess = success;
            if (callback != null) {
                Gdx.app.postRunnable(() -> callback.accept(finalSuccess));
            }
        }).start();
    }
}
