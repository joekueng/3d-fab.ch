# Email templates: preserve the 3D-Fab style

Read [backend/AGENTS.md](../../../../../AGENTS.md) before editing. SMTP rendering uses Thymeleaf through `SmtpEmailNotificationService`; new messages should follow that delivery path and the relevant domain service/event and audit conventions.

## Shared transactional layout

The source of truth is [fragments/layout.html](fragments/layout.html), extracted from the established [order confirmation](order-confirmation.html). Order, payment, shipping, and saved-session emails share these fragments:

```html
<head>
    <meta charset="UTF-8">
    <title th:text="${emailTitle}">Message title</title>
    <style th:replace="~{email/fragments/layout :: styles}"></style>
</head>
<body>
<div class="container">
    <div th:replace="~{email/fragments/layout :: header(${headlineText})}"></div>
    <div class="content">
        <p th:text="${introText}">Message content</p>
    </div>
    <div th:replace="~{email/fragments/layout :: footer(${footerText})}"></div>
</div>
</body>
```

Declare the Thymeleaf namespace on the enclosing `html` element. Pass localized text to header/footer; callers may use different context names, as [quote-session.html](quote-session.html) does. `SmtpEmailNotificationService` supplies `logoUrl` from `app.mail.logo-url`; this must be an absolute, publicly accessible image URL because mail clients fetch it without the frontend environment's authentication. Supply `currentYear` in the context.

The shared appearance is Arial, a light gray background, a centered white 600px container with 20px padding, the 220px brand logo, a centered title, gray body text, separators, and a centered footer. Reuse it directly, rather than copying CSS or adding a new palette, button style, logo size, or wrapper. Keep message-specific order/status panels in their templates. Existing contact-request emails retain their 640px tabular variant; inspect those when extending contact notifications.

## Adding or changing an email

1. Inspect the closest established message and the service providing its context. Use the shared fragments for new transactional templates; put only message-specific content in the new file.
2. Keep action links consistent with order emails: localized action text followed by an absolute visible URL. Preserve functional links, expiry information, and access notices.
   Customer order and saved-session URLs contain only the order/session identifier. Never append an access token in a query string or fragment: the frontend calls the corresponding `resume` endpoint, receives the credential with `Cache-Control: no-store`, and then loads private information from the backend.
3. Supply all dynamic values from the existing service flow. Use `th:text` for escaped text and `th:href`/`th:src` for URLs; do not use unescaped user HTML. Keep Italian, English, German, and French copy aligned.
4. If a shared design change is needed, update the fragment and check every consumer. Do not change shared branding as a side effect of adding a feature.
5. Render through the real Thymeleaf engine with fixture context, including the service context for new messages. Check fragment resolution, localized content, the configured public logo URL, footer, and exact links. Compare the rendered output with an established order email at desktop and narrow widths; opening the raw source does not resolve fragments. Real customer sends are unnecessary.

Run the focused commands in [backend/README.md](../../../../../README.md). `EmailTemplateTest` covers shared fragment rendering; `QuoteSessionEmailServiceTest` covers the saved-session context in all four languages.
