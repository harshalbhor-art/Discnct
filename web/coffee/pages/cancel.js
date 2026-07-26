import Head from 'next/head';
import Link from 'next/link';

const LANDING_URL = process.env.NEXT_PUBLIC_LANDING_URL || 'https://github.com/harshalbhor-art/discnct';

export default function Cancel() {
  return (
    <>
      <Head>
        <title>Checkout cancelled — Discnct</title>
      </Head>
      <main className="wrap">
        <div className="card">
          <div className="brand"><span className="dot" />DISCNCT</div>
          <div className="result-icon">·</div>
          <h1>No charge made</h1>
          <p className="sub">
            The checkout was cancelled — nothing was charged. You can try again any time.
          </p>
          <Link className="back" href="/">← Try again</Link>
          {' '}·{' '}
          <a className="back" href={LANDING_URL}>Back to Discnct</a>
        </div>
      </main>
    </>
  );
}
