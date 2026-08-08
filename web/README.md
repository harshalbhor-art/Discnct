# Discnct — web presence

Two independent, statically-deployable pieces, neither part of the Android Gradle
build:

- **`landing/`** — the marketing site. One static `index.html`, no build step: the app,
  the three levels, the stats screens, the APK download, and a scan-to-pay support
  section at the bottom. `qr/` holds the pre-rendered UPI codes and the script that
  makes them.
- **`coffee/`** — an unused Stripe Checkout flow. **The landing page no longer points
  at it.** Support payments now go by UPI, handled entirely in `landing/index.html`
  with no server involved, matching what the Android app does. This directory is kept
  in case card payments are wanted later — nothing links to it, and it doesn't need
  deploying.

Only `landing/` needs a Vercel project now.

## Screens on the landing page

The phone screens in `landing/index.html` are **rebuilt in HTML and CSS from the real
app**, not photographs of it. That keeps them sharp at any size, lets them follow the
page's own light/dark toggle, and costs nothing to download. It also means they can
drift: when a screen changes in the app, the markup here has to be updated by hand.
If you'd rather ship real PNG screenshots, drop them in `landing/screenshots/` and
swap each `.phone` block for an `<img>`.

## Deploying `landing/`

**It is deployed:** <https://discnct.vercel.app> (Vercel project `discnct`, team
"Harshal's art", production).

That first deploy was a direct file upload, which means **the project is not connected to
GitHub yet** — pushing to this repo will not redeploy it. Connecting it is a one-time job
in the dashboard and worth doing:

1. Vercel → project `discnct` → Settings → Git → **Connect Git Repository** →
   `harshalbhor-art/Discnct`.
2. Set **Root Directory** to `web/landing`.
3. Framework preset: **Other** (it's static — no build command, no output directory
   override needed). No environment variables.

After that, every push to the production branch redeploys the site on its own, and the
URL above stays the same. Until then, redeploying means re-uploading `web/landing/`.

Before or after deploying, edit the `<meta>` tag at the top of
`web/landing/index.html`:

```html
<meta name="discnct-apk-url" content="..." />
```

- `discnct-apk-url` should point at a GitHub Release asset, e.g.
  `https://github.com/harshalbhor-art/discnct/releases/latest/download/app-debug.apk`
  — which means a Release with an attached `app-debug.apk` (or a signed release
  build, once that exists) needs to be published first. The `android-build.yml`
  workflow currently only uploads a CI artifact, which requires a GitHub login to
  download — not suitable for a public download button. Publishing a proper Release
  with the APK attached is a separate, small follow-up.
## Changing the UPI payee

The VPA lives in exactly two places on the web side, and both have to move together:

1. `web/landing/qr/generate.py` — the `UPI_ID` constant.
2. `web/landing/index.html` — the `UPI_ID` constant in the script at the bottom, used
   only to build the `upi://` link behind "Open a UPI app instead".

Then regenerate the codes:

```bash
pip install qrcode
python3 web/landing/qr/generate.py
```

**The ID is never rendered.** There is no copy button and no line of text showing the
VPA — payment goes through the QR, or through the `upi://` button on a phone. It isn't
secret either way (it's inside the QR, and a VPA is an address for *receiving* money);
this just keeps the page free of the one string the Android app is careful never to
put on screen.

The URI the QRs encode must stay byte-identical to what the app builds in
`SupportLinks.kt` — two decimals on the amount, `@` unencoded, `%20` not `+` in the
note. `generate.py` repeats those rules and says why.

## Deploying `coffee/` (optional, currently unused)

1. New Vercel project → import this repo.
2. Set **Root Directory** to `web/coffee`.
3. Framework preset: **Next.js** (auto-detected).
4. Add environment variables (Project Settings → Environment Variables):

   | Name | Value | Notes |
   |---|---|---|
   | `STRIPE_SECRET_KEY` | `sk_test_...` | From the [Stripe dashboard](https://dashboard.stripe.com/apikeys). Start in **test mode** — test-mode keys and live-mode keys are both valid `sk_...` values, Stripe just routes them differently. Switch to a live key only when ready to accept real payments. |
   | `NEXT_PUBLIC_LANDING_URL` | e.g. `https://discnct.vercel.app` | Optional. Where the "back to Discnct" link on the success/cancel pages points. Defaults to the GitHub repo if unset. |

5. Deploy.
6. Point something at it. Nothing does today: the landing page pays by UPI, and
   `SupportCard.kt` in the Android app fires a `upi://` intent rather than opening a
   web checkout.

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
- No custom domain wiring (`discnct.app` or similar) — the APK `<meta>` above assumes
  you'll either use the Vercel-provided `*.vercel.app` domain or attach your own in
  the Vercel dashboard.
- **No payment verification anywhere, by design.** Neither the landing page nor the
  app can tell whether a UPI payment completed; there is no server to ask. Nothing is
  unlocked by paying, so nothing needs to know.
- **The QR codes have never been scanned by an actual phone.** They are generated by the
  `qrcode` package and their structure was checked against the library's own matrix
  (finder patterns, quiet zone, module-for-module match), but no camera has read one
  here. Scan each amount once with a real UPI app before relying on them.
- `web-prototype/` is unrelated to both of these — it's a standalone UI/logic demo
  of the Android app itself, not part of the web presence.
