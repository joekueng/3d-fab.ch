# Print Calculator Backend

Java 21 / Spring Boot application. Start with the [repository setup](../README.md) and [backend agent guide](AGENTS.md).

## Extending the backend consistently

Before adding an endpoint, service, integration, or document, inspect a comparable implementation in the same domain. Follow its naming, constructor injection, DTO mapping, validation, transactions, exception handling, event flow, and focused test conventions. Keep controller → service → repository responsibilities and public/admin boundaries intact. Reuse existing domain helpers before introducing abstractions.

For persistent changes, align entities, repositories, DTOs, frontend models, and migration/deployment expectations; the project currently uses Hibernate schema updates. For external integrations, reuse configuration and error/audit handling rather than adding a separate delivery path.

## Calculator admission and slicing capacity

`POST /api/quote-sessions` accepts an optional positive `itemCount`. Calculator
clients send the number of model files, including when reusing a session. Before
clearing any existing quote, `QuoteRateLimitService` atomically reserves one
calculation and all its files against the caller's IP budgets. Rejected groups
leave the existing quote intact. Each accepted group returns `calculationId` in
the session DTO; it is ephemeral and is not persisted or included in later session
snapshots. Send it as `X-Quote-Calculation` on every line-item upload.

The server binds each permit to the IP, session and reserved file count. Every
upload attempt consumes one file, including failed attempts; permits cannot be
replayed after exhaustion and expire after 15 minutes without an upload. Budgets
count reservations when the calculation starts, not completion times. Abandoned
or failed groups are not refunded. A group cannot exceed the configured file
budget. Lightweight session reads, quantity/color updates and checkout do not
consume these budgets.

Defaults and environment overrides:

| Setting | Default | Purpose |
| --- | --- | --- |
| `QUOTE_RATE_LIMIT_MAX_CALCULATIONS` | `15` | Accepted calculation groups per IP/window |
| `QUOTE_RATE_LIMIT_MAX_FILES` | `75` | Files reserved by groups and legacy requests per IP/window |
| `QUOTE_RATE_LIMIT_WINDOW_SECONDS` | `60` | Sliding window over accepted admissions |
| `QUOTE_RATE_LIMIT_MAX_REQUESTS` | `15` | Existing per-file legacy request cap per IP/window |
| `QUOTE_RATE_LIMIT_TRUST_PROXY_HEADERS` | `false` | Existing opt-in for trusted proxy IP headers |
| `QUOTE_SLICING_MAX_CONCURRENT` | `2` | Quote jobs running per backend instance |
| `QUOTE_SLICING_MAX_QUEUED` | `20` | Additional jobs allowed to wait |
| `QUOTE_SLICING_QUEUE_WAIT_SECONDS` | `60` | Maximum wait for a running slot |

With otherwise unused budgets, one or two files allow 15 calculations per window,
ten files allow seven, and 15 files allow five. Both budgets apply together;
mixed group sizes consume the same shared file budget. Requests without
`itemCount` / `X-Quote-Calculation` remain compatible with the old 15-request cap,
including `/api/quote` and `/calculate/stl`, and share the 75-file budget. Invalid
permits never fall back to legacy admission. HTTP 429 returns `Retry-After` based
on when enough accepted work expires; rejected requests never extend the wait.

`SlicingCapacityService` wraps the full job (scan, storage, conversion, inspection,
slicing and result), including admin line items and legacy public quotes. It
releases capacity on success and failure. Full queues, queue timeouts and
interrupted waits return HTTP 429 with a five-second retry hint. Frontend batches
upload/process one file at a time, retaining progress and partial-file results;
unsubscribing cancels pending client requests. An already running server job can
continue after its client disconnects and retains its capacity slot until it ends.

Both budgets and permits are in memory per instance and reset on restart. Multiple
replicas need sticky routing for a group's requests or a shared permit/budget
store. No schema migration or dependency change is required. Deploy the backend
before the new frontend; old frontend requests remain supported. Tune concurrency
with measured CPU/RAM use on the deployment host; two jobs is an initial default,
not a hardware capacity guarantee.

Focused checks:

```bash
./gradlew test --tests '*QuoteRateLimitServiceTest' --tests '*SlicingCapacityServiceTest' --tests '*QuoteSessionRateLimitControllerTest'
```

## Email transaction boundaries

