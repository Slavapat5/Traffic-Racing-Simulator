package com.manogstudios.racingsimulator.network;

import com.badlogic.gdx.Gdx;
import com.manogstudios.racingsimulator.CarOwnershipManager;
import com.manogstudios.racingsimulator.CashManager;
import com.manogstudios.racingsimulator.HighScoreManager;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SupabaseGameData {

    private static final String SUPABASE_URL = SupabaseAuth.SUPABASE_URL;
    private static final String API_KEY = SupabaseAuth.API_KEY;

    private static final HttpClient client = HttpClient.newHttpClient();

    /**
     * Ensures there is a valid access token then provides it to useToken.
     */
    private static void withValidToken(java.util.function.Consumer<String> useToken,
                                       java.util.function.Consumer<String> onFail) {
        SupabaseAuth.ensureValidSession((ok) -> {
            if (!ok || SupabaseAuth.accessToken == null) {
                if (onFail != null) Gdx.app.postRunnable(() -> onFail.accept("not_logged_in_or_expired"));
                return;
            }
            final String token = SupabaseAuth.accessToken;
            if (useToken != null) Gdx.app.postRunnable(() -> useToken.accept(token));
        });
    }



    private static HttpRequest.Builder baseRequest(String path, String accessToken) {
        return HttpRequest.newBuilder()
            .uri(URI.create(SUPABASE_URL + "/rest/v1/" + path))
            .header("apikey", API_KEY)
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Type", "application/json");
    }

    public static void purchaseCar(String carImage,
                                   int price,
                                   java.util.function.Consumer<Integer> onSuccessCash,
                                   java.util.function.Consumer<String> onFail) {

        if (!SupabaseAuth.isLoggedIn) {
            if (onFail != null) Gdx.app.postRunnable(() -> onFail.accept("not_logged_in"));
            return;
        }
        if (carImage == null || carImage.isEmpty()) {
            if (onFail != null) Gdx.app.postRunnable(() -> onFail.accept("invalid_car"));
            return;
        }
        if (price <= 0) {
            if (onFail != null) Gdx.app.postRunnable(() -> onFail.accept("invalid_price"));
            return;
        }

        withValidToken((token) -> {
            new Thread(() -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("car_image", carImage);
                    body.put("price", price);

                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(SUPABASE_URL + "/functions/v1/purchase-car"))
                        .header("Content-Type", "application/json")
                        .header("apikey", API_KEY)
                        .header("Authorization", "Bearer " + token)
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();

                    HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());

                    System.out.println("purchaseCar(function): " + response.statusCode() + " body=" + response.body());

                    if (response.statusCode() / 100 != 2) {
                        String msg = "purchase_failed";
                        try {
                            JSONObject err = new JSONObject(response.body());
                            msg = err.optString("error",
                                err.optString("message",
                                    err.optString("details", msg)));
                        } catch (Exception ignored) {}
                        String finalMsg = msg;
                        if (onFail != null) Gdx.app.postRunnable(() -> onFail.accept(finalMsg));
                        return;
                    }

                    JSONObject json = new JSONObject(response.body());
                    boolean ok = json.optBoolean("ok", true);

                    if (!ok) {
                        String msg = json.optString("error", "purchase_failed");
                        if (onFail != null) Gdx.app.postRunnable(() -> onFail.accept(msg));
                        return;
                    }

                    int newCash = json.has("cash") ? json.optInt("cash", -1) : json.optInt("new_cash", -1);
                    int finalNewCash = newCash;

                    Gdx.app.postRunnable(() -> {
                        if (onSuccessCash != null) onSuccessCash.accept(finalNewCash);
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                    if (onFail != null) Gdx.app.postRunnable(() -> onFail.accept("exception"));
                }
            }).start();
        }, (err) -> {
            if (onFail != null) Gdx.app.postRunnable(() -> onFail.accept(err));
        });
    }

    public static void unlockAchievement(String achievementId,
                                         Runnable onSuccess,
                                         java.util.function.Consumer<String> onFail) {

        if (!SupabaseAuth.isLoggedIn) {
            if (onFail != null) Gdx.app.postRunnable(() -> onFail.accept("not_logged_in"));
            return;
        }
        if (achievementId == null || achievementId.isEmpty()) {
            if (onFail != null) Gdx.app.postRunnable(() -> onFail.accept("invalid_achievement"));
            return;
        }

        withValidToken((token) -> {
            new Thread(() -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("achievement_id", achievementId);

                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(SUPABASE_URL + "/functions/v1/unlock-achievement"))
                        .header("Content-Type", "application/json")
                        .header("apikey", API_KEY)
                        .header("Authorization", "Bearer " + token)
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();

                    HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());

                    System.out.println("unlockAchievement(function): " + response.statusCode() + " body=" + response.body());

                    if (response.statusCode() / 100 != 2) {
                        String msg = "unlock_failed";
                        try {
                            JSONObject err = new JSONObject(response.body());
                            msg = err.optString("error",
                                err.optString("message",
                                    err.optString("details", msg)));
                        } catch (Exception ignored) {}
                        String finalMsg = msg;
                        if (onFail != null) Gdx.app.postRunnable(() -> onFail.accept(finalMsg));
                        return;
                    }

                    JSONObject json = new JSONObject(response.body());
                    boolean ok = json.optBoolean("ok", true);
                    if (!ok) {
                        String msg = json.optString("error", "unlock_failed");
                        if (onFail != null) Gdx.app.postRunnable(() -> onFail.accept(msg));
                        return;
                    }

                    if (onSuccess != null) Gdx.app.postRunnable(onSuccess);

                } catch (Exception e) {
                    e.printStackTrace();
                    if (onFail != null) Gdx.app.postRunnable(() -> onFail.accept("exception"));
                }
            }).start();
        }, (err) -> {
            if (onFail != null) Gdx.app.postRunnable(() -> onFail.accept(err));
        });
    }

    public static void submitRunTelemetry(
        String mode,
        long startedAtMillis,
        long endedAtMillis,
        float durationSec,
        int distanceMeters,
        int score,
        int crashes,
        int nearMisses,
        Float avgSpeedMph,
        Float maxSpeedMph,
        String carId,
        String clientVersion,
        Runnable onSuccess,
        java.util.function.Consumer<String> onFail) {

        if (!SupabaseAuth.isLoggedIn) {
            if (onFail != null) Gdx.app.postRunnable(() -> onFail.accept("not_logged_in"));
            return;
        }

        withValidToken((token) -> {
            new Thread(() -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("mode", mode);
                    body.put("started_at", Instant.ofEpochMilli(startedAtMillis).toString());
                    body.put("ended_at", Instant.ofEpochMilli(endedAtMillis).toString());
                    body.put("duration_sec", durationSec);
                    body.put("distance_m", distanceMeters);
                    body.put("score", score);
                    body.put("crashes", crashes);
                    body.put("near_misses", nearMisses);

                    if (avgSpeedMph != null) body.put("avg_speed_mph", avgSpeedMph);
                    if (maxSpeedMph != null) body.put("max_speed_mph", maxSpeedMph);
                    if (carId != null) body.put("car_id", carId);
                    if (clientVersion != null) body.put("client_version", clientVersion);

                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(SUPABASE_URL + "/functions/v1/submit-run"))
                        .header("Content-Type", "application/json")
                        .header("apikey", API_KEY)
                        .header("Authorization", "Bearer " + token)
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();

                    HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());

                    System.out.println("submitRunTelemetry(function): " + response.statusCode() + " body=" + response.body());

                    if (response.statusCode() / 100 != 2) {
                        String msg = "submit_failed";
                        try {
                            JSONObject err = new JSONObject(response.body());
                            msg = err.optString("error",
                                err.optString("message",
                                    err.optString("details", msg)));
                        } catch (Exception ignored) {}
                        String finalMsg = msg;
                        if (onFail != null) Gdx.app.postRunnable(() -> onFail.accept(finalMsg));
                        return;
                    }

                    if (onSuccess != null) Gdx.app.postRunnable(onSuccess);

                } catch (Exception e) {
                    e.printStackTrace();
                    if (onFail != null) Gdx.app.postRunnable(() -> onFail.accept("exception"));
                }
            }).start();
        }, (err) -> {
            if (onFail != null) Gdx.app.postRunnable(() -> onFail.accept(err));
        });
    }


    public static void adjustCash(int delta,
                                  java.util.function.Consumer<Integer> onSuccess,
                                  java.util.function.Consumer<String> onFail) {

        if (!SupabaseAuth.isLoggedIn) {
            if (onFail != null) Gdx.app.postRunnable(() -> onFail.accept("not_logged_in"));
            return;
        }

        withValidToken((token) -> {
            new Thread(() -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("delta", delta);

                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(SUPABASE_URL + "/functions/v1/adjust-cash"))
                        .header("Content-Type", "application/json")
                        .header("apikey", API_KEY)
                        .header("Authorization", "Bearer " + token)
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();

                    HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());

                    System.out.println("adjustCash(function): " + response.statusCode() + " body=" + response.body());

                    if (response.statusCode() / 100 != 2) {
                        String msg = "adjust_cash_failed";
                        try {
                            JSONObject err = new JSONObject(response.body());
                            msg = err.optString("error",
                                err.optString("message",
                                    err.optString("details", msg)));
                        } catch (Exception ignored) {}
                        String finalMsg = msg;
                        if (onFail != null) Gdx.app.postRunnable(() -> onFail.accept(finalMsg));
                        return;
                    }

                    JSONObject json = new JSONObject(response.body());
                    int newCash = json.getInt("cash");

                    if (onSuccess != null) Gdx.app.postRunnable(() -> onSuccess.accept(newCash));

                } catch (Exception e) {
                    e.printStackTrace();
                    if (onFail != null) Gdx.app.postRunnable(() -> onFail.accept("exception"));
                }
            }).start();
        }, (err) -> {
            if (onFail != null) Gdx.app.postRunnable(() -> onFail.accept(err));
        });
    }

    public static void loadProfile(String userId, String accessToken, Runnable onDone) {
        new Thread(() -> {
            try {
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

    public static void loadAchievements(String userId,
                                        String accessToken,
                                        Consumer<JSONArray> callback) {
        if (userId == null || accessToken == null) {
            if (callback != null) Gdx.app.postRunnable(() -> callback.accept(new JSONArray()));
            return;
        }

        new Thread(() -> {
            JSONArray arr = new JSONArray();
            try {
                String query = "user_achievements?user_id=eq." + userId
                    + "&select=achievement_id,unlocked_at";

                HttpRequest request = baseRequest(query, accessToken)
                    .GET()
                    .build();

                HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() / 100 != 2) {
                    System.out.println("loadAchievements: non-2xx: " + response.statusCode()
                        + " body=" + response.body());
                } else {
                    arr = new JSONArray(response.body());
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                JSONArray finalArr = arr;
                if (callback != null) {
                    Gdx.app.postRunnable(() -> callback.accept(finalArr));
                }
            }
        }).start();
    }

    public static void upsertPublicUsername(String userId,
                                            String accessToken,
                                            String username,
                                            Consumer<Boolean> callback) {
        if (userId == null || accessToken == null || username == null || username.isEmpty()) {
            if (callback != null) Gdx.app.postRunnable(() -> callback.accept(false));
            return;
        }

        new Thread(() -> {
            boolean success = false;
            try {
                JSONObject body = new JSONObject();
                body.put("user_id", userId);
                body.put("username", username);

                HttpRequest request = baseRequest("public_profiles?on_conflict=user_id", accessToken)
                    .header("Prefer", "resolution=merge-duplicates")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

                HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() / 100 == 2) {
                    success = true;
                } else {
                    System.out.println("upsertPublicUsername: non-2xx: " + response.statusCode()
                        + " body=" + response.body());
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

    // NOTE: If  user_achievements table is server-only, prefer unlockAchievement() and avoid this REST write.
    public static void saveAchievementUnlocked(String userId,
                                               String accessToken,
                                               String achievementId,
                                               long unlockedAtMillis) {
        if (userId == null || accessToken == null || achievementId == null) return;

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("user_id", userId);
                body.put("achievement_id", achievementId);

                String iso = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(unlockedAtMillis));
                body.put("unlocked_at", iso);

                HttpRequest request = baseRequest("user_achievements?on_conflict=user_id,achievement_id", accessToken)
                    .header("Prefer", "resolution=merge-duplicates")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

                HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() / 100 != 2) {
                    System.out.println("saveAchievementUnlocked: non-2xx: " + response.statusCode()
                        + " body=" + response.body());
                }
            } catch (Exception e) {
                e.printStackTrace();
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
                        int score = row.getInt("score");

                        String username = null;
                        if (row.has("profiles") && !row.isNull("profiles")) {
                            JSONObject prof = row.getJSONObject("profiles");
                            if (prof.has("username") && !prof.isNull("username")) {
                                username = prof.getString("username");
                            }
                        }

                        if (username == null || username.isEmpty()) {
                            String shortId = userId.length() > 8 ? userId.substring(0, 8) : userId;
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
        System.out.println("saveOwnedCar DEBUG: userId=" + userId
            + " authUserId=" + SupabaseAuth.userId
            + " tokenPresent=" + (accessToken != null && !accessToken.isEmpty())
            + " carImage=" + carImage);

        if (userId == null || accessToken == null || carImage == null || carImage.isEmpty()) return;

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("user_id", userId);
                body.put("car_image", carImage);

                HttpRequest request = baseRequest("owned_cars?on_conflict=user_id,car_image", accessToken)
                    .header("Prefer", "resolution=merge-duplicates,return=minimal")
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

                CarOwnershipManager.clearOwnedCars();

                if (arr.length() == 0) {
                    String starter = "Mazda MX-5 Miata - 2014.png";
                    CarOwnershipManager.addCarFromCloud(starter);
                    saveOwnedCar(userId, accessToken, starter);
                } else {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject row = arr.getJSONObject(i);
                        String carImage = row.getString("car_image");
                        CarOwnershipManager.addCarFromCloud(carImage);
                    }
                }

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
                String encodedCarImage = URLEncoder.encode(carImage, StandardCharsets.UTF_8);

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
                    String mode = row.getString("mode");
                    int score = row.getInt("score");
                    HighScoreManager.setScoreFromCloud(mode, score);
                }

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

    public static void saveHighScore_UNSAFE(String userId, String accessToken, String mode, int score) {
        if (userId == null || accessToken == null) return;

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("user_id", userId);
                body.put("mode", mode);
                body.put("score", score);

                HttpRequest request = baseRequest("high_scores?on_conflict=user_id,mode", accessToken)
                    .header("Prefer", "resolution=merge-duplicates")
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

    public static void submitScore(String mode, int score) {
        if (!SupabaseAuth.isLoggedIn) return;

        withValidToken((token) -> {
            new Thread(() -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("mode", mode);
                    body.put("score", score);

                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(SUPABASE_URL + "/functions/v1/submit-score"))
                        .header("Content-Type", "application/json")
                        .header("apikey", API_KEY)
                        .header("Authorization", "Bearer " + token)
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();

                    HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() / 100 != 2) {
                        System.out.println("submitScore(function): non-2xx: " + response.statusCode()
                            + " body=" + response.body());
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }, (err) -> {
            System.out.println("submitScore: token invalid: " + err);
        });
    }

    public static void adjustCash(int delta, String reason, java.util.function.Consumer<Integer> callback) {
        if (!SupabaseAuth.isLoggedIn) {
            if (callback != null) Gdx.app.postRunnable(() -> callback.accept(null));
            return;
        }

        withValidToken((token) -> {
            new Thread(() -> {
                Integer newCash = null;
                try {
                    JSONObject body = new JSONObject();
                    body.put("delta", delta);
                    body.put("reason", reason);

                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(SUPABASE_URL + "/functions/v1/adjust-cash"))
                        .header("Content-Type", "application/json")
                        .header("apikey", API_KEY)
                        .header("Authorization", "Bearer " + token)
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();

                    HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() / 100 == 2) {
                        JSONObject res = new JSONObject(response.body());
                        if (res.optBoolean("ok", false)) {
                            newCash = res.getInt("cash");
                        } else {
                            System.out.println("adjustCash: ok=false body=" + response.body());
                        }
                    } else {
                        System.out.println("adjustCash: non-2xx: " + response.statusCode()
                            + " body=" + response.body());
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    Integer finalCash = newCash;
                    if (callback != null) {
                        Gdx.app.postRunnable(() -> callback.accept(finalCash));
                    }
                }
            }).start();
        }, (err) -> {
            if (callback != null) Gdx.app.postRunnable(() -> callback.accept(null));
        });
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
                    .header("Prefer", "resolution=merge-duplicates")
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
