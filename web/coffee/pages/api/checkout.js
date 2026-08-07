import Razorpay from 'razorpay';

const MIN_AMOUNT_INR = 10;
const MAX_AMOUNT_INR = 50_000;

export default async function handler(req, res) {
  if (req.method !== 'POST') {
    res.setHeader('Allow', 'POST');
    return res.status(405).json({ error: 'Method not allowed' });
  }

  if (!process.env.RAZORPAY_KEY_ID || !process.env.RAZORPAY_KEY_SECRET) {
    return res.status(500).json({ error: 'Payments are not configured yet.' });
  }

  const amount = Number(req.body?.amount);
  if (!Number.isFinite(amount) || amount < MIN_AMOUNT_INR || amount > MAX_AMOUNT_INR) {
    return res.status(400).json({ error: `Enter an amount between ₹${MIN_AMOUNT_INR} and ₹${MAX_AMOUNT_INR}.` });
  }

  const razorpay = new Razorpay({
    key_id: process.env.RAZORPAY_KEY_ID,
    key_secret: process.env.RAZORPAY_KEY_SECRET,
  });
  const origin = req.headers.origin || `https://${req.headers.host}`;

  try {
    // Payment Links are the closest match to Stripe Checkout's hosted-page-plus-redirect shape:
    // create it server-side, send the browser to short_url, land back on callback_url after.
    // Razorpay only supports a success callback, not a separate cancel redirect — closing the
    // page or backing out just leaves the link open to retry, which /cancel below still covers
    // for the "Try again" case reached from a checkout error.
    const link = await razorpay.paymentLink.create({
      amount: Math.round(amount * 100),
      currency: 'INR',
      description: 'A coffee for Discnct',
      callback_url: `${origin}/success`,
      callback_method: 'get',
    });

    return res.status(200).json({ url: link.short_url });
  } catch (err) {
    console.error('Razorpay payment link creation failed', err);
    return res.status(500).json({ error: 'Could not start checkout. Please try again.' });
  }
}
