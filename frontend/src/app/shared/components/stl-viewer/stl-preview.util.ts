export const DEFAULT_MAX_STL_PREVIEW_BYTES = 75 * 1024 * 1024;
export const DEFAULT_MAX_STL_PREVIEW_TRIANGLES = 1_500_000;

export type StlPreviewValidationReason =
  | 'empty'
  | 'too_large'
  | 'too_many_triangles'
  | 'unsupported_payload';

export type StlPreviewValidationResult =
  | { ok: true; format: 'ascii' | 'binary' }
  | { ok: false; reason: StlPreviewValidationReason };

export type StlPreviewValidationOptions = {
  maxBytes?: number;
  maxTriangles?: number;
};

export function validateStlPreviewBuffer(
  buffer: ArrayBuffer,
  options: StlPreviewValidationOptions = {},
): StlPreviewValidationResult {
  const maxBytes = options.maxBytes ?? DEFAULT_MAX_STL_PREVIEW_BYTES;
  const maxTriangles =
    options.maxTriangles ?? DEFAULT_MAX_STL_PREVIEW_TRIANGLES;
  const byteLength = buffer.byteLength;

  if (byteLength <= 0) {
    return { ok: false, reason: 'empty' };
  }

  if (byteLength > maxBytes) {
    return { ok: false, reason: 'too_large' };
  }

  if (looksLikeBinaryStl(buffer)) {
    const faceCount = new DataView(buffer).getUint32(80, true);
    if (faceCount > maxTriangles) {
      return { ok: false, reason: 'too_many_triangles' };
    }
    return { ok: true, format: 'binary' };
  }

  if (looksLikeAsciiStl(buffer)) {
    return { ok: true, format: 'ascii' };
  }

  return { ok: false, reason: 'unsupported_payload' };
}

function looksLikeBinaryStl(buffer: ArrayBuffer): boolean {
  if (buffer.byteLength < 84) {
    return false;
  }

  const view = new DataView(buffer);
  const faceCount = view.getUint32(80, true);
  const expectedSize = 84 + faceCount * 50;

  return expectedSize === buffer.byteLength;
}

function looksLikeAsciiStl(buffer: ArrayBuffer): boolean {
  const sampleBytes = new Uint8Array(
    buffer,
    0,
    Math.min(buffer.byteLength, 2048),
  );
  if (sampleBytes.length === 0) {
    return false;
  }

  let printable = 0;
  for (const value of sampleBytes) {
    const isWhitespace =
      value === 9 || value === 10 || value === 13 || value === 32;
    const isPrintableAscii = value >= 32 && value <= 126;
    if (isWhitespace || isPrintableAscii) {
      printable += 1;
    }
  }

  if (printable / sampleBytes.length < 0.98) {
    return false;
  }

  const sample = new TextDecoder().decode(sampleBytes).replace(/\0/g, '');
  const normalized = sample.trimStart().toLowerCase();

  return normalized.startsWith('solid') && normalized.includes('facet');
}
