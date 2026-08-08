import Stripe from 'stripe';

const MIN_AMOUNT_INR = 10;
const MAX_AMOUNT_INR = 50_000;

export default async function handler(req, res) {
  if (req.method !== 'POST') {
    res.setHeader('Allow', 'POST');
    return res.status(405).json({ error: 'Method not allowed' });
  }

  if (!process.env.STRIPE_SECRET_KEY) {
    return res.status(500).json({ error: 'Payments are not configured yet.' });
  }

  const amount = Number(req.body?.amount);
  if (!Number.isFinite(amount) || amount < MIN_AMOUNT_INR || amount > MAX_AMOUNT_INR) {
    return res.status(400).json({ error: `Enter an amount between ₹${MIN_AMOUNT_INR} and ₹${MAX_AMOUNT_INR}.` });
  }

  const stripe = new Stripe(process.env.STRIPE_SECRET_KEY);
  const origin = req.headers.origin || `https://${req.headers.host}`;

  try {
    const session = await stripe.checkout.sessions.create({
      mode: 'payment',
      payment_method_types: ['card'],
      line_items: [
        {
          price_data: {
            currency: 'inr',
            unit_amount: Math.round(amount * 100),
            product_data: {
              name: 'A coffee for Discnct',
              description: 'One-time support for the Discnct project.',
            },
          },
          quantity: 1,
        },
      ],
      success_url: `${origin}/success`,
      cancel_url: `${origin}/cancel`,
    });

    return res.status(200).json({ url: session.url });
  } catch (err) {
    console.error('Stripe checkout session creation failed', err);
    return res.status(500).json({ error: 'Could not start checkout. Please try again.' });
  }
}
