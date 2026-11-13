import { FormItem } from './upload-form.types';
export {
  easyModePresetForQuality,
  isTpu85aMaterial,
  normalizeQualityValue,
} from '../../utils/print-settings-presets';

export function sameItemSettings(a: FormItem, b: FormItem): boolean {
  return (
    normalizeText(a.material) === normalizeText(b.material) &&
    normalizeText(a.quality) === normalizeText(b.quality) &&
    Math.abs(
      normalizeNumber(a.nozzleDiameter, 0.4) -
        normalizeNumber(b.nozzleDiameter, 0.4),
    ) < 0.0001 &&
    Math.abs(
      normalizeNumber(a.layerHeight, 0.2) - normalizeNumber(b.layerHeight, 0.2),
    ) < 0.0001 &&
    Math.abs(
      normalizeNumber(a.infillDensity, 20) -
        normalizeNumber(b.infillDensity, 20),
    ) < 0.0001 &&
    normalizeText(a.infillPattern) === normalizeText(b.infillPattern) &&
    Boolean(a.supportEnabled) === Boolean(b.supportEnabled)
  );
}

export function normalizeQuantity(quantity: number): number {
  if (!Number.isFinite(quantity) || quantity < 1) {
    return 1;
  }
  return Math.floor(quantity);
}

export function normalizeNumber(value: unknown, fallback: number): number {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : fallback;
}

export function normalizeText(value: unknown): string {
  return String(value || '')
    .trim()
    .toLowerCase();
}

export function normalizeFileName(fileName: string): string {
  return (fileName || '').split(/[\\/]/).pop()?.trim().toLowerCase() ?? '';
}

export function toNozzleKey(value: unknown): string {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return '0.40';
  }
  return numeric.toFixed(2);
}
