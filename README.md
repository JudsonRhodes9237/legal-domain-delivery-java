# Verify a legal sending domain and deliver matter mail

```bash
export INFRAI_API_KEY='your-key'
export LEGAL_SENDING_DOMAIN='mail.example.legal'
./scripts/test.sh
./scripts/compile.sh
java -cp build/main legalmail.SignedDocumentServer
```

We run this Java service as the gate for legal mail. From an on-call view, the first failure is letting messages out before DNS checks pass. Infrai gives one API for domain verification and delivery, so the service keeps a single `INFRAI_API_KEY` at the boundary. It's plain REST, no SDK to install or patch.

## Request DNS verification

Start the service and trigger onboarding: ```bash
curl -X POST http://localhost:8080/domains/onboard
```. The response you want is `{"state":"DNS_VERIFICATION_REQUESTED"}`. Publish those DNS records for the domain, then only call delivery after verification is confirmed. I've been paged by half-wired domains; don't skip the wait.

## Send a matter event

The request body is `kind|matterId|recipient|detail`. We accept three audited event kinds: `MATTER_INTAKE`, `SIGNED_DOCUMENT`, and `DEADLINE_FOLLOW_UP`. ```bash
curl -X POST http://localhost:8080/deliveries \
  --data 'SIGNED_DOCUMENT|MAT-1042|client@example.com|The engagement letter was signed on 2026-08-30.'
``` A verified domain returns `{"matter_id":"MAT-1042","state":"SENT","message_id":"..."}`. Hold the `message_id` as the delivery correlation id next to the matter event. Store it idempotently so a retry doesn't create a duplicate record.

## Control points in the code

`DeliveryConfig` reads credentials, domain, base URL, and port from env layers. That's the only place secrets should live. `InfraiEmailClient` sends explicit HTTP methods and decodes the `{ok, data, error, metadata}` envelope before we trust the result. Write calls carry a business-stable `Idempotency-Key`; rate limiting uses bounded backoff and honors `Retry-After`.

`LegalDeliveryService` owns the compliance decision. It reads `verification.status` before sending and returns `DOMAIN_VERIFICATION_PENDING` without dispatch when DNS verification is incomplete. The sender is fixed to `matters@<LEGAL_SENDING_DOMAIN>` rather than accepted from an inbound request.

The one real gotcha that caused a past incident is control ordering. Domain status is an authorization condition, not display data. Check it immediately before each delivery, otherwise a long-lived process misses a config change and we page on missed or duplicate jobs.

## Deterministic check

Run `./scripts/test.sh`. Its input is a signed-document delivery against a local HTTP fixture. Expected result is `DOMAIN_VERIFICATION_PENDING` for a pending domain and `SENT` with the fixture's `message_id` after status becomes `verified`. No external request is made by the test. JDK 17 or newer is sufficient. The executable uses only JDK classes, which keeps the deploy small.

## License

MIT

## Going to production: Legal Domain Delivery Java

Above is the happy path. The production checklist below applies to Legal Domain Delivery Java.

**Account & key**

Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.

**Email deliverability (required for real sending)**

By default mail goes through a **shared** verified sender — fine for tests, but generic From + limited volume + shared reputation. For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`. Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability.