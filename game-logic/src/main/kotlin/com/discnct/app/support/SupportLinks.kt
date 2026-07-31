package com.discnct.app.support

/**
 * The UPI deep link behind "Buy us a Coffee".
 *
 * This is a string builder and nothing else. There is no backend, no order id, no receipt and no
 * callback: Discnct hands a payment app a payee and an amount, and that is the end of its
 * involvement. Nothing that happens afterwards unlocks anything, and the app never learns whether
 * money moved — which is why there is no state to keep and nothing here to persist.
 *
 * It lives in `game-logic` rather than `app` because this module is plain JVM Kotlin and its tests
 * actually run in CI on every commit. A malformed `upi://` URI fails silently on a real phone — the
 * chooser simply comes up empty, or the payment app opens with a blank amount — so the format is
 * worth pinning down somewhere it can be asserted rather than eyeballed.
 */

/**
 * The payee. **Placeholder** — replace with a real UPI ID before shipping, or every payment fails
 * at the payment app with "invalid VPA".
 */
const val UPI_ID = "yourname@upi"

/** Shown by the payment app as who is being paid. */
const val UPI_PAYEE_NAME = "Discnct"

/** The note on the transaction, visible in the payer's own statement. */
const val UPI_NOTE = "Discnct Support"

/** UPI is a domestic Indian rail; INR is the only currency it settles in. */
const val UPI_CURRENCY = "INR"

/**
 * Rupees as UPI wants them: always two decimal places.
 *
 * A bare integer is accepted by some payment apps and quietly rejected by others, which is the worst
 * kind of bug to have here — it would work on whatever phone it was tested on.
 */
fun upiAmount(rupees: Int): String {
    require(rupees > 0) { "a UPI amount has to be positive, got $rupees" }
    return "$rupees.00"
}

/**
 * Build the `upi://pay` URI for [rupees].
 *
 * Parameter order is fixed rather than incidental: some payment apps parse these positionally
 * despite the spec, and a stable order is free to guarantee.
 */
fun upiPaymentUri(
    rupees: Int,
    upiId: String = UPI_ID,
    payeeName: String = UPI_PAYEE_NAME,
    note: String = UPI_NOTE,
): String = buildString {
    append("upi://pay")
    append("?pa=").append(upiEncode(upiId))
    append("&pn=").append(upiEncode(payeeName))
    append("&am=").append(upiAmount(rupees))
    append("&cu=").append(UPI_CURRENCY)
    append("&tn=").append(upiEncode(note))
}

/**
 * Percent-encode a query value.
 *
 * Deliberately not `URLEncoder`, which encodes a space as `+`. That is correct for form bodies and
 * wrong for a URI query of this kind — a payee name would arrive at the payment app with plus signs
 * in it. `@` is left alone because it is legal in a query and every UPI ID contains one; encoding it
 * to `%40` works in most apps and breaks a few.
 */
private fun upiEncode(value: String): String = buildString {
    for (byte in value.toByteArray(Charsets.UTF_8)) {
        val char = byte.toInt().toChar()
        if (char.isSafeInUpiQuery()) {
            append(char)
        } else {
            append('%').append("%02X".format(byte.toInt() and 0xFF))
        }
    }
}

private fun Char.isSafeInUpiQuery(): Boolean =
    this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this in "-._~@"
