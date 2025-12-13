package com.manogstudios.racingsimulator.network;

import com.badlogic.gdx.Gdx;
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
    public static String accessToken = null;
    public static String userId = null;
    public static String lastErrorCode = null;
    public static String lastErrorMessage = null;

    static final String SUPABASE_URL = "https://trfecuqpkrjobgxrmgwm.supabase.co";
    static final String API_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRyZmVjdXFwa3Jqb2JneHJtZ3dtIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTIwNzc0MjgsImV4cCI6MjA2NzY1MzQyOH0._sByi5zdyPnViPIzTsidac2REfnq5GCC89_zD7TLyEs";

    public static void login(String email, String password, Consumer<Boolean> callback) {
        new Thread(() -> {
            try {
                // reset last error
                lastErrorCode = null;
                lastErrorMessage = null;

                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SUPABASE_URL + "/auth/v1/token?grant_type=password"))
                    .header("Content-Type", "application/json")
                    .header("apikey", API_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(String.format(
                        "{\"email\":\"%s\",\"password\":\"%s\"}", email, password
                    )))
                    .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    isLoggedIn = true;

                    JSONObject json = new JSONObject(response.body());
                    accessToken = json.getString("access_token");

                    String userId = json.getJSONObject("user").getString("id");
                    SupabaseAuth.userId = userId;

                    callback.accept(true);
                } else {
                    // login failed – try to parse error details
                    isLoggedIn = false;

                    String body = response.body();
                    System.out.println("Supabase login error: " + response.statusCode() + " body=" + body);

                    try {
                        if (body != null && !body.isEmpty()) {
                            JSONObject err = new JSONObject(body);
                            lastErrorCode = err.optString("error", null);
                            lastErrorMessage = err.optString("error_description", null);
                        }
                    } catch (Exception parseEx) {
                        parseEx.printStackTrace();
                    }

                    callback.accept(false);
                }
            } catch (Exception e) {
                e.printStackTrace();
                isLoggedIn = false;
                lastErrorCode = "exception";
                lastErrorMessage = e.getMessage();
                callback.accept(false);
            }
        }).start();
    }



    public static void logout() {
        isLoggedIn = false;
        accessToken = null;
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
                HttpClient client = HttpClient.newHttpClient();

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



    public static boolean registerUser(String email, String password) {
        try {
            URL url = new URL(SUPABASE_URL + "/auth/v1/signup");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("apikey", API_KEY);
            conn.setDoOutput(true);

            String jsonInput = String.format(
                "{ \"email\": \"%s\", \"password\": \"%s\" }",
                email, password
            );

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInput.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int status = conn.getResponseCode();

            InputStream stream = (status >= 400) ? conn.getErrorStream() : conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            System.out.println("Supabase registration response: " + response);

            conn.disconnect();
            return status == 200 || status == 201;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    public static boolean registerUser(String email, String password, String username) {
        return registerUser(email, password);
    }

    public static void resetPassword(String email, Consumer<Boolean> callback) {
        new Thread(() -> {
            boolean success = false;
            try {
                URL url = new URL(SUPABASE_URL + "/auth/v1/recover");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("apikey", API_KEY);
                conn.setDoOutput(true);

                String jsonInput = String.format(
                    "{ \"email\": \"%s\" }",
                    email
                );

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonInput.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int status = conn.getResponseCode();
                InputStream stream = (status >= 400) ? conn.getErrorStream() : conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                System.out.println("Supabase resetPassword response: " + response);

                success = (status == 200 || status == 201 || status == 204);

                conn.disconnect();
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
