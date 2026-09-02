# עצור - Landing Page

Hebrew landing page for **עצור**. Static HTML, no build step, one Vercel function.

## Deploy (Vercel + Supabase)

### 1. Supabase

1. Create a project at [supabase.com](https://supabase.com)
2. Open **SQL Editor** and run `supabase/schema.sql` (already applied to the `atzor` project)
3. Copy **Project URL** and **service_role** key (Settings → API)

### 2. Vercel

```bash
cd /Users/ziv/Projects/atzor-landing
npx vercel
```

In **Project Settings → Environment Variables** add:

| Name | Value |
|------|--------|
| `SUPABASE_URL` | `https://xxxx.supabase.co` |
| `SUPABASE_SERVICE_ROLE_KEY` | service_role key (server only) |

Redeploy after adding env vars.

### 3. Smoke test

1. Open the Vercel URL
2. Submit a test email in both forms
3. In Supabase → **Table Editor → subscribers**, confirm two rows with different `source` values

## Local static preview (no API)

```bash
python3 -m http.server 8080
```

`npx serve` cannot run the API. Form POST to `/api/subscribe` needs Vercel (or `vercel dev` with env vars):

```bash
cp .env.example .env.local   # fill keys
npx vercel dev
```

## What’s included

| Piece | Role |
|-------|------|
| `index.html` / `styles.css` / `app.js` | Landing page |
| `privacy.html` | Short privacy note |
| `api/subscribe.js` | Vercel function → Supabase insert |
| `supabase/schema.sql` | `subscribers` table + RLS |

Two email capture points write to the same `subscribers` table, told apart by `source`:

| `source` | Where |
|----------|-------|
| `ios_waitlist` | "no Android?" block in the download section |
| `general_waitlist` | soft capture band below it |

Unique constraint is on `(email, source)`, so one person can be on both lists. A repeat
submit of the same pair returns success, not an error.

## Beta tester group

`GROUP_URL` at the top of `app.js` is empty until the public Google Group exists. While it
is empty the beta explainer line under each Play CTA is removed from the page. Set it and
the line renders, with the group name linked.

## Tracking

Vercel Web Analytics (pageviews) and Speed Insights (Core Web Vitals) load from script tags
in the HTML. There is no `package.json` and no build step: on a static site the script tags
are the correct install, and the npm packages would need a bundler to do anything.

Five custom events, sent from `app.js`:

| Event | Fires on |
|-------|----------|
| `cta_play_primary` | Play button in the download section |
| `cta_play_secondary` | Play button in the hero |
| `waitlist_ios_submit` | successful iOS capture submit (`duplicate` flag) |
| `waitlist_general_submit` | successful general capture submit (`duplicate` flag) |
| `privacy_click` | either link to the privacy policy |

Submit events fire on success only, so validation failures do not inflate the numbers.
No Google Analytics, no Meta Pixel, no third-party tracker.
