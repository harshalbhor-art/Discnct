import { useEffect, useState } from 'react';
import { useRouter } from 'next/router';
import Head from 'next/head';

const PRESETS = [49, 99, 199, 499];

export default function Home() {
  const router = useRouter();
  const [amount, setAmount] = useState(PRESETS[1]);
  const [custom, setCustom] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // Lets the app (or the landing page) deep-link in with an amount already chosen,
  // e.g. /?amount=199, matching whatever preset the user tapped there.
  useEffect(() => {
    if (!router.isReady) return;
    const fromQuery = Number(router.query.amount);
    if (Number.isFinite(fromQuery) && fromQuery > 0) {
      if (PRESETS.includes(fromQuery)) {
        setAmount(fromQuery);
      } else {
        setCustom(String(fromQuery));
      }
    }
  }, [router.isReady, router.query.amount]);

  const effectiveAmount = custom !== '' ? Number(custom) : amount;

  async function checkout() {
    setError(null);
    if (!Number.isFinite(effectiveAmount) || effectiveAmount < 10) {
      setError('Enter an amount of at least ₹10.');
      return;
    }
    setLoading(true);
    try {
      const res = await fetch('/api/checkout', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ amount: effectiveAmount }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Something went wrong.');
      window.location.href = data.url;
    } catch (err) {
      setError(err.message);
      setLoading(false);
    }
  }

  return (
    <>
      <Head>
        <title>Buy us a coffee — Discnct</title>
        <meta name="description" content="Support Discnct, a free Android app that helps you scroll less." />
      </Head>
      <main className="wrap">
        <div className="card">
          <div className="brand"><span className="dot" />DISCNCT</div>
          <h1>Buy us a coffee</h1>
          <p className="sub">
            Discnct is free, ad-free, and doesn&apos;t sell your data. If it&apos;s helped you
            scroll less, a coffee goes straight to keeping it that way.
          </p>

          <div className="amounts">
            {PRESETS.map((p) => (
              <button
                key={p}
                type="button"
                className={amount === p && custom === '' ? 'amount active' : 'amount'}
                onClick={() => { setAmount(p); setCustom(''); }}
              >
                ₹{p}
              </button>
            ))}
            <input
              className="custom"
              type="number"
              min="10"
              max="50000"
              placeholder="Custom"
              value={custom}
              onChange={(e) => setCustom(e.target.value)}
            />
          </div>

          {error && <p className="error">{error}</p>}

          <button type="button" className="pay" onClick={checkout} disabled={loading}>
            {loading ? 'Redirecting…' : `Pay ₹${effectiveAmount || 0} via Razorpay`}
          </button>

          <p className="fine">
            Payments are handled entirely by Razorpay — we never see or store your card details.
          </p>
        </div>
      </main>
    </>
  );
}
