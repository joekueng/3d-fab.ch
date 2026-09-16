# Print Calculator Backend

Java 21 / Spring Boot application. Start with the [repository setup](../README.md) and [backend agent guide](AGENTS.md).

## Extending the backend consistently

Before adding an endpoint, service, integration, or document, inspect a comparable implementation in the same domain. Follow its naming, constructor injection, DTO mapping, validation, transactions, exception handling, event flow, and focused test conventions. Keep controller → service → repository responsibilities and public/admin boundaries intact. Reuse existing domain helpers before introducing abstractions.

For persistent changes, align entities, repositories, DTOs, frontend models, and migration/deployment expectations; the project currently uses Hibernate schema updates. For external integrations, reuse configuration and error/audit handling rather than adding a separate delivery path.

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
