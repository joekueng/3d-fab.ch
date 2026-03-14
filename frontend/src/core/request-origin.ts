export type RequestLike = {
  protocol?: string;
  get?: (name: string) => string | undefined;
  headers?: Record<string, string | string[] | undefined>;
};

function firstHeaderValue(
  value: string | string[] | undefined,
): string | null {
  if (Array.isArray(value)) {
    return value[0] ?? null;
  }
  return typeof value === 'string' ? value : null;
}

function firstForwardedValue(
  value: string | string[] | undefined,
): string | null {
  const raw = firstHeaderValue(value);
  if (!raw) {
    return null;
  }

  return (
    raw
      .split(',')
      .map((part) => part.trim())
      .find(Boolean) ?? null
  );
}

export function resolveRequestOrigin(request: RequestLike | null): string | null {
  if (!request) {
    return null;
  }

  const host =
    firstForwardedValue(request.headers?.['x-forwarded-host']) ??
    request.get?.('host') ??
    firstHeaderValue(request.headers?.['host']);
  if (!host) {
    return null;
  }

  const forwardedProto = firstForwardedValue(
    request.headers?.['x-forwarded-proto'],
  )?.toLowerCase();
  const protocol = forwardedProto || request.protocol || 'http';
  return `${protocol}://${host}`;
}
