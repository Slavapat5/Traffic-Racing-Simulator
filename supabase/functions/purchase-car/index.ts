// supabase/functions/purchase-car/index.ts
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

Deno.serve(async (req) => {
  try {
    if (req.method !== "POST") {
      return new Response(JSON.stringify({ ok: false, error: "method_not_allowed" }), {
        status: 405,
        headers: { "Content-Type": "application/json" },
      });
    }

    const authHeader = req.headers.get("Authorization") ?? "";
    const jwt = authHeader.startsWith("Bearer ") ? authHeader.slice(7) : null;
    if (!jwt) {
      return new Response(JSON.stringify({ ok: false, error: "missing_jwt" }), {
        status: 401,
        headers: { "Content-Type": "application/json" },
      });
    }

    const { car_image, price } = await req.json();

    if (typeof car_image !== "string" || car_image.trim().length === 0) {
      return new Response(JSON.stringify({ ok: false, error: "invalid_car_image" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }

    if (typeof price !== "number" || !Number.isFinite(price) || price <= 0 || price > 5_000_000) {
      return new Response(JSON.stringify({ ok: false, error: "invalid_price" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }

    const url = Deno.env.get("SUPABASE_URL")!;
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const supabase = createClient(url, serviceKey, { auth: { persistSession: false } });

    // Identify user from the JWT, not trusting client user_id)
    const { data: userData, error: userErr } = await supabase.auth.getUser(jwt);
    if (userErr || !userData?.user) {
      return new Response(JSON.stringify({ ok: false, error: "invalid_jwt" }), {
        status: 401,
        headers: { "Content-Type": "application/json" },
      });
    }
    const userId = userData.user.id;

    // Atomic purchase using a single SQL function call
    const { data, error } = await supabase.rpc("purchase_car", {
      p_user_id: userId,
      p_car_image: car_image,
      p_price: Math.trunc(price),
    });

    if (error) {
      // e.g. insufficient_funds, already_owned, etc.
      return new Response(JSON.stringify({ ok: false, error: error.message }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }

    return new Response(JSON.stringify({ ok: true, ...data }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  } catch (e) {
    return new Response(JSON.stringify({ ok: false, error: "exception", details: String(e) }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});