Order and contact-request creation publish domain events inside the service transaction.
Their email listeners run asynchronously with `@TransactionalEventListener(AFTER_COMMIT)`;
notifications must not be sent before the request and its attachments are committed.
`EmailAuditService` writes in a separate `REQUIRES_NEW` transaction, whose foreign keys
can only reference committed orders/requests. Keep this boundary even when email is
disabled, because skipped attempts are audited too. Attachment I/O failures roll back
contact-request creation, and rolled-back requests must not trigger notifications.

## Customer-facing output

- **Emails:** read the [template guide](src/main/resources/templates/email/README.md). New transactional messages reuse the shared layout fragments and the established localized context/SMTP flow.
- **PDFs/invoices:** inspect `src/main/resources/templates/invoice.html`, `invoice-logo.svg`, and the corresponding generation service. Preserve typography, branding, tables, money/date formatting, and page layout; verify the generated document.
- **Copy and localization:** preserve existing terminology and support Italian, English, German, and French where the related feature is localized. Do not replace localized content with hard-coded copy.

Use the [repository contribution workflow](../README.md#contributing-and-ai-agents-preserve-project-conventions) for every new implementation, including areas not listed here.

## Verification

From this directory, run the narrowest relevant tests first:

```bash
./gradlew test --tests '*QuoteSessionEmailServiceTest' --tests '*EmailTemplateTest' --tests '*SmtpEmailNotificationServiceTest' --tests '*OrderEmailListenerTest'
```

Use `./gradlew test` for broader backend changes. Render affected emails/documents with representative fixture data and compare against their established reference; do not send real customer messages for visual verification.

## Payment confirmation and TWINT inbox

### Live order notifications

`GET /api/orders/{id}/events` provides SSE invalidations (`order-changed`, empty
JSON payload) under the same public tracking contract as the existing order GET.
It exposes no customer data, credentials or payment details. Angular reloads the
existing public DTO when notified. Payment reports/confirmations and admin status
changes mark subscriptions only after commit. A dedicated scheduler coalesces and
sends updates within 250 ms, without network writes in payment transactions.
Every connection receives an initial invalidation to cover changes during setup
or reconnection. Heartbeats run every 15 seconds; streams expire after five minutes
and EventSource reconnects. Completion, timeout, errors and shutdown remove emitters.
There are limits of 1,000 streams per instance and ten per order; excess clients
use polling. Reverse proxies must preserve streaming (`proxy_buffering off`,
`proxy_read_timeout 75s` or longer); the endpoint also sets `X-Accel-Buffering: no`
and `Cache-Control: no-store`. Configure every external proxy accordingly.

Subscriptions are local to one backend instance. The 60-second client recovery
poll covers missed events and changes committed on another replica; immediate
cross-replica delivery would require shared pub/sub. No database migration is needed.

`PaymentService` locks the order before reporting or confirming payment. Only
`PENDING_PAYMENT` with payment `PENDING`/`REPORTED` can become `PAID`; repeat
confirmations preserve the confirmed method, timestamps and production/shipping
state. The customer sees `PAID` separately from `IN_PRODUCTION`. Production emits
no payment email. Invoice and CAD access continue to depend on payment confirmation.

`PaymentEmailOutbox` synchronously handles the payment events inside that same
transaction. Its unique `(order_id, kind)` key prevents duplicate automatic emails.
The independent `paymentEmailScheduler` sends a reported-payment email after
`PAYMENT_REPORTED_EMAIL_DELAY` (default `PT5M`), rechecking the locked order just
before dispatch. Confirmation cancels a pending report job and queues the paid
invoice. Browser activity and IMAP availability do not affect this queue.

Workers commit `SENDING` before dispatch. They never automatically retry `UNKNOWN`
(including interrupted `SENDING` after 15 minutes), `FAILED` or `SKIPPED` jobs.
SMTP submission failures can be ambiguous: inspect the email audit and SMTP logs
before using the existing administrative resend action. Rendering/authentication
failures before SMTP submission are definite failures. The existing order-created
and shipped listeners remain after-commit. Outbox delivery writes its audit in the
worker transaction, avoiding a separate audit transaction while holding the order
lock. A crash after SMTP acceptance can still leave `UNKNOWN`; SMTP cannot provide
exactly-once delivery.

### Enabling inbox acquisition

Set dedicated reading credentials; SMTP credentials are not reused:

```dotenv
TWINT_INBOX_ENABLED=true
TWINT_INBOX_IDLE_ENABLED=true
TWINT_INBOX_IDLE_RENEWAL=PT20M
TWINT_INBOX_IDLE_FALLBACK_INTERVAL=PT1M
TWINT_INBOX_HOST=mail.infomaniak.com
TWINT_INBOX_PORT=993
TWINT_INBOX_USERNAME=info@3d-fab.ch
TWINT_INBOX_PASSWORD=<set in deployment secrets>
TWINT_INBOX_FOLDER=INBOX
TWINT_INBOX_ORIGINAL_RECIPIENT=joekueng05@gmail.com
TWINT_INBOX_INITIAL_SINCE=<explicit ISO-8601 timestamp including offset>
TWINT_INBOX_POLL_MS=5000
TWINT_INBOX_PERIODIC_INTERVAL=PT3H
TWINT_INBOX_ACTIVE_WINDOW=PT10M
PAYMENT_REPORTED_EMAIL_DELAY=PT5M
```

The enabled setting defaults to false. There is no dry-run mode. With automation
enabled, an authenticated matching payment really confirms the order and queues
its invoice. Validate the Gmail filter and an original message *received at
Infomaniak* in a test environment before enabling it for customer orders.
The Unraid deploy script merges `common.env` before the environment-specific `.env`;
check the resulting container environment when diagnosing a running deployment.

### IMAP IDLE (default when inbox acquisition is enabled)

`TwintIdleListener` keeps the configured mailbox open in read-only IMAPS mode.
It uses [Angus IdleManager](https://eclipse-ee4j.github.io/angus-mail/docs/api/org.eclipse.angus.mail/org/eclipse/angus/mail/imap/IdleManager.html)
with socket channels. A notification wakes one reader, which scans only UIDs after
the persisted cursor, using the existing DKIM verification, parser and reconciliation.
Message-ID alone is not trusted for deduplication: UIDVALIDITY/UID, content hashes
and transaction claims remain in force. No new mailbox or forwarding change is required.

Startup/reconnect drains the backlog in bounded, separately committed batches on
the same IMAP connection. The listener installs callbacks before reading and retains
notifications received during processing, then re-arms IDLE. Waiting never holds a
database transaction/lock. The existing payment commit triggers SSE and the email
outbox as before. No periodic order-window database query runs in IDLE mode.

Connections renew every 20 minutes (allowed range 1..25 minutes), also recovering
missed notifications. Network/authentication/processing failures close the session
and reconnect with exponential backoff from 5 seconds to 5 minutes. A server without
the IDLE capability uses a one-minute recovery read on the persistent connection
(minimum configured fallback interval: ten seconds). Shutdown stops the selector,
closes the folder without expunging, and interrupts the reader. No SMTP credentials
are reused; no mail is marked read, moved or deleted. `TWINT_INBOX_ENABLED` still
defaults to false; enabling it can confirm actual customer payments.

Each enabled backend instance owns a listener. Enable acquisition on only the
intended instance/environment; the persisted cursor lock still serializes concurrent
readers sharing a database. Logs show `mode=IDLE` or `mode=FALLBACK` and reconnect
delays without credentials or message contents. Automated tests simulate notifications,
read/watch races, backlog, failures and shutdown; real server capability and forwarded
DKIM preservation must also be verified in the target environment.

### Legacy polling (TWINT_INBOX_IDLE_ENABLED=false)

`TwintMailboxScheduler` is the sole polling coordinator on the dedicated single-thread
`twintMailboxTaskScheduler`. Its lightweight database check runs every
`TWINT_INBOX_POLL_MS` (default 5000 ms). It selects one of two mailbox frequencies:

- **ACTIVE:** read every `TWINT_INBOX_POLL_MS` while at least one unpaid
  `PENDING_PAYMENT` order was created or first reported within
  `TWINT_INBOX_ACTIVE_WINDOW` (default `PT10M`).
- **PERIODIC:** otherwise read every `TWINT_INBOX_PERIODIC_INTERVAL` (default
  `PT3H`), even with no unpaid orders, to acquire delayed notifications.

Intervals run from completion of the last attempt, including failures. A slow read
never overlaps another read. New orders are noticed on the next database check;
they do not create tasks, reset the last-attempt time or accelerate the shared rate.
Payment, cancellation or expiry of the last window returns the reader to PERIODIC.
Repeated reports and page refreshes do not extend a window. Due periodic reads may
start up to one database-check interval later. Existing bounded UID batches remain
in effect: a backlog is processed across successive reads at the selected frequency.

On restart, the first scheduled check reads the mailbox once, then resumes the
appropriate frequency. Windows and the mailbox cursor come from persisted state;
the scheduling deadline is local to the process. Each backend instance has its own
coordinator: configure the enable flag per environment if `common.env` is shared.
The cursor lock continues to serialize readers sharing the same database.

INFO logs show disabled status, ACTIVE/PERIODIC transitions, check start/completion,
the first successful IMAP connection, UID scan counts and each candidate's outcome.
`otherSender` means the visible From does not match the original TWINT sender;
`olderThanCutoff` counts messages with a missing or earlier receive date than the
saved initial timestamp. No body, subject, recipient, transaction ID or password is
logged. Connection and empty-inbox details are available at DEBUG level for
`com.printcalculator.service.payment.twint`.

The legacy polling coordinator flow is documented in the [Italian diagram](../docs/uml/10-polling-twint.mmd)
and [English diagram](../docs/uml/en/10-polling-twint.mmd).

Acquisition uses TLS with hostname verification and a read-only mailbox. It uses
UIDVALIDITY/UID, never unread flags, and never deletes mail. The initial timestamp
is required and stored per mailbox; changing the environment variable later does
not rewind it. A UIDVALIDITY reset replays from that cutoff, with transaction
claims protecting against duplicate payment confirmation. All candidate receipt
outcomes and the cursor commit together. Network/DNS interruptions roll back the
batch and resume from the committed cursor after reconnect or the next legacy poll.

### Authentication, parser and reconciliation

The reference notification is the Italian TWINT format supplied in September 2026:
`no-reply@twintpay.ch`, a transaction-success sentence, and separate label/value
lines for `Importo`, `Data della transazione`, `Numero di transazione`, `Messaggio`.
The subject does **not** contain TWINT. MIME decoding supports quoted-printable,
plain text and HTML alternatives. Unknown/ambiguous formats require manual review.
Tests use synthetic data, without real customer IDs or transaction reversal links.

`TwintNotificationAuthenticator` uses [Apache jDKIM](https://james.apache.org/jdkim/)
to verify the original `twintpay.ch` RSA/SHA-256 signature with DNS keys. It requires
full-body coverage and signed From/To/Subject/MIME-Version/Content-Type, rejects
duplicate critical headers, and binds the signed To address to the configured
original Gmail recipient. If an outer Content-Transfer-Encoding exists, it must
also be signed. Forwarding headers and claimed SPF/DKIM/ARC “PASS” results are not
trusted. Forwarding must preserve the original signature and body; there is no
fallback to sender/subject trust or to an unverified ARC chain. Temporary DNS
failures are retryable; invalid signatures are recorded for manual review.

Only `Messaggio` supplies the order reference. A known full UUID is authoritative;
otherwise its first eight characters must identify exactly one order across all
statuses. Amounts compare exactly at cent precision; currency is not an additional
matching criterion. Transaction IDs uniquely claim an order, never locate one.
A different transaction on the same order is flagged as possible double payment.
Late confirmations never resend the invoice, overwrite a confirmed method/date,
or move advanced/cancelled/refunded orders backwards.

### Schema and operations

The additive tables in root `db.sql` are `payment_email_jobs`, `twint_receipts` and
`twint_mailbox_cursors`. Hibernate creates them under the existing update policy.
No uniqueness constraint is added to old order numbers/payments, and existing
`PAID` orders are not automatically converted to `IN_PRODUCTION`. Before rollout,
check existing duplicate payments per order and inconsistent statuses; review the
new table definitions against the target database. Do not replay historical
payment events to backfill email jobs: that could resend customer confirmations.

Useful read-only operational checks:

```sql
select order_id, count(*) from payments group by order_id having count(*) > 1;
select id, order_id, kind, status, due_at, attempted_at, result
from payment_email_jobs where status in ('FAILED', 'UNKNOWN', 'SENDING') order by due_at;
select id, order_id, match_type, transaction_id, amount, outcome, acquired_at
from twint_receipts where outcome not in ('CONFIRMED', 'DUPLICATE_TRANSACTION', 'DUPLICATE_MESSAGE', 'ALREADY_CONFIRMED')
order by acquired_at desc;
```

Receipt review is available through these persistent records, not a new public
endpoint. Original MIME data stays in the mailbox. Use the existing admin payment
confirmation after investigating a mismatch; do not reset receipt transaction
claims or email jobs to force a replay.

`PaymentWorkflowIntegrationTest` covers persisted windows, rollback and concurrent
confirmation/reporting using H2 in PostgreSQL mode with only lock SQL adapted.
Authentication tests generate fresh RSA keys; IMAP and SMTP tests use mocks.
`PaymentEmailRenderingTest` renders real localized service contexts under
`build/payment-email-preview/` for visual checking without customer sends.
