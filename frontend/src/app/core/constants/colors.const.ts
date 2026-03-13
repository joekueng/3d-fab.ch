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
