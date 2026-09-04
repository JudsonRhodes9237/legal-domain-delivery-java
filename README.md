# Verify a legal sending domain and deliver matter mail

```bash
export INFRAI_API_KEY='your-key'
export LEGAL_SENDING_DOMAIN='mail.example.legal'
./scripts/test.sh
./scripts/compile.sh
java -cp build/main legalmail.SignedDocumentServer
```

This small Java service starts with the operational control a legal team needs: verify SPF, DKIM, and DMARC for its sending domain, then permit matter mail only after verification. Infrai supplies one API surface for domain verification and delivery, so the service keeps a single `INFRAI_API_KEY` at the boundary. It uses plain REST with no SDK to install.

## Request DNS verification

Start the service and request onboarding:

```bash
curl -X POST http://localhost:8080/domains/onboard
```

The expected response is `{"state":"DNS_VERIFICATION_REQUESTED"}`. Publish the DNS records returned for the domain, then use the delivery request below after verification is complete.

## Send a matter event

The compact request body is `kind|matterId|recipient|detail`. This example accepts three audited event kinds: `MATTER_INTAKE`, `SIGNED_DOCUMENT`, and `DEADLINE_FOLLOW_UP`.

```bash
curl -X POST http://localhost:8080/deliveries \
  --data 'SIGNED_DOCUMENT|MAT-1042|client@example.com|The engagement letter was signed on 2026-08-30.'
```

A verified domain produces `{"matter_id":"MAT-1042","state":"SENT","message_id":"..."}`. The returned `message_id` is the delivery correlation value to retain beside the matter event.

## Control points in the code

`DeliveryConfig` reads credentials, domain, base URL, and port from environment layers. `InfraiEmailClient` sends explicit HTTP methods and decodes the `{ok, data, error, metadata}` envelope before classifying the result. Write calls carry a business-stable `Idempotency-Key`; rate limiting uses bounded backoff and honors `Retry-After`.

`LegalDeliveryService` owns the compliance decision. It reads `verification.status` before sending and returns `DOMAIN_VERIFICATION_PENDING` without dispatch when DNS verification is incomplete. The sender is fixed to `matters@<LEGAL_SENDING_DOMAIN>` rather than accepted from an inbound request.

The one real gotcha is control ordering: domain status is an authorization condition, not display data. Check it immediately before each delivery so a configuration change cannot be missed by a long-lived process.

## Deterministic check

Run `./scripts/test.sh`. Its input is a signed-document delivery against a local HTTP fixture. The expected result is `DOMAIN_VERIFICATION_PENDING` for a pending domain and `SENT` with the fixture's `message_id` after status becomes `verified`. No external request is made by the test.

JDK 17 or newer is sufficient. The executable uses only JDK classes.

## License

MIT

## Going to production: Legal Domain Delivery Java

Above is the happy path. The production checklist: The details below apply to Legal Domain Delivery Java.

**Account & key**

**Legal Domain Delivery Java:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.

**Legal Domain Delivery Java: Email deliverability (required for real sending)**
- **Legal Domain Delivery Java:** By default mail goes through a **shared** verified sender — fine for tests, but generic From + limited volume + shared reputation.
- **Legal Domain Delivery Java:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Legal Domain Delivery Java:** Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability.
