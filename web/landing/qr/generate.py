#!/usr/bin/env python3
"""
Regenerate the UPI payment QR codes next to this script.

Run this whenever the payee changes — the QRs are pre-rendered rather than built in the
browser, so editing the VPA anywhere else leaves these five files pointing at the old one.

    pip install qrcode
    python3 web/landing/qr/generate.py

Pre-rendering is the point: no QR library ships to visitors, switching amounts is just
swapping an <img>, and the codes can be checked once here rather than trusted to run
correctly on someone else's phone.

The URI must stay byte-identical to what the Android app builds in
`game-logic/src/main/kotlin/com/discnct/app/support/SupportLinks.kt` — two decimal places on
the amount, '@' left unencoded, and %20 rather than '+' in the note. A QR that encodes a
subtly different URI than the app's button is the kind of bug nobody notices until a payment
fails at someone else's bank.
"""

import pathlib
from urllib.parse import quote

import qrcode

UPI_ID = "harsh.bhor007@okhdfcbank"
PAYEE_NAME = "Discnct"
NOTE = "Discnct Support"

# The four presets the support section offers, plus an open-amount code the payer fills in.
AMOUNTS: list[int | None] = [49, 99, 199, 499, None]

HERE = pathlib.Path(__file__).parent


def encode(value: str) -> str:
    """Percent-encode, but leave '@' alone — it is legal in a query and every VPA has one."""
    return quote(value, safe="-._~@")


def payment_uri(rupees: int | None) -> str:
    uri = f"upi://pay?pa={encode(UPI_ID)}&pn={encode(PAYEE_NAME)}"
    if rupees is not None:
        uri += f"&am={rupees}.00"
    return uri + f"&cu=INR&tn={encode(NOTE)}"


def main() -> None:
    for rupees in AMOUNTS:
        data = payment_uri(rupees)
        # border=4 is the quiet zone the QR spec asks for; scanners get unreliable below it.
        code = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M, border=4)
        code.add_data(data)
        code.make(fit=True)

        matrix = code.get_matrix()
        size = len(matrix)
        path = "".join(
            f"M{x} {y}h1v1h-1z"
            for y, row in enumerate(matrix)
            for x, dark in enumerate(row)
            if dark
        )
        # Always black on white, whatever the page theme is doing — a scanner needs the
        # contrast the spec assumes, and an inverted QR fails on a fair number of phones.
        svg = (
            f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {size} {size}" '
            f'shape-rendering="crispEdges" role="img" '
            f'aria-label="UPI payment QR code for {PAYEE_NAME}">'
            f'<rect width="{size}" height="{size}" fill="#ffffff"/>'
            f'<path d="{path}" fill="#000000"/></svg>'
        )

        name = f"upi-{rupees if rupees is not None else 'any'}.svg"
        (HERE / name).write_text(svg)
        print(f"{name:>14}  v{code.version}  {size}x{size} modules  {len(svg):>6} bytes")


if __name__ == "__main__":
    main()
