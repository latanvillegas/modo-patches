# File Manager Plus 3.8.2 — Premium license model audit

## Scope
Read-only analysis of the existing JADX reports. This document does not alter entitlements, purchases, subscription data, receipts, billing, or patch behavior.

## Confirmed implementation
- `ax.p387y3.h.g()`: returns true if the local trial condition `h()` is true; otherwise delegates to `ax.p164k3.c.u().F(c.p0)`.
- `c.F(String)`: returns true for license state `VALID` or `PENDING`.
- `ax.p387y3.h.i()`: delegates to `c.I(c.p0)`, which checks whether the license product is an instance of `ax.p220nb.e`.
- `ax.p387y3.h.j()`: delegates to `c.H(c.p0)`, which checks whether the product type is `MANAGED`.
- `ax.p387y3.h.k()`: delegates to `c.J(c.p0)`, which checks for a `SUBSCRIPTION` product with license state `FREE`.
- `PaymentActivity.y0()`: displays the Premium interface when `h.g()` is true; if `h.j()` is false, it displays an expiration date from `h.d(context)`. It controls the manage-subscription button using `h.i()`, `h.j()`, and `h.k()`.
- `h.d(context)`: normally delegates to `c.u().s(c.p0)` for the expiration text.

## Interpretation and limits
- A `MANAGED` product is distinguished from a `SUBSCRIPTION` product, but these snippets do not establish a universal lifetime entitlement policy.
- Forcing `h.g()` alone does not create a license token or change the stored product type.
- The observed subscription screen is consistent with the user's active subscription; it does not prove that the experimental gate would work after expiration.
- Changing `h.j()` or the expiration label would only change local behavior/presentation and would not establish an authentic lifetime purchase.
- No changes to Privacy, AMOLED, Premium code, or the draft PR merge state are proposed by this report.

## Evidence
- User-provided `experimental-gates-source-audit.txt`, license implementation lines 525–550, PaymentActivity callers.
- User-provided `premium-ui-conditions-audit.txt`, PaymentActivity lines 992–1031 and PremiumGate lines 25–66.

## Further verification
Determine how the legitimate one-time purchase SKU is represented and displayed from existing product metadata, without modifying license or billing state. Avoid additional builds until a concrete functional change is justified.
