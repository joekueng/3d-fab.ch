export interface ColorOption {
  label: string;
  value: string;
  hex: string;
  variantId?: number;
  outOfStock?: boolean;
}

export interface ColorCategory {
  name: string; // 'Glossy' | 'Matte'
  colors: ColorOption[];
}

const DEFAULT_BRAND_COLOR = '#facf0a';

/** Order near-white colors first, then hues from red to violet, then dark neutrals. */
export function compareColorHex(
  a: string | null | undefined,
  b: string | null | undefined,
): number {
  const key = (hex: string | null | undefined): [number, number, number] => {
    const match = /^#([\da-f]{6})$/i.exec(String(hex ?? '').trim());
    if (!match) return [3, 0, 0];
    const value = parseInt(match[1], 16);
    const r = ((value >> 16) & 255) / 255;
    const g = ((value >> 8) & 255) / 255;
    const b = (value & 255) / 255;
    const max = Math.max(r, g, b);
    const min = Math.min(r, g, b);
    const delta = max - min;
    const lightness = (max + min) / 2;
    const saturation =
      delta === 0 ? 0 : delta / (1 - Math.abs(2 * lightness - 1));
    if (saturation < 0.15) {
      return [lightness >= 0.8 ? 0 : 2, -lightness, 0];
    }
    const hue =
      max === r
        ? ((g - b) / delta + 6) % 6
        : max === g
          ? (b - r) / delta + 2
          : (r - g) / delta + 4;
    return [1, hue, -lightness];
  };
  const left = key(a);
  const right = key(b);
  return left[0] - right[0] || left[1] - right[1] || left[2] - right[2];
}

export const PRODUCT_COLORS: ColorCategory[] = [
  {
    name: 'COLOR.CATEGORY_GLOSSY',
    colors: [
      { label: 'COLOR.NAME.BLACK', value: 'Black', hex: '#1a1a1a' }, // Not pure black for visibility
      { label: 'COLOR.NAME.WHITE', value: 'White', hex: '#f5f5f5' },
      {
        label: 'COLOR.NAME.RED',
        value: 'Red',
        hex: '#d32f2f',
        outOfStock: true,
      },
      { label: 'COLOR.NAME.BLUE', value: 'Blue', hex: '#1976d2' },
      { label: 'COLOR.NAME.GREEN', value: 'Green', hex: '#388e3c' },
      { label: 'COLOR.NAME.YELLOW', value: 'Yellow', hex: '#fbc02d' },
    ],
  },
  {
    name: 'COLOR.CATEGORY_MATTE',
    colors: [
      { label: 'COLOR.NAME.MATTE_BLACK', value: 'Matte Black', hex: '#2c2c2c' }, // Lighter charcoal for matte
      { label: 'COLOR.NAME.MATTE_WHITE', value: 'Matte White', hex: '#e0e0e0' },
      { label: 'COLOR.NAME.MATTE_GRAY', value: 'Matte Gray', hex: '#757575' },
    ],
  },
];

export function normalizeColorValue(value: string | null | undefined): string {
  return String(value ?? '')
    .trim()
    .toLowerCase()
    .replace(/ß/g, 'ss')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[_-]+/g, ' ')
    .replace(/\s+/g, ' ');
}

export function findColorHex(value: string | null | undefined): string | null {
  const normalized = normalizeColorValue(value);
  if (!normalized) {
    return null;
  }

  for (const category of PRODUCT_COLORS) {
    const match = category.colors.find(
      (color) => normalizeColorValue(color.value) === normalized,
    );
    if (match) {
      return match.hex;
    }
  }

  return null;
}

export interface LocalizedColorLabelSet {
  fallback?: string | null;
  it?: string | null;
  en?: string | null;
  de?: string | null;
  fr?: string | null;
}

export function resolveLocalizedColorLabel(
  language: string | null | undefined,
  labels: LocalizedColorLabelSet,
): string | null {
  const normalizedLanguage = String(language ?? '')
    .trim()
    .toLowerCase()
    .split('-')[0];

  const preferred =
    normalizedLanguage === 'it'
      ? labels.it
      : normalizedLanguage === 'en'
        ? labels.en
        : normalizedLanguage === 'de'
          ? labels.de
          : normalizedLanguage === 'fr'
            ? labels.fr
            : null;

  return (
    firstNonBlank(preferred, labels.fallback) ??
    firstNonBlank(labels.it, labels.en, labels.de, labels.fr)
  );
}

function firstNonBlank(
  ...values: Array<string | null | undefined>
): string | null {
  for (const value of values) {
    const normalized = String(value ?? '').trim();
    if (normalized) {
      return normalized;
    }
  }
  return null;
}

export function getColorHex(value: string): string {
  return findColorHex(value) ?? DEFAULT_BRAND_COLOR;
}

/** Shared finish terminology for the calculator and public sample catalogue. */
export function filamentFinishLabelKey(finishType: string): string {
  const keys: Readonly<Record<string, string>> = {
    glossy: 'COLOR.CATEGORY_GLOSSY',
    matte: 'COLOR.CATEGORY_MATTE',
    marble: 'COLOR.CATEGORY_MARBLE',
    silk: 'COLOR.CATEGORY_SILK',
    translucent: 'COLOR.CATEGORY_TRANSLUCENT',
    special: 'COLOR.CATEGORY_SPECIAL',
  };
  return (
    keys[
      String(finishType ?? '')
        .trim()
        .toLowerCase()
    ] ?? 'COLOR.CATEGORY_OTHER'
  );
}
