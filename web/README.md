# Discnct — web presence

Two independent, statically-deployable pieces, neither part of the Android Gradle
build:

- **`landing/`** — the marketing site. A single static `index.html`, no build step.
  Explains the app, walks through the three levels, and links out to the APK
  download and the coffee checkout.
- **`coffee/`** — the "Buy us a Coffee" payment flow. A small Next.js app with one
  page and one API route that creates a [Stripe Checkout](https://stripe.com/docs/payments/checkout)
  session server-side. Card details are entered on Stripe's own hosted page — this
  app never sees or stores them.

Both are meant to be deployed as **separate Vercel projects**, each pointed at its
own subdirectory.

## Deploying `landing/`

1. New Vercel project → import this repo.
2. Set **Root Directory** to `web/landing`.
3. Framework preset: **Other** (it's static — no build command, no output directory
   override needed).
4. Deploy. That's it — no environment variables.

Before or after deploying, edit the two `<meta>` tags at the top of
`web/landing/index.html`:

```html
<meta name="discnct-apk-url" content="..." />
<meta name="discnct-coffee-url" content="..." />
```

- `discnct-apk-url` should point at a GitHub Release asset, e.g.
  `https://github.com/harshalbhor-art/discnct/releases/latest/download/app-debug.apk`
  — which means a Release with an attached `app-debug.apk` (or a signed release
  build, once that exists) needs to be published first. The `android-build.yml`
  workflow currently only uploads a CI artifact, which requires a GitHub login to
  download — not suitable for a public download button. Publishing a proper Release
  with the APK attached is a separate, small follow-up.
- `discnct-coffee-url` should be the `coffee/` project's real Vercel URL (or custom
  domain) once deployed.

## Deploying `coffee/`

1. New Vercel project → import this repo.
2. Set **Root Directory** to `web/coffee`.
3. Framework preset: **Next.js** (auto-detected).
4. Add environment variables (Project Settings → Environment Variables):

   | Name | Value | Notes |
   |---|---|---|
   | `STRIPE_SECRET_KEY` | `sk_test_...` | From the [Stripe dashboard](https://dashboard.stripe.com/apikeys). Start in **test mode** — test-mode keys and live-mode keys are both valid `sk_...` values, Stripe just routes them differently. Switch to a live key only when ready to accept real payments. |
   | `NEXT_PUBLIC_LANDING_URL` | e.g. `https://discnct.vercel.app` | Optional. Where the "back to Discnct" link on the success/cancel pages points. Defaults to the GitHub repo if unset. |

5. Deploy.
6. Update `discnct-coffee-url` in `web/landing/index.html` (see above) to this
   project's URL, and update `COFFEE_CHECKOUT_URL` in
   `app/src/main/kotlin/com/discnct/app/ui/home/SupportCard.kt` to match, then
   rebuild the app.

### Testing a payment end-to-end

In test mode, Stripe's card `4242 4242 4242 4242`, any future expiry, any CVC, any
ZIP completes a successful test payment. No real money moves until the secret key
is swapped for a live one.

### Local development

```bash
cd web/coffee
npm install
cp .env.example .env.local   # fill in STRIPE_SECRET_KEY
npm run dev
```

## What's intentionally out of scope here

- No webhook / payment-confirmation record-keeping. Stripe's own dashboard is the
  ledger; this is a checkout flow, not an accounting system. Worth adding later if
  you want an automated "thank you" email or a running total.
- No custom domain wiring (`discnct.app` or similar) — both `<meta>`/`SupportCard.kt`
  URLs above assume you'll either use the Vercel-provided `*.vercel.app` domains or
  attach your own domain in the Vercel dashboard and update those two spots to match.
- `web-prototype/` is unrelated to both of these — it's a standalone UI/logic demo
  of the Android app itself, not part of the web presence.
