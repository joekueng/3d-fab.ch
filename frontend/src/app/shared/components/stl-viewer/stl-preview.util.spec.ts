import { validateStlPreviewBuffer } from './stl-preview.util';

describe('validateStlPreviewBuffer', () => {
  it('accepts a valid binary STL payload', () => {
    const buffer = createBinaryStlBuffer(2);

    expect(validateStlPreviewBuffer(buffer)).toEqual({
      ok: true,
      format: 'binary',
    });
  });

  it('rejects a binary STL that exceeds the triangle limit', () => {
    const buffer = createBinaryStlBuffer(2);

    expect(
      validateStlPreviewBuffer(buffer, {
        maxTriangles: 1,
      }),
    ).toEqual({
      ok: false,
      reason: 'too_many_triangles',
    });
  });

  it('accepts an ASCII STL payload', () => {
    const ascii = [
      'solid sample',
      'facet normal 0 0 0',
      'outer loop',
      'vertex 0 0 0',
      'vertex 1 0 0',
      'vertex 0 1 0',
      'endloop',
      'endfacet',
      'endsolid sample',
    ].join('\n');

    expect(
      validateStlPreviewBuffer(toArrayBuffer(new TextEncoder().encode(ascii))),
    ).toEqual({
      ok: true,
      format: 'ascii',
    });
  });

  it('rejects payloads that are too large before parsing', () => {
    const oversized = new ArrayBuffer(16);

    expect(
      validateStlPreviewBuffer(oversized, {
        maxBytes: 8,
      }),
    ).toEqual({
      ok: false,
      reason: 'too_large',
    });
  });

  it('rejects non-STL payloads', () => {
    const html = toArrayBuffer(
      new TextEncoder().encode('<html>not an stl</html>'),
    );

    expect(validateStlPreviewBuffer(html)).toEqual({
      ok: false,
      reason: 'unsupported_payload',
    });
  });
});

function createBinaryStlBuffer(faceCount: number): ArrayBuffer {
  const buffer = new ArrayBuffer(84 + faceCount * 50);
  const view = new DataView(buffer);
  view.setUint32(80, faceCount, true);
  return buffer;
}

function toArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  return bytes.buffer.slice(
    bytes.byteOffset,
    bytes.byteOffset + bytes.byteLength,
  ) as ArrayBuffer;
}
