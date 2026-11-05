# Telecom Platform – Phases 1, 2a, 2b, 2c, 2d and 2e

Spring Boot 3 (Java 21) + MySQL 8 backend, and two React + Vite + MUI apps:
an **operator console** (staff) and a **customer portal**, built separately from one codebase.

```
backend/    Spring Boot API, Flyway migration, tests
frontend/   React apps (operator on :5173, customer on :5174)
docker-compose.yml   MySQL for local development
```

## Run locally
Requirements: JDK 21, Maven 3.9+, Node 20+, Docker.

```bash
docker compose up -d                                   # MySQL on 127.0.0.1:3306
cd backend && SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run     # API on :8080
cd frontend && npm install
npm run dev:operator      # http://localhost:5173
npm run dev:customer      # http://localhost:5174
```
The `dev` profile creates sample data (see `DevDataSeeder`) and prints one-time codes in the
backend console instead of sending email.

Sample accounts (all use email OTP, then set up an authenticator app on first login):
- Operator console: `super@platform.test` (Super Admin), `admin@netbridge.test`, `planner@netbridge.test`, `engineer@netbridge.test`, `admin@skylink.test`
- Customer portal: `anita@example.com`, `admin@orbit.test`, `billing@orbit.test`

## Sign-in flow
1. `POST /api/auth/{pool}/otp/request` – `pool` is `staff` or `customer`.
2. `POST /api/auth/{pool}/otp/verify` – returns a short-lived challenge.
3. `POST /api/auth/mfa/setup` then `/api/auth/mfa/confirm` (first login) – authenticator app (TOTP).
4. Access token in the response body; refresh token in an HttpOnly cookie.
5. `POST /api/auth/{pool}/refresh`, `POST /api/auth/{pool}/logout`, `GET /api/me`.

Dashboards: `GET /api/dashboard/platform` (Super Admin), `/operator` (operator staff), `/customer` (customers).
Roles and tenant scope come from the verified token, never from request parameters.

## Configuration
Outside the `dev` profile the app refuses to start without `JWT_SECRET`, `OTP_PEPPER` and
`TOTP_ENCRYPTION_KEY` (see `.env.example`). Never reuse the dev values.

## Phase 2a: network inventory and capacity (operator console)
New menu items: **Sites, Devices, Links, Topology, Capacity** (sample network is seeded in the `dev` profile
for NetBridge; sign in as `admin@netbridge.test`).

| Who | Can do |
| --- | --- |
| Operator Admin | everything below, plus create/edit sites, register devices, manage ports, edit capacity thresholds |
| Network Planner | read all; create, suspend and decommission links; add capacity measurements |
| Network Engineer | read only |
| Customers / Super Admin | no access to operator inventory |

API (all under the operator in the token; a `?operatorId=` parameter is ignored):
- `/api/inventory/sites` (GET, POST, PUT `/{id}`, PATCH `/{id}/status`)
- `/api/inventory/devices` (GET, POST, GET `/{id}`, PATCH `/{id}/status`, POST `/{id}/ports`), `/api/inventory/ports/{id}/status`, `/api/inventory/ports/available`
- `/api/inventory/links` (GET, POST, PATCH `/{id}/status`, POST `/{id}/decommission`)
- `/api/capacity/links`, `/links/{id}/history`, `/links/{id}/estimate`, `POST /links/{id}/measurements`, `/thresholds`, `/topology`

Rules enforced on the server: device lifecycle (PLANNED to INSTALLED to ACTIVE to MAINTENANCE or DECOMMISSIONED),
a device with live links cannot be decommissioned, a link needs two available ports on two different devices,
a port can belong to only one link (primary key on `port_allocations`), reserved/occupied ports cannot be toggled by hand,
IPv4 validation, unique codes and names per operator. Capacity measurements are simulated or entered by hand;
the estimate is a straight-line projection and never triggers any action.

### Reconciliation (Operator Admin only)
Menu item **Reconciliation**: paste or upload a discovery CSV (`deviceName`, `status` required; `managementIp`, `siteCode`,
`deviceType`, `vendor`, `model` optional; up to 5000 rows, 1 MB). Each device is compared with the inventory by name
(case-insensitive) and classified as MATCHED, STATUS_MISMATCH, NEWLY_DISCOVERED or MISSING_FROM_DISCOVERY. Bad rows are
skipped and listed with their line number. Nothing changes until an admin approves a result:
- STATUS_MISMATCH: applies the discovered status through the normal device lifecycle rules (so an impossible change is refused).
- NEWLY_DISCOVERED: creates the device as PLANNED (needs a valid `siteCode` in the file).
- MISSING_FROM_DISCOVERY: acknowledged only. Devices are never decommissioned automatically.
- Two admins cannot both apply the same result (the row is claimed first; failures roll the claim back).
API: `POST /api/reconciliation/imports` (JSON `{filename, csv}`), `GET /api/reconciliation/imports`, `GET /api/reconciliation/imports/{id}`,
`POST /api/reconciliation/results/{id}/review` (`APPROVED` or `REJECTED`). IP differences are not compared yet; only status.

