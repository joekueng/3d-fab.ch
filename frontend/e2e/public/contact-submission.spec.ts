import { expect, test } from '@playwright/test';

interface MailpitMessage {
  ID: string;
  To: Array<{ Address: string }>;
  Subject: string;
}

interface MailpitMessageList {
  messages: MailpitMessage[];
}

interface MailpitMessageDetail {
  HTML?: string;
  Text?: string;
}

test('PUBLIC-004: a contact request reaches the local mailbox', async ({ page, request }) => {
  const mailPort = process.env['E2E_MAIL_PORT'];
  if (!mailPort || !/^\d+$/.test(mailPort)) {
    throw new Error('PUBLIC-004 requires E2E_MAIL_PORT from the disposable E2E harness');
  }

  const customerEmail = `contact-${crypto.randomUUID()}@example.test`;
  const message = `Browser contact request ${crypto.randomUUID()}`;
  await page.goto('/en/contact');
  await page.waitForLoadState('networkidle');
  const form = page.locator('app-contact-form form');
  const nameInput = form.locator('app-input[formcontrolname="name"] input');
  const emailInput = form.locator('app-input[formcontrolname="email"] input');
  const messageInput = form.locator('textarea[formcontrolname="message"]');
  const consent = form.locator('app-legal-consent input[type="checkbox"]');
  await nameInput.fill('E2E Contact');
  await emailInput.fill(customerEmail);
  await messageInput.fill(message);
  await consent.check();
  await expect(nameInput).toHaveValue('E2E Contact');
  await expect(emailInput).toHaveValue(customerEmail);
  await expect(messageInput).toHaveValue(message);
  await expect(consent).toBeChecked();

  const submit = form.locator('app-button[type="submit"] button');
  await expect(submit).toBeEnabled();
  await submit.click();
  await expect(page.locator('app-contact-form .success-state')).toBeVisible();

  const mailbox = process.env['E2E_MAIL_URL'] ?? `http://127.0.0.1:${mailPort}`;
  await expect.poll(async () => {
    const response = await request.get(`${mailbox}/api/v1/messages`);
    expect(response.status()).toBe(200);
    const payload = (await response.json()) as MailpitMessageList;
    const customerCount = payload.messages.filter((mail) =>
      mail.To.some((recipient) => recipient.Address.toLowerCase() === customerEmail),
    ).length;
    const adminCandidates = payload.messages.filter((mail) =>
      mail.To.some((recipient) => recipient.Address.toLowerCase() === 'contact@example.test') &&
      mail.Subject.startsWith('Nuova richiesta di contatto #'),
    );
    const adminBodies = await Promise.all(adminCandidates.map(async (mail) => {
      const detailResponse = await request.get(`${mailbox}/api/v1/message/${mail.ID}`);
      const detail = (await detailResponse.json()) as MailpitMessageDetail;
      return `${detail.HTML ?? ''}\n${detail.Text ?? ''}`;
    }));
    return { customerCount, adminFound: adminBodies.some((body) => body.includes(message)) };
  }, { timeout: 20_000 }).toEqual({ customerCount: 1, adminFound: true });

  const response = await request.get(`${mailbox}/api/v1/messages`);
  const payload = (await response.json()) as MailpitMessageList;
  const customerMail = payload.messages.find((mail) =>
    mail.To.some((recipient) => recipient.Address.toLowerCase() === customerEmail),
  );
  expect(customerMail?.Subject).toBeTruthy();
  expect(customerMail?.ID).toBeTruthy();

  const capturedMessage = await request.get(`${mailbox}/api/v1/message/${customerMail?.ID}`);
  expect(capturedMessage.status()).toBe(200);
  const detail = (await capturedMessage.json()) as MailpitMessageDetail;
  expect(`${detail.HTML ?? ''}\n${detail.Text ?? ''}`).toContain(message);
});
