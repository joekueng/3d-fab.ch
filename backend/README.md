# Print Calculator Backend

Java 21 / Spring Boot application. Start with the [repository setup](../README.md) and [backend agent guide](AGENTS.md).

## Extending the backend consistently

Before adding an endpoint, service, integration, or document, inspect a comparable implementation in the same domain. Follow its naming, constructor injection, DTO mapping, validation, transactions, exception handling, event flow, and focused test conventions. Keep controller → service → repository responsibilities and public/admin boundaries intact. Reuse existing domain helpers before introducing abstractions.

For persistent changes, align entities, repositories, DTOs, frontend models, and migration/deployment expectations; the project currently uses Hibernate schema updates. For external integrations, reuse configuration and error/audit handling rather than adding a separate delivery path.

## Public and admin order responses

The public order endpoints (`GET /api/orders/{id}`, order creation and payment
reporting) return `PublicOrderDto`. This independent DTO excludes customer email,
phone, billing/shipping addresses, customer type, internal contact-request IDs
and admin email logs in every order status. Keep it separate from `OrderDto`,
which is reserved for authenticated `/api/admin/orders` endpoints. Shared item
and deliverable DTOs contain the product/print details needed by both interfaces.
The checkout response still supplies the information-area credential; order GET
and payment-report responses do not populate it.

Checkout requests still collect customer data for fulfilment, emails and document
generation. `GET /api/orders/{id}/confirmation` serves the QR payment PDF only for
`PENDING_PAYMENT` orders without `paidAt`. If a payment record exists, it must be
`PENDING` or `REPORTED` without `receivedAt`. Other states and missing orders return
`404` with `Cache-Control: no-store`, so direct UUID links cannot retrieve the
document after payment. The page already hides the download outside
`PENDING_PAYMENT`. Email attachments and authenticated admin document downloads
use their separate generation paths and remain available after payment.
Information-area access is unchanged; its messages and attachments may contain
customer-provided personal data.

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

## Model conversion and previews

Calculator uploads accept STL, 3MF, OBJ and STEP/STP. Preserve original files and names in
quote/order storage. 3MF quoting continues slicing the original project with its
existing conversion fallbacks; OBJ and STEP/STP quoting use a generated STL. Store generated
STLs in the existing `convertedStoredPath` pricing metadata, with no schema change.
`/line-items/{id}/stl-preview` serves that STL when available and verifies that the
item and stored path belong to the requested session.

`POST /api/model-previews` accepts one multipart `file` and returns `model/stl`
without creating a quote session or retaining files. It scans the upload before
conversion, uses the existing quote admission and slicing capacity controls, and
always cleans temporary files. Preview requests consume the legacy request/file
budget, but do not reserve calculations. Uploads retain the 200 MB limit; previews
are bounded at 75 MiB, matching the browser viewer. Failure leaves quoting available.

Admin session previews use `GET /api/admin/sessions/{sessionId}/items/{itemId}/stl-preview`.
`AdminSessionPreviewService` checks item ownership and the existing session storage
boundary, serves the stored converted STL (or the original STL), and returns
`no-store`. Missing or non-STL previews return 404; reads never trigger slicing.
Deploy this additive endpoint before the frontend session preview action. No
schema or stored-file migration is needed.