## Phase 2b: plans, customer sign-up, verification (KYC/KYB), team users

**Who does what**
| Who | Can do |
| --- | --- |
| Super Admin | onboard operators (invites their admin), set each operator's highest plan price, suspend/reactivate operators |
| Operator Admin | create/edit/retire their own plans and prices (within the limit); list customers, review documents, approve/reject verification, suspend/reactivate/close customers |
| Network Planner | read plans; list customers (no documents or decisions) |
| Network Engineer | read plans |
| Customer (account admin) | upload verification documents and submit them; business accounts also manage team users |
| Customer (other team users) | view their account and plans only |

**Sign-up (customer portal, "New customer? Create an account")**
1. Details (individual or business; business needs a company name and a GSTIN, one live account per GSTIN per operator) and an operator.
2. A one-time code is emailed. Nothing is created and no GSTIN is reserved until the email is proven (`POST /api/signup/start`, then `/verify`).
3. The account is created as TRIAL (verification NOT_SUBMITTED, 14-day deadline) and the normal two-factor set-up follows.
Business accounts get a tenant code (`TEN-xxxxx`) and the first user becomes the account admin.

**Verification.** Individuals upload an ID, address proof and selfie; businesses upload an incorporation certificate, GST certificate,
company PAN and signatory ID. Files must be PDF, PNG or JPEG (checked by content, max 5 MB, max 20 per account) and are stored outside the
database (`app.storage.dir`, default `./data/kyc`). Documents lock once submitted. The operator admin approves (account becomes ACTIVE) or rejects with a
reason (the customer can fix and resubmit). A daily job suspends TRIAL accounts that missed the deadline without submitting, or were rejected and did not fix it.
Closing an account frees its GSTIN.

**Plans.** Types: TRIAL (price 0), PREPAID, POSTPAID, BUCKET (data add-on, no speed). Segment (individual/business) and type are fixed after creation.
Price changes are recorded in `plan_price_history`. Retiring hides a plan from new customers.

**Team (business accounts).** The account admin invites BILLING_MANAGER, TECHNICIAN or VIEWER users and toggles devices/plans/billing access.
Team users never get the "users" permission, and the admin account cannot be changed there.

New endpoints: `/api/public/operators`, `/api/signup/{start,verify}`, `/api/plans`, `/api/platform/operators`, `/api/customers` (+ `/{id}`, `/kyc/review`,
`/action`, `/kyc/documents/{id}/file`), `/api/customer/{profile,plans,kyc,kyc/documents,kyc/submit,team}`.
Migration `V4__plans_customers_kyc.sql` adds plans, price history, documents, sign-up requests and a "one live account per GSTIN" rule.

**Run notes.** `docker-compose.yml` now starts MySQL with `--log-bin-trust-function-creators=1`: the Phase 1 audit triggers cannot be created by a
non-root user otherwise when binary logging is on. In production run migrations with a privileged user or set that variable on the server.
The dev profile also seeds sample plans for NetBridge.

**Known limits (by design for now).** GSTIN is checked for format only; the real check is the operator reviewing the documents (later: GST portal lookup).
Documents are on local disk in dev: production needs encrypted object storage and malware scanning. Invitation and decision emails only go to the backend log
in dev (no real sender yet). A disabled team user keeps access until their access token expires (at most 15 minutes). The sign-up rate limit uses the
request's remote address, so configure forwarded headers behind a proxy.

## Phase 2c: orders, port reservation and installation

**The flow.** A verified customer chooses a plan, the operator checks the site and reserves a port, the site team installs the equipment, and only then
does the plan go live. A new, unverified account can take the free trial; paid plans need approved verification.

| Step | Who | What happens |
| --- | --- | --- |
| 1. Order | Customer account admin (Plans, "Choose this plan") | A trial creates a subscription (PENDING_ACTIVATION) and a connection (AWAITING_PORT). A paid plan creates an invoice and waits for payment (PENDING_PAYMENT); the connection is only arranged once the payment is verified. One live plan per customer, also enforced by the database. |
| 2. Reserve | Operator admin or planner (Connections, "Reserve port") | The site must have a free port on an ACTIVE device and its busiest measured uplink must be below the elevated threshold (default 85%). The port becomes RESERVED and an installation job is created. |
| 3. Install | Network engineer or admin (Installations, "Mark installed") | The customer's equipment serial number is recorded (unique), the port becomes OCCUPIED, the connection and subscription become ACTIVE, and the plan's validity starts that day. |
| If the visit fails | Engineer or admin ("Cannot complete") | The port goes back to the pool, the connection returns to AWAITING_PORT, and the customer is told why. |
| Cancel or release | Customer (before installation) or operator admin/planner | The port is freed, open jobs are cancelled, the subscription is cancelled. Closing a customer account releases its ports too (suspending does not). |

