# Print Calculator Backend

Java 21 / Spring Boot application. Start with the [repository setup](../README.md) and [backend agent guide](AGENTS.md).

## Extending the backend consistently

Before adding an endpoint, service, integration, or document, inspect a comparable implementation in the same domain. Follow its naming, constructor injection, DTO mapping, validation, transactions, exception handling, event flow, and focused test conventions. Keep controller → service → repository responsibilities and public/admin boundaries intact. Reuse existing domain helpers before introducing abstractions.

For persistent changes, align entities, repositories, DTOs, frontend models, and migration/deployment expectations; the project currently uses Hibernate schema updates. For external integrations, reuse configuration and error/audit handling rather than adding a separate delivery path.

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
TWINT_INBOX_HOST=mail.infomaniak.com
TWINT_INBOX_PORT=993
TWINT_INBOX_USERNAME=info@3d-fab.ch
TWINT_INBOX_PASSWORD=<set in deployment secrets>
TWINT_INBOX_FOLDER=INBOX
TWINT_INBOX_ORIGINAL_RECIPIENT=joekueng05@gmail.com
TWINT_INBOX_INITIAL_SINCE=<explicit ISO-8601 timestamp including offset>
TWINT_INBOX_POLL_MS=5000
TWINT_INBOX_ACTIVE_WINDOW=PT10M
PAYMENT_REPORTED_EMAIL_DELAY=PT5M
```

The enabled setting defaults to false. There is no dry-run mode. With automation
enabled, an authenticated matching payment really confirms the order and queues
its invoice. Validate the Gmail filter and an original message *received at
Infomaniak* in a test environment before enabling it for customer orders.
The Unraid deploy script merges `common.env` before the environment-specific `.env`;
check the resulting container environment when diagnosing a running deployment.

No IMAP connection is opened unless an unpaid `PENDING_PAYMENT` order was created
or first reported within the active window. Creation/report events wake the reader
immediately; subsequent checks are every five seconds. Repeated reports and page
refreshes do not extend the window. When all qualifying orders are paid/cancelled,
or the last window expires, acquisition stops. Late messages wait for the next
new order/first report to reactivate the reader. This also applies after a server
restart: windows derive from persisted creation/report timestamps.

At INFO level the reader logs whether it is disabled or idle, when an order wakes
it, the first successful IMAP connection, UID scan counts, and each TWINT candidate's
review/confirmation outcome. `otherSender` means the message's visible From address
is not the original TWINT sender; `olderThanCutoff` means it predates the saved
initial timestamp. No message body, subject, recipient, transaction ID or password
is logged. Per-poll connection and empty-inbox details are available at DEBUG level
for `com.printcalculator.service.payment.twint` when needed.

Acquisition uses TLS with hostname verification and a read-only mailbox. It uses
UIDVALIDITY/UID, never unread flags, and never deletes mail. The initial timestamp
is required and stored per mailbox; changing the environment variable later does
not rewind it. A UIDVALIDITY reset replays from that cutoff, with transaction
claims protecting against duplicate payment confirmation. All candidate receipt
outcomes and the cursor commit together. Network/DNS interruptions roll back the
batch and resume during the next active check.

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
