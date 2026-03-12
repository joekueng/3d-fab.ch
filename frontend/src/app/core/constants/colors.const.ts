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

const COLOR_HEX_BY_TRANSLATION_KEY: Record<string, string> = {
  ...Object.fromEntries(
    PRODUCT_COLORS.flatMap((category) =>
      category.colors.map((color) => [color.label, color.hex] as const),
    ),
  ),
  'COLOR.NAME.ORANGE': '#f5a623',
  'COLOR.NAME.GRAY': '#b7b7b7',
  'COLOR.NAME.LIGHT_GRAY': '#d8dadd',
  'COLOR.NAME.DARK_GRAY': '#4f4f4f',
  'COLOR.NAME.PURPLE': '#7b1fa2',
  'COLOR.NAME.BEIGE': '#d4c09a',
  'COLOR.NAME.SAND_BEIGE': '#d7c2a0',
};

const COLOR_TRANSLATION_KEY_BY_VALUE: Record<string, string> = {
  black: 'COLOR.NAME.BLACK',
  nero: 'COLOR.NAME.BLACK',
  noir: 'COLOR.NAME.BLACK',
  schwarz: 'COLOR.NAME.BLACK',
  white: 'COLOR.NAME.WHITE',
  bianco: 'COLOR.NAME.WHITE',
  blanc: 'COLOR.NAME.WHITE',
  weiss: 'COLOR.NAME.WHITE',
  red: 'COLOR.NAME.RED',
  rosso: 'COLOR.NAME.RED',
  rouge: 'COLOR.NAME.RED',
  rot: 'COLOR.NAME.RED',
  blue: 'COLOR.NAME.BLUE',
  blu: 'COLOR.NAME.BLUE',
  bleu: 'COLOR.NAME.BLUE',
  blau: 'COLOR.NAME.BLUE',
  green: 'COLOR.NAME.GREEN',
  verde: 'COLOR.NAME.GREEN',
  vert: 'COLOR.NAME.GREEN',
  grun: 'COLOR.NAME.GREEN',
  yellow: 'COLOR.NAME.YELLOW',
  giallo: 'COLOR.NAME.YELLOW',
  jaune: 'COLOR.NAME.YELLOW',
  gelb: 'COLOR.NAME.YELLOW',
  orange: 'COLOR.NAME.ORANGE',
  arancione: 'COLOR.NAME.ORANGE',
  naranja: 'COLOR.NAME.ORANGE',
  gris: 'COLOR.NAME.GRAY',
  gray: 'COLOR.NAME.GRAY',
  grey: 'COLOR.NAME.GRAY',
  grigio: 'COLOR.NAME.GRAY',
  grau: 'COLOR.NAME.GRAY',
  'light gray': 'COLOR.NAME.LIGHT_GRAY',
  'light grey': 'COLOR.NAME.LIGHT_GRAY',
  'grigio chiaro': 'COLOR.NAME.LIGHT_GRAY',
  'gris clair': 'COLOR.NAME.LIGHT_GRAY',
  hellgrau: 'COLOR.NAME.LIGHT_GRAY',
  'dark gray': 'COLOR.NAME.DARK_GRAY',
  'dark grey': 'COLOR.NAME.DARK_GRAY',
  'grigio scuro': 'COLOR.NAME.DARK_GRAY',
  'gris fonce': 'COLOR.NAME.DARK_GRAY',
  dunkelgrau: 'COLOR.NAME.DARK_GRAY',
  purple: 'COLOR.NAME.PURPLE',
  violet: 'COLOR.NAME.PURPLE',
  viola: 'COLOR.NAME.PURPLE',
  lila: 'COLOR.NAME.PURPLE',
  beige: 'COLOR.NAME.BEIGE',
  'sand beige': 'COLOR.NAME.SAND_BEIGE',
  'beige sabbia': 'COLOR.NAME.SAND_BEIGE',
  'beige sable': 'COLOR.NAME.SAND_BEIGE',
  sandbeige: 'COLOR.NAME.SAND_BEIGE',
  'matte black': 'COLOR.NAME.MATTE_BLACK',
  'black matte': 'COLOR.NAME.MATTE_BLACK',
  'nero opaco': 'COLOR.NAME.MATTE_BLACK',
  'noir mat': 'COLOR.NAME.MATTE_BLACK',
  'matt schwarz': 'COLOR.NAME.MATTE_BLACK',
  'schwarz matt': 'COLOR.NAME.MATTE_BLACK',
  'matte white': 'COLOR.NAME.MATTE_WHITE',
  'white matte': 'COLOR.NAME.MATTE_WHITE',
  'bianco opaco': 'COLOR.NAME.MATTE_WHITE',
  'blanc mat': 'COLOR.NAME.MATTE_WHITE',
  'matt weiss': 'COLOR.NAME.MATTE_WHITE',
  'weiss matt': 'COLOR.NAME.MATTE_WHITE',
  'matte gray': 'COLOR.NAME.MATTE_GRAY',
  'matte grey': 'COLOR.NAME.MATTE_GRAY',
  'grigio opaco': 'COLOR.NAME.MATTE_GRAY',
  'gris mat': 'COLOR.NAME.MATTE_GRAY',
  'matt grau': 'COLOR.NAME.MATTE_GRAY',
  'grau matt': 'COLOR.NAME.MATTE_GRAY',
};

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

export function getColorTranslationKey(
  value: string | null | undefined,
): string | null {
  const normalized = normalizeColorValue(value);
  return normalized ? COLOR_TRANSLATION_KEY_BY_VALUE[normalized] ?? null : null;
}

export function getColorLabelToken(
  value: string | null | undefined,
): string | null {
  const raw = String(value ?? '').trim();
  if (!raw) {
    return null;
  }

  return getColorTranslationKey(raw) ?? raw;
}

export function findColorHex(value: string | null | undefined): string | null {
  const translationKey = getColorTranslationKey(value);
  if (translationKey) {
    return COLOR_HEX_BY_TRANSLATION_KEY[translationKey] ?? null;
  }

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

export function getColorHex(value: string): string {
  return findColorHex(value) ?? DEFAULT_BRAND_COLOR;
}
