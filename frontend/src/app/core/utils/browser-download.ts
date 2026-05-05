export function downloadBlobInBrowser(
  blob: Blob,
  filename: string,
  documentRef?: Document,
): boolean {
  const ownerDocument =
    documentRef ?? (typeof document === 'undefined' ? null : document);
  const fallbackWindow = typeof window === 'undefined' ? null : window;
  const ownerWindow = ownerDocument?.defaultView ?? fallbackWindow;

  if (!ownerDocument || !ownerWindow) {
    return false;
  }

  const url = ownerWindow.URL.createObjectURL(blob);
  const anchor = ownerDocument.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  ownerWindow.URL.revokeObjectURL(url);
  return true;
}
