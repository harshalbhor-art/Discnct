import Head from 'next/head';

const LANDING_URL = process.env.NEXT_PUBLIC_LANDING_URL || 'https://github.com/harshalbhor-art/discnct';

export default function Success() {
  return (
    <>
      <Head>
        <title>Thank you — Discnct</title>
      </Head>
      <main className="wrap">
        <div className="card">
          <div className="brand"><span className="dot" />DISCNCT</div>
          <div className="result-icon">☕</div>
          <h1>Thank you</h1>
          <p className="sub">
            Your coffee is on its way to keeping Discnct free, ad-free, and independent.
          </p>
          <a className="back" href={LANDING_URL}>← Back to Discnct</a>
        </div>
      </main>
    </>
  );
}
