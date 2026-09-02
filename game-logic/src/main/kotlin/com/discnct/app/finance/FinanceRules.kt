package com.discnct.app.finance

/**
 * Which apps Discnct steps aside for entirely.
 *
 * The reasoning is that being locked out of your bank is a worse outcome than a minute of
 * unenforced scrolling. A block screen thrown over a payment halfway through, or a reel bounce that
 * fires while you're reading a one-time passcode, is the kind of failure that makes someone
 * uninstall the app — and rightly.
 *
 * Two things about the shape of this:
 *
 *  * **It is a list, because Android has no category to ask.** `ApplicationInfo.category` knows
 *    about games, video, news and maps; there is no finance value, and there is no permission or
 *    manifest flag that marks a banking app either. Guessing from the package name would be worse
 *    than useless: `*pay*` catches Google Play, and `*bank*` catches nothing at all for half the
 *    banks here. So the seed below is written out, and [FinancialAppsScreen] is how a user adds the
 *    one their own bank ships.
 *  * **The seed can be overruled in both directions.** [financialPackages] takes what the user has
 *    added and what they have taken away, so a bank we've never heard of can be exempted and one of
 *    ours can be un-exempted, while updates to this list still reach people who never touched it.
 *
 * This is deliberately a hole in enforcement, and it is worth naming: anyone who works out that a
 * banking app suspends Discnct has a two-tap bypass. That is the trade being made.
 */

/**
 * Banking, payments, brokerage and the authenticator apps that stand in front of them.
 *
 * Only packages worth being confident about are here. A wrong entry exempts an app that shouldn't
 * be, silently, so anything uncertain is left out and left to the user to add.
 */
val FINANCIAL_PACKAGE_SEED: Set<String> = setOf(
    // UPI and wallets
    "com.google.android.apps.nbu.paisa.user",
    "com.phonepe.app",
    "net.one97.paytm",
    "in.org.npci.upiapp",
    "com.dreamplug.androidapp",
    "com.mobikwik_new",
    "com.freecharge.android",

    // Indian banks
    "com.csam.icici.bank.imobile",
    "com.snapwork.hdfc",
    "com.sbi.lotusintouch",
    "com.sbi.SBIFreedomPlus",
    "com.axis.mobile",
    "com.msf.kbank.mobile",
    "com.bankofbaroda.mconnect",

    // International banks and payments
    "com.paypal.android.p2pmobile",
    "com.squareup.cash",
    "com.venmo",
    "com.chase.sig.android",
    "com.infonow.bofa",
    "com.wf.wellsfargomobile",
    "com.citi.citimobile",
    "com.revolut.revolut",
    "com.transferwise.android",
    "com.starlingbank.android",
    "com.barclays.android.barclaysmobilebanking",
    "uk.co.hsbc.hsbcukmobilebanking",

    // Brokerage and crypto
    "com.zerodha.kite3",
    "com.robinhood.android",
    "com.coinbase.android",
    "com.binance.dev",

    // Authenticators, because a passcode you can't reach is a bank you can't reach
    "com.google.android.apps.authenticator2",
    "com.azure.authenticator",
    "com.authy.authy",
)

/**
 * The effective exemption list: the seed, plus what this user added, minus what they took away.
 *
 * Removals are applied last on purpose. A user who has explicitly un-exempted their bank means it,
 * even if a later version of Discnct ships that same package in the seed.
 */
fun financialPackages(
    added: Set<String> = emptySet(),
    removed: Set<String> = emptySet(),
): Set<String> = (FINANCIAL_PACKAGE_SEED + added) - removed

/** True while [packageName] is one Discnct stands down for. */
fun isFinancialPackage(
    packageName: String,
    added: Set<String> = emptySet(),
    removed: Set<String> = emptySet(),
): Boolean = packageName in financialPackages(added, removed)
