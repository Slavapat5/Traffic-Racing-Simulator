import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

Deno.serve(async (req) => {
  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

    const authHeader = req.headers.get("Authorization") ?? "";
    if (!authHeader.startsWith("Bearer ")) {
      return new Response(JSON.stringify({ ok: false, error: "missing_token" }), { status: 401 });
    }

    // Use caller token ONLY to identify the user
    const supabaseAuth = createClient(supabaseUrl, serviceRoleKey, {
      global: { headers: { Authorization: authHeader } },
    });

    const { data: { user }, error: userErr } = await supabaseAuth.auth.getUser();
    if (userErr || !user) {
      return new Response(JSON.stringify({ ok: false, error: "invalid_token" }), { status: 401 });
    }

    const body = await req.json().catch(() => ({}));
    const achievement_id = String(body.achievement_id ?? "").trim();
    if (!achievement_id) {
      return new Response(JSON.stringify({ ok: false, error: "invalid_achievement" }), { status: 400 });
    }

    // Service-role client for DB (bypasses RLS)
    const supabase = createClient(supabaseUrl, serviceRoleKey);

    // Check existing unlocked_at (so we don't overwrite the original timestamp)
    const { data: existing, error: exErr } = await supabase
      .from("user_achievements")
      .select("unlocked_at")
      .eq("user_id", user.id)
      .eq("achievement_id", achievement_id)
      .maybeSingle();

    if (exErr) {
      return new Response(JSON.stringify({ ok: false, error: "db_read_failed", details: exErr.message }), { status: 500 });
    }

    const payload = {
      user_id: user.id,
      achievement_id,
      unlocked_at: existing?.unlocked_at ?? new Date().toISOString(),
    };

    const { data, error } = await supabase
      .from("user_achievements")
      .upsert(payload, { onConflict: "user_id,achievement_id" })
      .select("user_id, achievement_id, unlocked_at")
      .single();

    if (error) {
      return new Response(JSON.stringify({ ok: false, error: "db_write_failed", details: error.message }), { status: 500 });
    }

    return new Response(JSON.stringify({ ok: true, achievement: data }), {
      headers: { "Content-Type": "application/json" },
      status: 200,
    });

  } catch (e) {
    return new Response(JSON.stringify({ ok: false, error: "exception" }), { status: 500 });
  }
});
