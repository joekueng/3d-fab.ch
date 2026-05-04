const RICH_TEXT_ALLOWED_TAGS = new Set([
  'P',
  'DIV',
  'BR',
  'STRONG',
  'B',
  'EM',
  'I',
  'U',
  'UL',
  'OL',
  'LI',
  'A',
]);

export function normalizeDescriptionForEditor(
  value: string | null | undefined,
  isBrowser: boolean,
): string {
  return normalizeRichTextStorageValue(value ?? '', isBrowser) ?? '';
}

export function normalizeRichTextStorageValue(
  value: string,
  isBrowser: boolean,
): string | null {
  const normalized = value.trim();
  if (!normalized) {
    return null;
  }
  const sanitized = containsHtmlMarkup(normalized)
    ? sanitizeRichTextHtml(normalized, isBrowser)
    : plainTextToRichTextHtml(normalized);
  const compact = sanitized.trim();
  if (!compact || !hasMeaningfulRichText(compact, isBrowser)) {
    return null;
  }
  return compact;
}

export function hasMeaningfulRichText(
  value: string,
  isBrowser: boolean,
): boolean {
  return (
    extractTextFromHtml(value, isBrowser)
      .replace(/\u00a0/g, ' ')
      .trim().length > 0
  );
}

export function serializeNodeChildren(
  node: Node,
  isBrowser: boolean,
): string {
  if (!isBrowser) {
    return node.textContent ?? '';
  }
  const serializer = new XMLSerializer();
  let html = '';
  for (const child of Array.from(node.childNodes)) {
    html += serializer.serializeToString(child);
  }
  return html;
}

export function replaceElementContentFromHtml(
  element: HTMLElement,
  html: string,
  documentRef: Document,
  isBrowser: boolean,
): void {
  if (!isBrowser) {
    return;
  }

  if (!html) {
    element.replaceChildren();
    return;
  }

  const parser = new DOMParser();
  const parsed = parser.parseFromString(`<body>${html}</body>`, 'text/html');
  const nodes = Array.from(parsed.body.childNodes).map((child) =>
    documentRef.importNode(child, true),
  );
  element.replaceChildren(...nodes);
}

function containsHtmlMarkup(value: string): boolean {
  return /<\/?[a-z][\s\S]*>/i.test(value);
}

function plainTextToRichTextHtml(value: string): string {
  const normalized = value.replace(/\r\n?/g, '\n').trim();
  if (!normalized) {
    return '';
  }
  return normalized
    .split(/\n{2,}/)
    .map(
      (paragraph) =>
        `<p>${escapeHtml(paragraph).replace(/\n/g, '<br>')}</p>`,
    )
    .join('');
}

function sanitizeRichTextHtml(value: string, isBrowser: boolean): string {
  if (!isBrowser) {
    return stripPotentiallyUnsafeHtml(value);
  }

  const parser = new DOMParser();
  const sourceDocument = parser.parseFromString(
    `<body>${value}</body>`,
    'text/html',
  );
  const outputDocument = parser.parseFromString('<body></body>', 'text/html');
  const outputBody = outputDocument.body;

  for (const child of Array.from(sourceDocument.body.childNodes)) {
    const sanitizedNode = sanitizeRichTextNode(child, outputDocument);
    if (sanitizedNode) {
      outputBody.appendChild(sanitizedNode);
    }
  }

  return serializeNodeChildren(outputBody, isBrowser);
}

function sanitizeRichTextNode(
  node: Node,
  outputDocument: Document,
): Node | DocumentFragment | null {
  if (node.nodeType === Node.TEXT_NODE) {
    return outputDocument.createTextNode(node.textContent ?? '');
  }
  if (node.nodeType !== Node.ELEMENT_NODE) {
    return null;
  }

  const sourceElement = node as HTMLElement;
  const tagName = sourceElement.tagName.toUpperCase();
  const childNodes = Array.from(sourceElement.childNodes)
    .map((child) => sanitizeRichTextNode(child, outputDocument))
    .filter((child): child is Node | DocumentFragment => child !== null);

  if (!RICH_TEXT_ALLOWED_TAGS.has(tagName)) {
    const fragment = outputDocument.createDocumentFragment();
    for (const child of childNodes) {
      fragment.appendChild(child);
    }
    return fragment;
  }

  const element = outputDocument.createElement(tagName.toLowerCase());
  if (tagName === 'A') {
    const href = sanitizeRichTextHref(sourceElement.getAttribute('href'));
    if (href) {
      element.setAttribute('href', href);
      if (href.startsWith('http://') || href.startsWith('https://')) {
        element.setAttribute('target', '_blank');
        element.setAttribute('rel', 'noopener noreferrer');
      }
    }
  }
  for (const child of childNodes) {
    element.appendChild(child);
  }

  if (tagName === 'A' && !element.textContent?.trim()) {
    return null;
  }
  if (
    (tagName === 'UL' || tagName === 'OL') &&
    !element.querySelector('li')
  ) {
    return null;
  }
  if (tagName === 'LI' && !element.textContent?.trim()) {
    return null;
  }

  return element;
}

function sanitizeRichTextHref(rawHref: string | null): string | null {
  const href = rawHref?.trim();
  if (!href) {
    return null;
  }
  const lowerHref = href.toLowerCase();
  if (lowerHref.startsWith('/') || lowerHref.startsWith('#')) {
    return href;
  }
  if (
    lowerHref.startsWith('http://') ||
    lowerHref.startsWith('https://') ||
    lowerHref.startsWith('mailto:') ||
    lowerHref.startsWith('tel:')
  ) {
    return href;
  }
  return null;
}

function extractTextFromHtml(value: string, isBrowser: boolean): string {
  if (!isBrowser) {
    return value.replace(/<[^>]+>/g, ' ');
  }
  const parser = new DOMParser();
  const parsed = parser.parseFromString(`<body>${value}</body>`, 'text/html');
  return parsed.body.textContent ?? '';
}

function stripPotentiallyUnsafeHtml(value: string): string {
  return value
    .replace(/<script[\s\S]*?>[\s\S]*?<\/script>/gi, '')
    .replace(/<style[\s\S]*?>[\s\S]*?<\/style>/gi, '');
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}
