import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

function json(status: number, body: any) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function pushFlag(flags: string[], f: string) {
  if (!flags.includes(f)) flags.push(f);
}

function getModeCaps(mode: string) {
  switch (mode) {
    case "free_ride":
      return { maxMph: 260, maxScorePerSec: 6000, maxDistPerSecM: 140 }; // 140 m/s ~ 313 mph
    case "endless_one_way":
      return { maxMph: 300, maxScorePerSec: 9000, maxDistPerSecM: 170 };
    case "endless_two_way":
      return { maxMph: 300, maxScorePerSec: 10000, maxDistPerSecM: 170 };
    case "time_trial":
      return { maxMph: 260, maxScorePerSec: 8000, maxDistPerSecM: 140 };
    case "test_drive":
      return { maxMph: 260, maxScorePerSec: 999999, maxDistPerSecM: 140 }; // score irrelevant
    default:
      return { maxMph: 260, maxScorePerSec: 7000, maxDistPerSecM: 140 };
  }
}

Deno.serve(async (req) => {
  try {
    const projectUrl = Deno.env.get("PROJECT_URL")!;
    const serviceRoleKey = Deno.env.get("SERVICE_ROLE_KEY")!;

    const authHeader = req.headers.get("Authorization") ?? "";
    if (!authHeader.startsWith("Bearer ")) {
      return json(401, { ok: false, error: "missing_token" });
    }

    // 1) Identify caller (use JWT to find user)
    const supabaseAuth = createClient(projectUrl, serviceRoleKey, {
      global: { headers: { Authorization: authHeader } },
    });

    const { data: { user }, error: userErr } = await supabaseAuth.auth.getUser();
    if (userErr || !user) {
      return json(401, { ok: false, error: "invalid_token" });
    }

    // 2) Parse body
    const body = await req.json().catch(() => ({}));

    const mode = String(body.mode ?? "").trim();
    const started_at = String(body.started_at ?? "").trim();
    const ended_at = String(body.ended_at ?? "").trim();

    const duration_sec = Number(body.duration_sec);
    const distance_m = Number(body.distance_m);
    const score = Number(body.score);

    const crashes = Number.isFinite(body.crashes) ? Number(body.crashes) : 0;
    const near_misses = Number.isFinite(body.near_misses) ? Number(body.near_misses) : 0;

    const avg_speed_mph = Number.isFinite(body.avg_speed_mph) ? Number(body.avg_speed_mph) : null;
    const max_speed_mph = Number.isFinite(body.max_speed_mph) ? Number(body.max_speed_mph) : null;

    const car_id = (body.car_id != null) ? String(body.car_id) : null;
    const client_version = (body.client_version != null) ? String(body.client_version) : null;

    // 3) Hard sanity checks (reject obvious garbage)
    if (!mode) return json(400, { ok: false, error: "invalid_mode" });
    if (!started_at || !ended_at) return json(400, { ok: false, error: "invalid_timestamps" });

    if (!Number.isFinite(duration_sec) || duration_sec <= 0 || duration_sec > 60 * 60) {
      return json(400, { ok: false, error: "invalid_duration" });
    }
    if (!Number.isFinite(distance_m) || distance_m < 0 || distance_m > 5_000_000) {
      return json(400, { ok: false, error: "invalid_distance" });
    }
    if (!Number.isFinite(score) || score < 0 || score > 50_000_000) {
      return json(400, { ok: false, error: "invalid_score" });
    }
    if (crashes < 0 || crashes > 10_000) return json(400, { ok: false, error: "invalid_crashes" });
    if (near_misses < 0 || near_misses > 1_000_000) return json(400, { ok: false, error: "invalid_near_misses" });

    //  (distance/duration)=m/s, mph = m/s * 2.23694
    const impliedMps = distance_m / Math.max(duration_sec, 0.1);
    const impliedMph = impliedMps * 2.2369362920544;

    // This is a HARD reject only if it's totally impossible.
    // Keep this high
    if (impliedMph > 600) {
      return json(400, { ok: false, error: "impossible_speed" });
    }

    // 4) Flag suspicious runs (accept + store flags)
    const flags: string[] = [];
    const caps = getModeCaps(mode);

    // Timestamp consistency + drift
    const startMs = Date.parse(started_at);
    const endMs = Date.parse(ended_at);
    if (!Number.isFinite(startMs) || !Number.isFinite(endMs)) {
      pushFlag(flags, "timestamps_unparseable");
    } else {
      if (endMs < startMs) pushFlag(flags, "ended_before_started");

      const wallSec = (endMs - startMs) / 1000;
      if (Number.isFinite(wallSec) && wallSec > 0) {
        const drift = Math.abs(wallSec - duration_sec);
        if (drift > Math.max(2.0, wallSec * 0.15)) pushFlag(flags, "duration_drift");
      }
    }

    // Rate-based plausibility
    const scorePerSec = score / Math.max(duration_sec, 0.1);

    if (impliedMps > caps.maxDistPerSecM) pushFlag(flags, "distance_rate_too_high");
    if (impliedMph > caps.maxMph) pushFlag(flags, "speed_above_mode_cap");
    if (scorePerSec > caps.maxScorePerSec) pushFlag(flags, "score_rate_too_high");

    // sanity for client-reported speeds
    if (avg_speed_mph != null) {
      if (avg_speed_mph < 0 || avg_speed_mph > 600) pushFlag(flags, "avg_speed_out_of_range");
      if (Math.abs(avg_speed_mph - impliedMph) > 120) pushFlag(flags, "avg_speed_mismatch");
    }
    if (max_speed_mph != null) {
      if (max_speed_mph < 0 || max_speed_mph > 700) pushFlag(flags, "max_speed_out_of_range");
      if (avg_speed_mph != null && max_speed_mph + 1 < avg_speed_mph) pushFlag(flags, "max_lt_avg");
    }


    const is_suspicious = flags.length > 0;

    // 5) Write using service role
    const supabase = createClient(projectUrl, serviceRoleKey);

    const insertPayload: any = {
      user_id: user.id,
      mode,
      started_at,
      ended_at,
      duration_sec,
      distance_m: Math.floor(distance_m),
      score: Math.floor(score),
      crashes: Math.floor(crashes),
      near_misses: Math.floor(near_misses),
      avg_speed_mph,
      max_speed_mph,
      car_id,
      client_version,

      // derived + flags
      is_suspicious,
      anomaly_flags: flags,
      score_rate: Number.isFinite(scorePerSec) ? scorePerSec : null,
      implied_avg_speed_mps: Number.isFinite(impliedMps) ? impliedMps : null,
      implied_avg_speed_mph: Number.isFinite(impliedMph) ? impliedMph : null,
    };

    const { data, error } = await supabase
      .from("run_telemetry")
      .insert(insertPayload)
      .select("id, is_suspicious, anomaly_flags")
      .single();

    if (error) {
      return json(500, { ok: false, error: "db_write_failed", details: error.message });
    }

    // Return flags
    return json(200, { ok: true, id: data.id, is_suspicious: data.is_suspicious, flags: data.anomaly_flags });

  } catch (_e) {
    return json(500, { ok: false, error: "exception" });
  }
});