A port belongs to exactly one owner: `port_allocations` now holds links and customer connections, its primary key stops two reservations
winning the same port, and a check constraint stops a row having two owners. A device that still carries customer connections cannot be decommissioned.
The customer sees the serving site once reserved, and the device and port only once active.

Endpoints: `POST /api/customer/orders`, `GET /api/customer/service`, `POST /api/customer/subscription/cancel`; `GET /api/connections`,
`/connections/serviceability?siteId=`, `POST /connections/{id}/reserve|release`; `GET /api/installations`, `POST /installations/{id}/complete|fail`.
Migration `V5__subscriptions_and_provisioning.sql`.

**Payment.** Since Phase 2d a paid plan is paid first (see below); the free trial skips payment.

**Try it (dev profile).** Sign in to the customer portal as a verified customer (approve one in Operator console, Customers) and choose a plan;
in the operator console reserve a port at a site with a free port (Connections), then as the engineer mark it installed (Installations) and watch
"My connection" go live. Reserving at a congested or empty site is refused with the reason.

**Limits.** No visit scheduling, one equipment record (ONT) per connection, no data buckets yet, and no re-check of site capacity at installation time.
Customer emails only go to the backend log in dev.

## Phase 2d: billing and payments

**The money flow.** Choose a paid plan, get an invoice (price includes GST, split and stored), pay it, and only then is a connection arranged.
Renewals, extra data and refunds use the same invoice and payment machinery.

**Rules that keep money safe**
- Amounts are calculated on the server from the plan. The browser never sends an amount.
- A payment counts only when the payment provider reports it (a verified event or webhook), never because the browser says so.
- Events are idempotent: the same event twice changes nothing. A late success after a failure is still honoured.
- An invoice can have only one open payment and only one successful payment; the database enforces both (so a double click or two tabs cannot charge twice).
- An amount that does not match, a second payment for an already-paid invoice, or a payment whose purpose can no longer be fulfilled is never treated as paid:
  it is marked REFUND_DUE and returned by the refund job (every 10 minutes, retried until the provider accepts).
- Cancelling before installation voids the invoice and refunds a payment already made.
- No card or bank details touch this application. Only a masked UPI handle is kept for autopay.

**Payment provider seam.** `PaymentGateway` (create order, verify webhook signature, create mandate, charge mandate, refund) is the only thing a provider adapter
implements. A `MockPaymentGateway` is included for development (`app.payments.gateway=mock`, secret `app.payments.mock-secret`, at least 16 characters).
With `gateway=none` (the default outside dev) paid plans cannot be ordered at all. A real provider (for example Razorpay) needs its own adapter and keys.
The provider reports results to `POST /api/payments/webhook`; the signature (header `X-Gateway-Signature`) is checked on the raw body before anything is trusted.
In dev, the checkout page is a stand-in (`/api/customer/billing/payments/{id}/mock-checkout`) that feeds the same code path as a real webhook.

**Renewals (daily job, 02:15 UTC).** Seven days before a paid plan ends a renewal invoice is raised (once per period) and a manual-pay customer gets one reminder email.
With autopay on, the mandate is charged on the due date (retried daily; a failure emails the customer once per day). A paid plan that is still unpaid
seven days after its end date expires: the subscription ends, the open invoice is voided and the port is released. A free trial ends the day after its period.

**Data buckets.** With an active plan, "Add data" on a bucket plan raises an invoice; once paid the data is recorded as extra data on the plan (shown on My connection).

Endpoints: customer `GET /api/customer/billing/invoices|payments`, `POST /billing/invoices/{id}/pay`, `GET|POST|DELETE /autopay`, `POST /addons`;
operator admin `GET /api/billing/invoices|payments`; public `POST /api/payments/webhook`. Migration `V6__billing_and_payments.sql`.
Billing is available to the account admin and to team users with the billing permission.

**Try it (dev profile).** Approve a customer, choose a paid plan, pay on the stand-in page (try "Simulate failure" first), then reserve a port and install as before.
Turn on autopay with a UPI ID such as `anita@okbank` (an ID starting with `fail` gives a mandate that is always declined, to try the failure path).

