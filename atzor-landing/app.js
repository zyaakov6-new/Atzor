/**
 * עצור landing page - lightweight interactions only.
 * A product page with a Play Store CTA plus two email capture points.
 *
 * To point the download buttons at the app, set PLAY_URL below.
 *   During closed testing, use your opt-in link:
 *     https://play.google.com/apps/testing/com.atzor.app
 *   At public launch, use the store listing:
 *     https://play.google.com/store/apps/details?id=com.atzor.app
 *
 * Analytics: pageviews and Core Web Vitals come from the two Vercel scripts in
 * the HTML. Five named custom events are sent from here via window.va.
 */
(function () {
  "use strict";

  const PLAY_URL = "https://play.google.com/apps/testing/com.atzor.app";

  // ---------------------------------------------------------------------------
  // Public Google Group for the beta testers. Drop the real URL in here once the
  // group exists, for example:
  //   https://groups.google.com/g/atzor-testers
  // While this is empty the beta explainer line under each CTA is removed from
  // the page, since we cannot tell people to join a group that has no address.
  const GROUP_URL = "https://groups.google.com/g/testers-atzor";
  // ---------------------------------------------------------------------------

  const ENDPOINT = "/api/subscribe";
  const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

  const MSG = {
    invalid: "נראה שהמייל לא תקין. בדקו רגע?",
    failed: "משהו השתבש אצלנו. נסו שוב עוד רגע, או כתבו לנו למייל.",
    sending: "רגע...",
    ios_waitlist: "מעולה, רשמנו אתכם. נעדכן ברגע שיש גרסה לאייפון.",
    general_waitlist: "רשמנו. נעדכן אתכם כשיש משהו ששווה לדעת.",
  };

  // Queue for Vercel Analytics custom events (works before/after script loads).
  window.va = window.va || function () {
    (window.vaq = window.vaq || []).push(arguments);
  };

  function track(name, props) {
    try {
      window.va("event", Object.assign({ name: name }, props || {}));
    } catch (_) {
      /* analytics optional - never break the page */
    }
  }

  // Point every Play CTA at the single source-of-truth URL.
  document.querySelectorAll("[data-play-link]").forEach((el) => {
    el.setAttribute("href", PLAY_URL);
  });

  // The five tracked events: cta_play_primary, cta_play_secondary,
  // waitlist_ios_submit, waitlist_general_submit, privacy_click.
  // Form submits are tracked in submit() instead, on success only.
  document.querySelectorAll("a[data-event]").forEach((el) => {
    el.addEventListener("click", () => track(el.getAttribute("data-event")));
  });

  // Beta explainer: linkify the group, or drop the line entirely when we have no
  // URL to send people to. The markup ships visible so it survives JS being off.
  document.querySelectorAll("[data-beta-note]").forEach((note) => {
    if (!GROUP_URL) {
      note.remove();
      return;
    }
    const slot = note.querySelector("[data-group-link]");
    if (!slot) return;
    const link = document.createElement("a");
    link.href = GROUP_URL;
    link.className = "beta-note-link";
    link.rel = "noopener";
    link.textContent = slot.textContent;
    slot.replaceWith(link);
  });

  // Email capture. Progressive: the form has a real action/method, and this only
  // takes over to keep the visitor on the page.
  document.querySelectorAll(".capture-form").forEach((form) => {
    const source = form.getAttribute("data-capture");
    const eventName = form.getAttribute("data-event");
    const input = form.querySelector("input[type=email]");
    const button = form.querySelector("button");
    const status = form.parentElement.querySelector("[data-status]");

    function say(text, state) {
      if (!status) return;
      status.textContent = text;
      status.className = "capture-status" + (state ? " is-" + state : "");
    }

    form.addEventListener("submit", async (event) => {
      event.preventDefault();

      const email = (input.value || "").trim();
      if (!EMAIL_RE.test(email)) {
        say(MSG.invalid, "error");
        input.focus();
        return;
      }

      button.disabled = true;
      say(MSG.sending, "pending");

      try {
        const res = await fetch(ENDPOINT, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            email: email,
            source: source,
            referrer: document.referrer || null,
          }),
        });
        const data = await res.json().catch(() => ({}));

        if (res.ok && data.ok) {
          // Success state replaces the form so there is nothing left to re-submit.
          form.hidden = true;
          say(MSG[source], "ok");
          // duplicate flag keeps a returning visitor from inflating demand numbers.
          track(eventName, { duplicate: Boolean(data.duplicate) });
          return;
        }

        say(data.error === "invalid_email" ? MSG.invalid : MSG.failed, "error");
      } catch (_) {
        say(MSG.failed, "error");
      } finally {
        button.disabled = false;
      }
    });
  });

  // Header gets a hairline border once you scroll.
  const header = document.querySelector(".site-header");
  const onScroll = () => header && header.classList.toggle("scrolled", window.scrollY > 8);
  onScroll();
  window.addEventListener("scroll", onScroll, { passive: true });
})();
