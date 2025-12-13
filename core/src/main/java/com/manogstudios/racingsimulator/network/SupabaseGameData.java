package com.manogstudios.racingsimulator.network;

import com.badlogic.gdx.Gdx;
import com.manogstudios.racingsimulator.CarOwnershipManager;
import com.manogstudios.racingsimulator.CashManager;
import com.manogstudios.racingsimulator.HighScoreManager;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SupabaseGameData {

    private static final String SUPABASE_URL = SupabaseAuth.SUPABASE_URL;
    private static final String API_KEY = SupabaseAuth.API_KEY;

    private static final HttpClient client = HttpClient.newHttpClient();

    private static HttpRequest.Builder baseRequest(String path, String accessToken) {
        return HttpRequest.newBuilder()
            .uri(URI.create(SUPABASE_URL + "/rest/v1/" + path))
            .header("apikey", API_KEY)
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Type", "application/json");
    }

    public static void loadProfile(String userId, String accessToken, Runnable onDone) {
        new Thread(() -> {
            try {
                // GET /rest/v1/profiles?user_id=eq.<uuid>&select=user_id,cash
                String query = "profiles?user_id=eq." + userId + "&select=user_id,cash";
                HttpRequest request = baseRequest(query, accessToken)
                    .GET()
                    .build();

                HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() / 100 != 2) {
                    System.out.println("loadProfile: non-2xx: " + response.statusCode()
                        + " body=" + response.body());
                }

                JSONArray arr = new JSONArray(response.body());

                if (arr.length() == 0) {
                    // No profile yet, create one with default cash
                    int startingCash = 10000;
                    CashManager.setCash(startingCash);
                    CashManager.saveCash();
                    createProfile(userId, accessToken, startingCash);
                } else {
                    JSONObject row = arr.getJSONObject(0);
                    int cash = row.optInt("cash", 10000);
                    CashManager.setCash(cash);
                    CashManager.saveCash();
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (onDone != null) {
                    Gdx.app.postRunnable(onDone);
                }
            }
        }).start();
    }

    public static class LeaderboardEntry {
        public final String userId;
        public final String username;
        public final int score;

        public LeaderboardEntry(String userId, String username, int score) {
            this.userId = userId;
            this.username = username;
            this.score = score;
        }
    }


    public static void fetchLeaderboard(String mode,
                                        int limit,
                                        String accessToken,
                                        Consumer<List<LeaderboardEntry>> callback) {
        if (accessToken == null) {
            Gdx.app.postRunnable(() -> callback.accept(new ArrayList<>()));
            return;
        }

        new Thread(() -> {
            List<LeaderboardEntry> result = new ArrayList<>();
            try {
                // Join profiles: select=user_id,score,profiles(username)
                // GET /rest/v1/high_scores?mode=eq.free_ride&select=user_id,score,profiles(username)&order=score.desc&limit=10
                String query = "high_scores"
                    + "?mode=eq." + mode
                    + "&select=user_id,score,profiles(username)"
                    + "&order=score.desc"
                    + "&limit=" + limit;

                HttpRequest request = baseRequest(query, accessToken)
                    .GET()
                    .build();

                HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() / 100 != 2) {
                    System.out.println("fetchLeaderboard: non-2xx: " + response.statusCode()
                        + " body=" + response.body());
                } else {
                    JSONArray arr = new JSONArray(response.body());
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject row = arr.getJSONObject(i);
                        String userId = row.getString("user_id");
                        int score     = row.getInt("score");

                        // Nested object for profiles
                        String username = null;
                        if (row.has("profiles") && !row.isNull("profiles")) {
                            JSONObject prof = row.getJSONObject("profiles");
                            if (prof.has("username") && !prof.isNull("username")) {
                                username = prof.getString("username");
                            }
                        }

                        // Fallback if username is null/empty
                        if (username == null || username.isEmpty()) {
                            String shortId = userId.length() > 8
                                ? userId.substring(0, 8)
                                : userId;
                            username = "Player " + shortId;
                        }

                        result.add(new LeaderboardEntry(userId, username, score));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Gdx.app.postRunnable(() -> callback.accept(result));
            }
        }).start();
    }

    public static void fetchUsername(String userId, String accessToken, Consumer<String> callback) {
        if (userId == null || accessToken == null) {
            Gdx.app.postRunnable(() -> callback.accept(null));
            return;
        }

        new Thread(() -> {
            String username = null;
            try {
                // GET /rest/v1/profiles?user_id=eq.<uuid>&select=username
                String query = "profiles?user_id=eq." + userId + "&select=username";
                HttpRequest request = baseRequest(query, accessToken)
                    .GET()
                    .build();

                HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() / 100 != 2) {
                    System.out.println("fetchUsername: non-2xx: " + response.statusCode()
                        + " body=" + response.body());
                } else {
                    JSONArray arr = new JSONArray(response.body());
                    if (arr.length() > 0) {
                        JSONObject row = arr.getJSONObject(0);
                        if (row.has("username") && !row.isNull("username")) {
                            username = row.getString("username");
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                String finalUsername = username;
                Gdx.app.postRunnable(() -> callback.accept(finalUsername));
            }
        }).start();
    }


    public static void resetHighScores(String userId, String accessToken) {
        if (userId == null || accessToken == null) return;

        new Thread(() -> {
            try {
                // DELETE /rest/v1/high_scores?user_id=eq.<uuid>
                String query = "high_scores?user_id=eq." + userId;

                HttpRequest request = baseRequest(query, accessToken)
                    .method("DELETE", HttpRequest.BodyPublishers.noBody())
                    .build();

                HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() / 100 != 2) {
                    System.out.println("resetHighScores: non-2xx: " + response.statusCode()
                        + " body=" + response.body());
                } else {
                    System.out.println("resetHighScores: all highs deleted for user " + userId);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public static void updateUsername(String userId,
                                      String accessToken,
                                      String username,
                                      Consumer<Boolean> callback) {
        if (userId == null || accessToken == null || username == null || username.isEmpty()) {
            if (callback != null) {
                Gdx.app.postRunnable(() -> callback.accept(false));
            }
            return;
        }

        new Thread(() -> {
            boolean success = false;
            try {
                JSONObject body = new JSONObject();
                body.put("username", username);

                String query = "profiles?user_id=eq." + userId;
                HttpRequest request = baseRequest(query, accessToken)
                    .header("Prefer", "return=minimal")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

                HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() / 100 == 2) {
                    success = true;
                    System.out.println("updateUsername: username set to " + username + " for " + userId);
                } else {
                    System.out.println("updateUsername: non-2xx: " + response.statusCode()
                        + " body=" + response.body());
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (callback != null) {
                    boolean finalSuccess = success;
                    Gdx.app.postRunnable(() -> callback.accept(finalSuccess));
                }
            }
        }).start();
    }



    private static void createProfile(String userId, String accessToken, int cash) throws Exception {
        JSONObject body = new JSONObject();
        body.put("user_id", userId);
        body.put("cash", cash);

        HttpRequest request = baseRequest("profiles", accessToken)
            .header("Prefer", "return=minimal")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build();

        HttpResponse<String> response =
            client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() / 100 != 2) {
            System.out.println("createProfile: non-2xx: " + response.statusCode()
                + " body=" + response.body());
        }
    }

    public static void saveOwnedCar(String userId, String accessToken, String carImage) {
        if (userId == null || accessToken == null) return;

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("user_id", userId);
                body.put("car_image", carImage);

                HttpRequest request = baseRequest("owned_cars", accessToken)
                    .header("Prefer", "return=minimal")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

                HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() / 100 != 2) {
                    System.out.println("saveOwnedCar: non-2xx: " + response.statusCode()
                        + " body=" + response.body());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }


    public static void loadOwnedCars(String userId, String accessToken, Runnable onDone) {
        new Thread(() -> {
            try {
                // GET /rest/v1/owned_cars?user_id=eq.<uuid>&select=car_image
                String query = "owned_cars?user_id=eq." + userId + "&select=car_image";
                HttpRequest request = baseRequest(query, accessToken)
                    .GET()
                    .build();

                HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() / 100 != 2) {
                    System.out.println("loadOwnedCars: non-2xx: " + response.statusCode()
                        + " body=" + response.body());
                }

                JSONArray arr = new JSONArray(response.body());

                // Clear current owned cars and rebuild from cloud
                CarOwnershipManager.clearOwnedCars();

                if (arr.length() == 0) {
                    // First time this user: give starter car
                    String starter = "Mazda MX-5 Miata - 2014.png";
                    CarOwnershipManager.addCar(starter);
                    // Also push this starter car to Supabase
                    saveOwnedCar(userId, accessToken, starter);
                } else {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject row = arr.getJSONObject(i);
                        String carImage = row.getString("car_image");
                        CarOwnershipManager.addCar(carImage);
                    }
                }

                // Optionally persist to local file as cache
                CarOwnershipManager.saveOwnedCars();

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (onDone != null) {
                    Gdx.app.postRunnable(onDone);
                }
            }
        }).start();
    }

    public static void removeOwnedCar(String userId, String accessToken, String carImage) {
        if (userId == null || accessToken == null) return;

        new Thread(() -> {
            try {
                // Encode carImage so spaces etc. don't break the URL
                String encodedCarImage = URLEncoder.encode(carImage, StandardCharsets.UTF_8);

                // DELETE /rest/v1/owned_cars?user_id=eq.<uuid>&car_image=eq.<encodedCarImage>
                String query = "owned_cars?user_id=eq." + userId +
                    "&car_image=eq." + encodedCarImage;

                HttpRequest request = baseRequest(query, accessToken)
                    .method("DELETE", HttpRequest.BodyPublishers.noBody())
                    .build();

                HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() / 100 != 2) {
                    System.out.println("removeOwnedCar: non-2xx: " + response.statusCode()
                        + " body=" + response.body());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public static void loadHighScores(String userId, String accessToken, Runnable onDone) {
        new Thread(() -> {
            try {
                // GET /rest/v1/high_scores?user_id=eq.<uuid>&select=mode,score
                String query = "high_scores?user_id=eq." + userId + "&select=mode,score";
                HttpRequest request = baseRequest(query, accessToken)
                    .GET()
                    .build();

                HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() / 100 != 2) {
                    System.out.println("loadHighScores: non-2xx: " + response.statusCode()
                        + " body=" + response.body());
                }

                JSONArray arr = new JSONArray(response.body());

                HighScoreManager.clearScores();

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject row = arr.getJSONObject(i);
                    String mode  = row.getString("mode");
                    int score    = row.getInt("score");
                    HighScoreManager.setScoreFromCloud(mode, score);
                }

                // Optionally cache to local file
                HighScoreManager.saveHighScores();

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (onDone != null) {
                    Gdx.app.postRunnable(onDone);
                }
            }
        }).start();
    }

    public static void saveHighScore(String userId, String accessToken, String mode, int score) {
        if (userId == null || accessToken == null) return;

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("user_id", userId);
                body.put("mode", mode);
                body.put("score", score);

                // POST /rest/v1/high_scores?on_conflict=user_id,mode
                HttpRequest request = baseRequest("high_scores?on_conflict=user_id,mode", accessToken)
                    .header("Prefer", "resolution=merge-duplicates") // upsert
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

                HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() / 100 != 2) {
                    System.out.println("saveHighScore: non-2xx: " + response.statusCode()
                        + " body=" + response.body());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }


    public static void upsertProfileUsername(String userId, String accessToken, String username) {
        if (userId == null || accessToken == null) return;

        new Thread(() -> {
            try {

                String path = "profiles";

                String jsonBody = String.format(
                    "{ \"user_id\": \"%s\", \"username\": \"%s\" }",
                    userId, username
                );

                HttpRequest request = baseRequest(path, accessToken)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "resolution=merge-duplicates") // UPSERT
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

                HttpResponse<String> response = client.send(
                    request, HttpResponse.BodyHandlers.ofString()
                );

                if (response.statusCode() / 100 != 2) {
                    System.out.println("upsertProfileUsername: non-2xx: " +
                        response.statusCode() + " body=" + response.body());
                } else {
                    System.out.println("upsertProfileUsername: username set for user " + userId);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }


    /** Push new cash value to Supabase (PATCH profiles row). */
    public static void saveCash(String userId, String accessToken, int cash) {
        if (userId == null || accessToken == null) return;

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("cash", cash);

                String query = "profiles?user_id=eq." + userId;
                HttpRequest request = baseRequest(query, accessToken)
                    .header("Prefer", "return=minimal")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

                HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() / 100 != 2) {
                    System.out.println("saveCash: non-2xx: " + response.statusCode()
                        + " body=" + response.body());
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
