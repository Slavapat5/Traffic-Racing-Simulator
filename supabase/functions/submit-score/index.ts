// supabase/functions/submit-score/index.ts
import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

// Allow only known modes to prevent any junk modes
const ALLOWED_MODES = new Set([
  "free_ride",
  "drag_sprint",
  "time_trial",
  "endless_one_way",
  "endless_two_way",
]);

function jsonResponse(status: number, body: Record<string, unknown>) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json",
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Headers": "authorization, content-type",
    },
  });
}

serve(async (req) => {
  // CORS preflight
  if (req.method === "OPTIONS") {
    return new Response(null, {
      status: 204,
      headers: {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Headers": "authorization, content-type",
        "Access-Control-Allow-Methods": "POST, OPTIONS",
      },
    });
  }

  if (req.method !== "POST") {
    return jsonResponse(405, { error: "method_not_allowed" });
  }

  try {
    const authHeader = req.headers.get("Authorization") ?? "";
    const jwt = authHeader.startsWith("Bearer ") ? authHeader.slice(7) : null;
    if (!jwt) {
      return jsonResponse(401, { error: "missing_bearer_token" });
    }

    // Client bound to the caller JWT only for identifying user
    const userClient = createClient(SUPABASE_URL, SERVICE_ROLE_KEY, {
      global: { headers: { Authorization: `Bearer ${jwt}` } },
    });

    const { data: userData, error: userErr } = await userClient.auth.getUser();
    if (userErr || !userData?.user) {
      return jsonResponse(401, { error: "invalid_token" });
    }
    const userId = userData.user.id;

    const body = await req.json().catch(() => null);
    if (!body) return jsonResponse(400, { error: "invalid_json" });

    const mode = String(body.mode ?? "");
    const scoreNum = Number(body.score);

    if (!ALLOWED_MODES.has(mode)) {
      return jsonResponse(400, { error: "invalid_mode" });
    }
    if (!Number.isFinite(scoreNum) || scoreNum <= 0 || scoreNum > 1_000_000_000) {
      return jsonResponse(400, { error: "invalid_score" });
    }

    // Service role client to write (bypasses RLS) - dont expose this key to client
    const admin = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);

    // Only improve score: keep the maximum score for (user_id, mode)
    // This stops someone from overwriting with lower values
    const { data: existing, error: exErr } = await admin
      .from("high_scores")
      .select("score")
      .eq("user_id", userId)
      .eq("mode", mode)
      .maybeSingle();

    if (exErr) {
      return jsonResponse(500, { error: "db_select_failed", details: exErr.message });
    }

    const existingScore = existing?.score ?? null;
    if (existingScore !== null && scoreNum <= existingScore) {
      return jsonResponse(200, { ok: true, updated: false, score: existingScore });
    }

    const { error: upErr } = await admin
      .from("high_scores")
      .upsert(
        { user_id: userId, mode, score: Math.floor(scoreNum) },
        { onConflict: "user_id,mode" }
      );

    if (upErr) {
      return jsonResponse(500, { error: "db_upsert_failed", details: upErr.message });
    }

    return jsonResponse(200, { ok: true, updated: true, score: Math.floor(scoreNum) });
  } catch (e) {
    return jsonResponse(500, { error: "exception", details: String(e?.message ?? e) });
  }
});
