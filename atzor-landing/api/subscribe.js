/**
 * Vercel Serverless Function
 * POST /api/subscribe  { email, source, referrer? }
 *
 *   source: "ios_waitlist"     - the "no Android?" capture in the download section
 *           "general_waitlist" - the softer capture below it
 *
 * Env (Vercel project settings):
 *   SUPABASE_URL
 *   SUPABASE_SERVICE_ROLE_KEY
 *
 * created_at comes from the column default, so the timestamp is the server's.
 */

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// Allow-list. The client says which form it submitted, so it does not get to
// invent a source value.
const SOURCES = ["ios_waitlist", "general_waitlist"];

// Kept in sync with MSG in app.js, so the no-JS page says the same thing.
function okMessage(source) {
  return source === "ios_waitlist"
    ? "מעולה, רשמנו אתכם. נעדכן ברגע שיש גרסה לאייפון."
    : "רשמנו. נעדכן אתכם כשיש משהו ששווה לדעת.";
}

function json(res, status, body) {
  res.statusCode = status;
  res.setHeader("Content-Type", "application/json; charset=utf-8");
  res.end(JSON.stringify(body));
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let raw = "";
    req.on("data", (chunk) => {
      raw += chunk;
      if (raw.length > 1e5) {
        reject(new Error("body_too_large"));
        req.destroy();
      }
    });
    req.on("end", () => {
      if (!raw) return resolve({});
      // With JS disabled the browser posts the form natively, as urlencoded.
      const type = req.headers["content-type"] || "";
      if (type.includes("application/x-www-form-urlencoded")) {
        return resolve(Object.fromEntries(new URLSearchParams(raw)));
      }
      try {
        resolve(JSON.parse(raw));
      } catch {
        reject(new Error("invalid_json"));
      }
    });
    req.on("error", reject);
  });
}

// A native form post lands the browser on this endpoint, so it needs a page to
// look at rather than raw JSON. Matches the site's palette and stays in Hebrew.
function htmlPage(res, status, title, message) {
  res.statusCode = status;
  res.setHeader("Content-Type", "text/html; charset=utf-8");
  res.end(`<!DOCTYPE html>
<html lang="he" dir="rtl">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<title>${title} | עצור</title>
<style>
  body { margin:0; min-height:100vh; display:flex; align-items:center; justify-content:center;
         background:#f5ead8; color:#201e1d; font-family:Assistant, Arial, sans-serif;
         line-height:1.65; text-align:center; padding:1.5rem; }
  .box { max-width:26rem; }
  h1 { font-family:Georgia, serif; font-size:1.8rem; margin:0 0 .6rem; }
  p { color:#82796a; margin:0 0 1.6rem; }
  a { display:inline-block; background:#c67139; color:#fffbf2; font-weight:700;
      padding:.8rem 1.6rem; border-radius:999px; text-decoration:none; }
</style>
</head>
<body><div class="box"><h1>${title}</h1><p>${message}</p><a href="/">חזרה לדף הבית</a></div></body>
</html>`);
}

module.exports = async function handler(req, res) {
  // Same-origin only. This endpoint is called from our own page, nowhere else,
  // so no Access-Control-Allow-Origin is sent.
  res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");

  if (req.method === "OPTIONS") {
    res.statusCode = 204;
    return res.end();
  }

  if (req.method !== "POST") {
    return json(res, 405, { error: "method_not_allowed" });
  }

  // No-JS visitors post the form natively and land here, so they get HTML back.
  const isFormPost = (req.headers["content-type"] || "").includes(
    "application/x-www-form-urlencoded"
  );
  const fail = (status, error, title, message) =>
    isFormPost ? htmlPage(res, status, title, message) : json(res, status, { error: error });

  const url = process.env.SUPABASE_URL;
  const key = process.env.SUPABASE_SERVICE_ROLE_KEY;

  if (!url || !key) {
    console.error("Missing SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY");
    return fail(500, "server_misconfigured", "משהו השתבש אצלנו", "נסו שוב עוד רגע, או כתבו לנו ל-zyaakov6@gmail.com.");
  }

  let body;
  try {
    body = await readBody(req);
  } catch {
    return fail(400, "invalid_body", "משהו השתבש", "לא הצלחנו לקרוא את הטופס. נסו שוב עוד רגע.");
  }

  const email = typeof body.email === "string" ? body.email.trim().toLowerCase() : "";
  if (!EMAIL_RE.test(email) || email.length > 254) {
    return fail(400, "invalid_email", "המייל לא תקין", "נראה שכתובת המייל לא תקינה. בדקו רגע ונסו שוב.");
  }

  const source = typeof body.source === "string" ? body.source : "";
  if (!SOURCES.includes(source)) {
    return fail(400, "invalid_source", "משהו השתבש", "לא זיהינו מאיזה טופס הגעתם. נסו שוב מדף הבית.");
  }

  const row = {
    email,
    source,
    referrer: typeof body.referrer === "string" ? body.referrer.slice(0, 500) : null,
  };

  try {
    const response = await fetch(`${url.replace(/\/$/, "")}/rest/v1/subscribers`, {
      method: "POST",
      headers: {
        apikey: key,
        Authorization: `Bearer ${key}`,
        "Content-Type": "application/json",
        Prefer: "return=minimal",
      },
      body: JSON.stringify(row),
    });

    if (response.status === 201 || response.status === 200) {
      return isFormPost
        ? htmlPage(res, 200, "נרשמתם", okMessage(source))
        : json(res, 200, { ok: true, duplicate: false });
    }

    // Already on this list. A returning visitor should see success, not an error.
    if (response.status === 409) {
      return isFormPost
        ? htmlPage(res, 200, "נרשמתם", okMessage(source))
        : json(res, 200, { ok: true, duplicate: true });
    }

    const errText = await response.text();
    // PostgREST sometimes reports the unique violation as 23505 in the body.
    if (errText.includes("23505") || errText.includes("duplicate") || errText.includes("unique")) {
      return isFormPost
        ? htmlPage(res, 200, "נרשמתם", okMessage(source))
        : json(res, 200, { ok: true, duplicate: true });
    }

    console.error("Supabase insert failed", response.status, errText);
    return fail(502, "upstream_error", "משהו השתבש אצלנו", "נסו שוב עוד רגע, או כתבו לנו ל-zyaakov6@gmail.com.");
  } catch (err) {
    console.error("Subscribe handler error", err);
    return fail(500, "server_error", "משהו השתבש אצלנו", "נסו שוב עוד רגע, או כתבו לנו ל-zyaakov6@gmail.com.");
  }
};