`GCodeParser` sums comma-separated per-filament weights and lengths, including unused
slots, and prefers Orca's explicit total weight when present. Repeated metadata
lines replace the previous snapshot. This matches OrcaSlicer’s
[G-code exporter](https://github.com/OrcaSlicer/OrcaSlicer/blob/v2.3.2/src/libslic3r/GCode.cpp)
(verified 2026-10-22). Invalid or zero material/time statistics trigger the existing
geometry fallback or a processing error; they must never create READY items with
zero weight and an indefinitely pending shipping quote. Existing affected sessions
need recalculation to replace their stored zero-weight results.

`ModelConversionSupport` reuses the installed LWJGL Assimp 3.4.3 binding and its
existing triangulation/transform flags; OBJ imports remove material-library
references because only geometry is used. OBJ coordinates use the per-file
`modelUnit` (`mm`, `cm`, `m`, `in`, default `mm`). Preview and quote conversion
apply the same factor (1, 10, 1000, 25.4) to the generated STL only. Preserve
`modelUnit` in pricing metadata and the session response, including review items,
so reopening a session cannot reset its physical size. STL remains in millimetres;
3MF and STEP retain their declared units. 3MF conversion retains the existing Assimp/CLI/XML
fallbacks. OBJ support is documented in
the [official Assimp format list](https://github.com/assimp/assimp/blob/master/doc/Fileformats.md)
(checked 2026-10-21); conversion tests exercise the installed native binding.

The viewer fits its camera and zoom limits to the actual model bounds, including
submillimetre meshes. Before slicing an STL (including converted OBJ/STEP), reject
geometry whose largest dimension is below the selected nozzle diameter with
`MODEL_TOO_SMALL`. This avoids sending microscopic meshes to OrcaSlicer; retain
the original and generated preview for review. Never infer metres from small OBJ
coordinates or silently resize a customer's part.

STEP/STP conversion uses `StepConversionService` and the bundled
`model-conversion/step_to_stl.py` script in an isolated Python subprocess. The
Docker image installs `cadquery-ocp-novtk==7.9.3.1.1` and
`cadquery-ocp-proxy==7.9.3.1.1` in `/opt/step-converter`; `step-requirements.txt`
pins all versions and published wheel hashes. This pinned release supplies
Python 3.10 wheels used by the previous Ubuntu 22.04 runtime. The same pinned
release also supports Python 3.12 in the current Ubuntu 24.04 runtime; no CAD
dependency upgrade accompanies the Orca update. Java and Node dependencies are unchanged.

The converter transfers all STEP roots, normalizes declared units to millimetres,
meshes with 0.05 mm linear and 0.1 radian angular deflection, and exports binary
STL. Preview and quoting use this same conversion. Only a complete STL is copied
into session storage; failures preserve the uploaded original for consultation.
Timeout, interruption and errors terminate the child process and clean temporary
files. Deploy the rebuilt backend image before enabling STEP/STP in the frontend.
Launch the configured interpreter with `-I` and separate script/input/output
arguments, validated with the shared CLI argument helper; never build a shell
command from model paths. `StepConversionServiceTest` checks literal argument
boundaries with spaces and shell metacharacters as well as conversion cleanup.

| Setting | Default | Purpose |
| --- | --- | --- |
| `STEP_PYTHON_PATH` | `venv/bin/python` under the backend working directory; `/opt/step-converter/bin/python` in Docker | Python interpreter with the pinned CAD packages |
| `STEP_CONVERSION_TIMEOUT_SECONDS` | `120` | Maximum time for one STEP conversion |

`./start.sh` prepares the pinned environment automatically and sets
`STEP_PYTHON_PATH`. Manual local startup defaults to `venv/bin/python` under
the backend working directory; the deployed image keeps `/opt/step-converter`.
For a manual local start with Python 3.10–3.14, run:

```bash
bash scripts/setup-step-converter.sh
export STEP_PYTHON_PATH="$PWD/venv/bin/python"
"$STEP_PYTHON_PATH" scripts/check_step_conversion.py
```

The native check generates STEP/STP fixtures and verifies dimensions, units,
volume, curved surfaces, multiple bodies and invalid files. When OrcaSlicer is
available at `SLICER_PATH`, it also checks the generated STL through `--info`.
The existing limits of the browser viewer still apply to every preview.

Dependency evidence, checked 2026-10-21: the
[CadQuery wheel build system](https://github.com/CadQuery/ocp-build-system) documents
the `novtk` variant and its proxy dependency; the
[selected PyPI release](https://pypi.org/project/cadquery-ocp-novtk/7.9.3.1.1/)
provides CPython 3.10+ wheels for Linux amd64/arm64 and macOS. OCP is licensed
under Apache-2.0; its bundled OpenCascade libraries retain their upstream licenses.
Verified the installed `STEPControl_Reader`, `BRepMesh_IncrementalMesh` and
`StlAPI_Writer` APIs against the release and exercised them in the amd64 runtime.
Run `node scripts/check-external-packages.mjs` from the repository root to validate
the lock metadata alongside the existing dependency manifests.

Verification on 2026-10-21: the external-package check passed all 45 metadata
checks; native STEP checks passed all six cases, including OrcaSlicer `--info`.
The backend suite passed with 491 tests (one skipped), and all 72 calculator
frontend tests passed. TypeScript, i18n parity and UI reuse checks also passed.
The browser upload flow and a rebuilt production image were not rerun in this check.

## CLI printer profiles and usable volume

`SlicerProfilePreparation` changes only temporary job profiles. Raw meshes use
Textured PEI Plate when the filament supports it and no explicit plate was set;
this avoids Orca's implicit Cool Plate rejecting PETG. Existing 3MF project plate
selection is retained. For single-extruder P2S presets, choose the first/default
extruder variant explicitly: Orca 2.3.2 can crash while expanding the Standard /
High Flow alternatives. Preserve temperatures, flow limits, speeds and acceleration;
never collapse actual multiple extruders or change the source presets.

Pass `--arrange 0` explicitly on the first slice. Omitting it enables Orca's
automatic arrangement with a 1 mm bed-edge inset. Retry arrangement/orientation
only when needed. Classify placement failures as requiring splitting only when
the model bounds exceed the physical profile volume, allowing 0.001 mm for STL
float noise. The A1 and P2S profiles therefore include the full 256 × 256 × 256 mm;
249, 250 and 256 mm cubes do not require splitting, while 257 and 270 mm cubes do.
An unrelated slicer failure inside the volume must remain a processing failure.

## Email transaction boundaries

Order and contact-request creation publish domain events inside the service transaction.
Their email listeners run asynchronously with `@TransactionalEventListener(AFTER_COMMIT)`;
notifications must not be sent before the request and its attachments are committed.
`EmailAuditService` writes in a separate `REQUIRES_NEW` transaction, whose foreign keys
can only reference committed orders/requests. Keep this boundary even when email is
disabled, because skipped attempts are audited too. Attachment I/O failures roll back
contact-request creation, and rolled-back requests must not trigger notifications.

## Legal acceptance records

`LegalConsentService` records the existing required terms/privacy checkboxes in
`legal_consents`, following the order/contact-request foreign-key pattern used by
`EmailLog`. Each new checkout (shop, calculator or CAD/services) and each submitted
contact/custom-quote request has one record with exactly one parent, a server UTC
timestamp, both acceptance flags, the submitted email snapshot, normalized language,
document versions and client IP (IPv4/IPv6). The parent identifies the submission
and its customer; calculator orders also retain their source quote session.
Records are not included in public API responses.

Controllers obtain the IP from `LegalConsentRequestService` and pass it through
the existing service calls; request bodies cannot supply the audit IP. The shared
`security/IpAddressUtils` retains the QR parser/normalization behavior. Legal
acceptance uses the connecting address by default. To resolve `X-Forwarded-For`,
`Forwarded` or `X-Real-IP`, set `LEGAL_CONSENT_TRUST_PROXY_HEADERS=true` and
`LEGAL_CONSENT_TRUSTED_PROXY_NETWORKS` to the connecting proxies' IPs/CIDRs.
Both settings are required; headers from untrusted peers are ignored, including
private peers. Configure only actual proxies that sanitize incoming headers.
For a trusted connecting proxy, walk the chain from right to left and stop at
the first address outside the configured proxy networks, including private
addresses. Never cross that boundary to select an earlier public address for audit.
Invalid/unavailable IPs and existing records retain a null `client_ip`; no
historical IP is inferred. Browser/user-agent data is not collected.

Creation joins the submission transaction (`MANDATORY`): persistence failures abort
the submission, and failed orders/attachment uploads leave no acceptance record.
Existing validation still rejects missing/false flags. Unique constraints prevent
duplicate acceptance records for the same order or contact request. Records have
no update API and their mapped columns are not updatable.

The version constants in `LegalConsentService` match the currently published
`LEGAL.TERMS.META.VERSION`, `LEGAL.TERMS_UPDATE_DATE` and
`LEGAL.PRIVACY_UPDATE_DATE` in all frontend catalogs. Change these constants with
the corresponding documents whenever the legal text changes, preserving prior
versions in source history. An already open older client cannot attest a separate
document version: the record identifies the server's published version at submission.

Deployment is additive: Hibernate `ddl-auto=update` creates the new table, foreign
keys and unique constraints; matching reference DDL is in `../db.sql`. No existing
orders or requests are backfilled because their acceptance timestamps/versions
were not recorded. A plain calculator upload, preview or saved session does not
submit the required terms/privacy checkbox and creates no such record.

## Filament and shop color translations

`POST /api/admin/filaments/translate-colors` uses the existing admin session/CSRF
protection and `AdminLocalizedTextTranslationService`. The source is always the
saved Italian color label (legacy fallback: `colorName`); EN/DE/FR are overwritten.
`AdminFilamentColorTranslationService` deduplicates Italian labels, translates in
batches of 25, validates all three target languages, and applies all batches in one
transaction only after every translation succeeds. It covers inactive filaments
and shop variants too. Existing shop variants matching material, internal color name
and hex use the filament's Italian source; shop-only colors use their own source.
Italian labels, internal names, IDs, stock and prices remain unchanged. Existing
columns are reused, so no schema migration is required. The public options/catalogue,
cart and order DTOs already expose the localized labels and receive the saved updates
on their next read. New products continue to copy color labels from filament stock.
Repository updates write only EN/DE/FR and check that the Italian source is unchanged;
concurrent stock/price edits are preserved, and source changes abort with HTTP 409.

## Customer-facing output

- **Emails:** read the [template guide](src/main/resources/templates/email/README.md). New transactional messages reuse the shared layout fragments and the established localized context/SMTP flow.
- **PDFs/invoices:** inspect `src/main/resources/templates/invoice.html`, `invoice-logo.svg`, and the corresponding generation service. Preserve typography, branding, tables, money/date formatting, and page layout; verify the generated document.
- **Copy and localization:** preserve existing terminology and support Italian, English, German, and French where the related feature is localized. Do not replace localized content with hard-coded copy.

Use the [repository contribution workflow](../README.md#contributing-and-ai-agents-preserve-project-conventions) for every new implementation, including areas not listed here.

## Verification

### Mandatory antivirus scanning

Every public and admin upload requires a successful ClamAV scan before the file is
accepted, processed or copied into permanent storage. Both `ClamAVService.scan()`
and `scanRequired()` apply the same policy: infected files are rejected, and a
disabled, unavailable or failing scanner returns HTTP 503 `ANTIVIRUS_UNAVAILABLE`.
Do not catch these failures and continue storing the upload.

`CLAMAV_ENABLED` defaults to `true`. Setting it to `false` blocks uploads; there is
no development bypass. `./start.sh` starts the local scanner and connects the
backend to `localhost:3310`. Deployment environments must provide a reachable
`CLAMAV_HOST`/`CLAMAV_PORT` with current virus definitions. Copies of existing
stored files and application-generated documents do not represent new uploads.

Run the live clean-file/EICAR verification against a local scanner with
`CLAMAV_LIVE_TEST=true ./gradlew test --tests '*ClamAVRequiredScanTest'`.

### Backend container user and storage permissions

The runtime image runs the API and OrcaSlicer as `printcalc` (UID/GID `10001:10001`). `/app/temp` and the five storage directories are owned by that user in the image. The deployment's host bind mounts replace those image directories, so the host directories must also be readable, writable and traversable by UID 10001. The GeoLite directory is read-only; the named profiles volume is populated by a short-lived root maintenance container and read by the API.

Before deploying the non-root image to an existing environment, inspect and back up its storage directories, then prepare permissions during a maintenance window. For example, on the Docker host for one environment:

```bash
environment_name=dev # or int / prod
storage_base="/mnt/cache/appdata/print-calculator/${environment_name}"
for storage_name in storage_quotes storage_orders storage_requests storage_media storage_shop; do
  sudo chown -R 10001:10001 "${storage_base}/${storage_name}"
done
```

Preserve the public media directory's read and traverse permissions for the external Nginx alias. If the host uses Docker user namespaces or ACLs, map the container UID to its host UID or grant equivalent ACL access instead of using the example `chown`. The deployment script checks each bind mount with UID 10001 before replacing the running backend. After deployment, verify `docker exec print-calculator-backend-${environment_name} id`, health, a quote upload/slice, an order attachment and a media upload. For a newly created disposable E2E stack, Docker initializes named volumes from the image-owned directories.

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

## Service invoices and interim billing

The existing admin CAD invoice flow also supports general service invoices.
`POST /api/admin/cad-invoices` accepts `serviceLines`: an ordered list of
free-text descriptions, `HOURLY` or `FIXED` billing, quantity and CHF unit price.
There is no application-level row-count limit or predefined service catalogue.
Hourly quantity and unit price use at most two decimal places; fixed-price rows
have quantity one. Each line rounds to cents before summing. Providing service
rows replaces the legacy CAD hours/rate contribution, preventing double charges.
Print prices, setup and shipping still come from the existing calculator.

Optional `clientName` and `collaborationName` are administrative grouping labels
(customer/company and project), never public identity or authorization. Exact
label pairs form groups; these are not staff assignments or a customer CRM.
Each interim invoice is a separate quote session, checkout and order. The UI
starts it with the same group labels and empty service rows; it does not copy
previously billed services or calculate a project's remaining balance.

`invoiceName` and immutable service details are copied to the order on checkout.
Public checkout, order details and invoice PDFs use that agreed snapshot.
Converted sessions cannot change financial details. The admin metadata PATCH
may relabel their grouping/name without rewriting issued documents. Both admin
updates acquire the session lock used by order creation.

Deployment adds nullable `invoice_name`, `invoice_client_name`,
`collaboration_name` and JSON `service_lines` to quote sessions; orders add
nullable `invoice_name` and JSON `service_lines`. Hibernate update and the
idempotent additions in `db.sql` align these columns. Existing CAD invoices need
no backfill and retain their previous hours/rate calculation. Keep the existing
`CAD_ACTIVE` / `isCadOrder` compatibility flags: general service orders reuse
the paid-deliverable upload/download flow and payment confirmation rules.

Verification covers service-only/mixed totals, request validation with 250 rows,
Hibernate JSON round trips, immutable order details, existing paid-file gates
and multi-page invoice rendering.

CAD content edits use `PUT /api/admin/cad-invoices/{sessionId}`. The path ID is
authoritative even if `sessionId` is missing or different in the body. The existing
POST remains the creation / calculator-session association contract. Ordinary
content and metadata edits keep the same session and checkout URL.

`POST /api/admin/cad-invoices/{sessionId}/delete` requires `linkAction`:
`NEW_INVOICE` reserves the existing UUID for a blank replacement; `REDIRECT`
requires a public `targetPath`, normalized with the existing QR path rules.
Destinations cannot be external, contain query strings, or point back to checkout.
Converted or otherwise order-linked invoices cannot be deleted. Deletion takes
the same session lock as checkout, removes print rows and invoice attachments,
clears labels/notes and detaches customer instructions. A `CAD_DELETED` record
retains the link reservation; it is excluded from invoice/session lists, session
counts and expiry cleanup. No previous invoice details are returned from it.
Public checkout receives only a `redirectPath` or HTTP 410, both without caching.
Resume returns no customer credentials for a redirect, including after expiry;
checkout of a deleted record is rejected even from a stale browser tab.

Saving the replacement reactivates the reserved UUID and renews its expiry.
Until then the old checkout is unavailable. A redirect reservation cannot be
reactivated by an ordinary edit. The new nullable `deleted_redirect_path` column
is additive under Hibernate schema update; matching DDL is in `db.sql` and needs
no backfill. Existing instructions retain their normal draft retention policy.