**Limits for now.** Postpaid is billed like prepaid (a flat fee in advance); usage-based billing and data-cap enforcement need metering that does not exist yet.
No PDF invoices or GST e-invoicing, no manual adjustments or operator-initiated refunds, and no real provider adapter. A payment left open at the provider is never
expired by us. The gateway is called inside a database transaction (short, but note it when you add a slow provider). The daily jobs are idempotent but run on every
instance, so add a lock before running several. Reminders are email only (SMS and calls come with the ticketing phase).

## Phase 2e: support tickets and notifications

**One queue for every channel.** A ticket can be raised in the customer portal, or created from an email, an SMS or a phone call. Staff see all of them
in one queue (Support tickets) with filters, SLA timers and a conversation per ticket.

| Channel | How it arrives |
| --- | --- |
| Portal | The customer raises it (category, how bad it is, how to be updated). Priority follows the impact: down = high, slow = medium, question = low. |
| Email, SMS, call | The provider reports it to a signed webhook (`POST /api/inbound/{operatorCode}/email`, `/sms`, `/call`). The sender is matched to a customer by email address or mobile number. |
| Staff | "Log a ticket" for a customer who phoned, wrote or texted. |

Rules: a reference such as `TKT-00042` in an email subject or SMS adds a reply to that ticket, but only if the ticket belongs to the same customer (quoting someone
else's number does nothing); an SMS can start with `HELP`; a message from an unknown sender is kept in "Unmatched messages" and is never lost; a message the provider
delivers twice is ignored the second time. A ticket is acknowledged to the customer straight away.

**Status and SLA.** OPEN, IN_PROGRESS, WAITING_CUSTOMER, RESOLVED, CLOSED. A staff reply moves a new ticket to in progress; a customer reply to a waiting or resolved ticket
reopens it; a closed ticket takes no replies (open a new one). SLA due time: urgent 4h, high 8h, medium 24h, low 48h from creation, recalculated when the priority changes;
unresolved after that is "breached". Internal notes are never visible to the customer, and the customer never sees staff names.

**Updates by the customer's chosen channel.** Staff replies and "resolved" notices go by email or SMS. For "call me back" the notification is recorded as QUEUED, meaning a
person should call. Plan-ending reminders (Phase 2d) now go by email and SMS. Every message is recorded once in `notifications` (duplicates are impossible).

**Security.** The inbound webhooks are public but need a valid HMAC-SHA256 signature of the raw body (`X-Inbound-Signature`, secret `INBOUND_SECRET`, at least 16 characters).
With no secret they are switched off, never open. Staff queries are scoped by the operator in the token and customer queries by the customer in the token.

Endpoints: staff `/api/tickets` (list, `/summary`, `/customers`, `/{id}`, `POST` to log, `/{id}/reply`, `PATCH /{id}`, `/inbound`); customer `/api/customer/tickets`
(list, `/{id}`, `POST`, `/{id}/reply`, `/{id}/close`); public signed `/api/inbound/{operatorCode}/email|sms|call`. Migration `V7__support_tickets.sql`.
In the dev profile staff get "Simulate email/SMS/call" buttons (`app.inbound.simulator=true`, the endpoints do not exist otherwise).

**Limits for now.** Real email, SMS and telephony providers are not connected; they need adapters that post to the webhooks (the signature scheme is generic) and an SMS sender.
The inbound secret is shared by all operators. No attachments, no escalation emails to staff, no assignment to individual people (a team only), no call-back task screen,
and SLA hours are fixed. Unmatched messages can be dismissed but not linked automatically.

## Tests
Backend: `TotpServiceTest`, `CryptoServiceTest`, `DashboardSecurityTest` (Phase 1); `DeviceLifecycleTest`,
`UtilizationTest`, `CapacityEstimatorTest`, `InventorySecurityTest` (Phase 2a, including role rules and
"operator comes from the token"); `CsvParserTest`, `ReconcilerTest`, `ReconciliationSecurityTest`; `PlanRulesTest`, `GstinTest`, `CustomerRulesTest`, `Phase2bSecurityTest` (Phase 2b); `ProvisioningRulesTest`, `Phase2cSecurityTest` (Phase 2c); `BillingRulesTest`, `MockPaymentGatewayTest`, `Phase2dSecurityTest` (Phase 2d); `SupportRulesTest`, `InboundVerifierTest`, `Phase2eSecurityTest` (Phase 2e). Run `mvn verify`.
Frontend: `npm test` (operator and customer pages render with mocked API data, plus the sign-up form); `npm run build` type-checks and builds both apps.

## Not built yet
AI (Phase 3), real provider adapters (payments, email, SMS, telephony), usage metering, and the hardening items listed under each phase.
Redis and Kafka are not used; they are added when a feature needs them.
