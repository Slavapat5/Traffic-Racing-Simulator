import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY")!;

serve(async (req) => {
  try {
    if (req.method !== "POST") {
      return new Response(JSON.stringify({ ok: false, error: "method_not_allowed" }), {
        status: 405,
        headers: { "Content-Type": "application/json" },
      });
    }

    const authHeader = req.headers.get("Authorization");
    if (!authHeader) {
      return new Response(JSON.stringify({ ok: false, error: "missing_authorization" }), {
        status: 401,
        headers: { "Content-Type": "application/json" },
      });
    }

    // 1) Identify the caller (user JWT)
    const userClient = createClient(SUPABASE_URL, ANON_KEY, {
      global: { headers: { Authorization: authHeader } },
    });

    const { data: userData, error: userErr } = await userClient.auth.getUser();
    if (userErr || !userData?.user) {
      return new Response(JSON.stringify({ ok: false, error: "invalid_jwt" }), {
        status: 401,
        headers: { "Content-Type": "application/json" },
      });
    }

    const userId = userData.user.id;

    // 2) Read request body
    const body = await req.json().catch(() => ({}));
    const delta = Number(body.delta);
    const reason = String(body.reason ?? "unspecified");

    if (!Number.isFinite(delta) || Math.abs(delta) > 1_000_000) {
      return new Response(JSON.stringify({ ok: false, error: "bad_delta" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }
    if (reason.length < 1 || reason.length > 80) {
      return new Response(JSON.stringify({ ok: false, error: "bad_reason" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }

    // 3) Perform atomic update as server (service_role)
    const admin = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);

    const { data, error } = await admin.rpc("adjust_cash_admin", {
      p_user_id: userId,
      p_delta: delta,
      p_reason: reason,
    });

    if (error) {
      const msg = (error.message || "").toLowerCase();
      const isInsufficient =
        msg.includes("insufficient") || msg.includes("funds") || msg.includes("missing_profile");

      return new Response(
        JSON.stringify({ ok: false, error: isInsufficient ? "insufficient_funds" : "rpc_failed", details: error.message }),
        {
          status: isInsufficient ? 400 : 500,
          headers: { "Content-Type": "application/json" },
        },
      );
    }

    // `data` is the bigint new cash value returned from the function
    return new Response(JSON.stringify({ ok: true, cash: data }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });

  } catch (e) {
    return new Response(JSON.stringify({ ok: false, error: "exception", details: String(e?.message ?? e) }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});
