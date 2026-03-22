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
import java.util.function.Consumer;

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

    /** Call this at startup  */
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

    /** Ensures accessToken is valid (refreshes if needed). Callback runs on LibGDX thread. */
    public static void ensureValidSession(Consumer<Boolean> callback) {
        // Not logged in
        if (!isLoggedIn || refreshToken == null || refreshToken.isEmpty()) {
            if (callback != null) Gdx.app.postRunnable(() -> callback.accept(false));
            return;
        }

        long now = System.currentTimeMillis();

        // If token is still valid and not near expiry, its good
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
        // access_token is always present on successful login/refresh
        String at = json.optString("access_token", null);
        if (at != null && !at.isEmpty()) {
            accessToken = at;
        }

        // refresh_token present on login/refresh
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
            accessTokenExpiresAtMillis = System.currentTimeMillis() + 1000;//accessTokenExpiresAtMillis = System.currentTimeMillis() + (expiresInSec * 1000L);
        } else {
            // Fallback: if not provided, treat token as soon expiring
            accessTokenExpiresAtMillis = System.currentTimeMillis() + 1000; //60_000L
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
