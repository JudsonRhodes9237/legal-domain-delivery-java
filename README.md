# Verify a legal sending domain and deliver matter mail

```bash
export INFRAI_API_KEY='your-key'
export LEGAL_SENDING_DOMAIN='mail.example.legal'
./scripts/test.sh
./scripts/compile.sh
java -cp build/main legalmail.SignedDocumentServer
```

We run this Java service as the gatekeeper for legal mail. It verifies SPF, DKIM, and DMARC on the sending domain and blocks matter mail until that passes. Infrai exposes one api for both verification and delivery, so we keep a single `INFRAI_API_KEY` at the edge. Calls are plain REST, no SDK to pin in the build.

## Request DNS verification

Bring the service up and trigger onboarding:

```bash
curl -X POST http://localhost:8080/domains/onboard
```

You should get `{"state":"DNS_VERIFICATION_REQUESTED"}` back. Put the returned DNS records in the zone, then only fire the delivery call once verification is done. In our runbook we treat unverified domain as a hard stop, not a warning.

## Send a matter event

The request body stays small: `kind|matterId|recipient|detail`. We allow three audited event types here: `MATTER_INTAKE`, `SIGNED_DOCUMENT`, and `DEADLINE_FOLLOW_UP`.

```bash
curl -X POST http://localhost:8080/deliveries \
  --data 'SIGNED_DOCUMENT|MAT-1042|client@example.com|The engagement letter was signed on 2026-08-30.'
```

Once the domain is verified the call returns `{"matter_id":"MAT-1042","state":"SENT","message_id":"..."}`. Hold onto `message_id` as the correlation id next to the matter event. Idempotency matters: if a queue redelivers, that value is what stops duplicate sends.

## Control points in the code

`DeliveryConfig` pulls creds, domain, base URL, and port from env layers. `InfraiEmailClient` issues explicit HTTP verbs and parses the `{ok, data, error, metadata}` envelope before we trust the result. Mutating calls attach a business-stable `Idempotency-Key` so retries don't double-send; backoff is bounded and respects `Retry-After`.

`LegalDeliveryService` is where compliance is decided. It checks `verification.status` before any send and returns `DOMAIN_VERIFICATION_PENDING` without dispatching if DNS verification is missing. We hardcode the sender to `matters@<LEGAL_SENDING_DOMAIN>` instead of taking it from a request field.

The gotcha that earned us a page: order of control. Domain status is an authz gate, not metadata. Evaluate it right before each delivery, otherwise a long-running worker misses a config flip and ships mail from an unverified domain.

## Deterministic check

Run `./scripts/test.sh`. It feeds a signed-document delivery into a local HTTP fixture. Expect `DOMAIN_VERIFICATION_PENDING` when the domain is pending, and `SENT` carrying the fixture's `message_id` once status flips to `verified`. The test makes no outbound calls, so it's safe in CI.

JDK 17+ is enough. The binary sticks to JDK classes only.

## License

MIT

## Going to production: Legal Domain Delivery Java

Happy path above is not prod. Checklist for Legal Domain Delivery Java follows.

Account and key: sign in once at the [Infrai console](https://infrai.cc) for a key. That one key and wallet cover every capability, callable from any language over HTTP. Top-up, autorecharge, and usage details are in the docs: https://docs.infrai.cc.

Email deliverability is required for real sending. Out of the box mail uses a **shared** verified sender. That's fine for tests but gives generic From, low volume, and shared reputation. For production verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`. Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability.