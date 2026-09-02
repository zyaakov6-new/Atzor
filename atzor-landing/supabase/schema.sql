-- Run this once in the Supabase SQL editor (or via CLI).
-- Already applied to the `atzor` project as migration `create_subscribers_table`.

create table if not exists public.subscribers (
  id uuid primary key default gen_random_uuid(),
  email text not null,
  source text not null,
  referrer text,
  created_at timestamptz not null default now(),
  -- Unique on the pair, not on email alone: one person can legitimately be on
  -- both the iOS list and the general list.
  constraint subscribers_email_source_unique unique (email, source),
  constraint subscribers_source_check check (source in ('ios_waitlist', 'general_waitlist'))
);

create index if not exists subscribers_created_at_idx on public.subscribers (created_at desc);
create index if not exists subscribers_source_idx on public.subscribers (source);

alter table public.subscribers enable row level security;

-- No public policies: only the service role (the Vercel function) can read or write.
-- api/subscribe.js uses SUPABASE_SERVICE_ROLE_KEY, which bypasses RLS.

comment on table public.subscribers is 'Landing page email captures. source: ios_waitlist | general_waitlist';
comment on column public.subscribers.source is 'Which capture point the email came from.';
