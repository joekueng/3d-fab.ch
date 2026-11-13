export interface EasyModePreset {
  quality: string;
  nozzleDiameter: number;
  layerHeight: number;
  infillDensity: number;
  infillPattern: string;
}

export function easyModePresetForQuality(
  qualityRaw: string,
  isTechnical = false,
  materialCode: unknown = '',
  preferredNozzle?: number,
): EasyModePreset {
  const quality = normalizeQualityValue(qualityRaw);

  if (isTpu85aMaterial(materialCode)) {
    return {
      quality,
      nozzleDiameter: preferredNozzle === 0.8 ? 0.8 : 0.6,
      layerHeight: 0.24,
      infillDensity: quality === 'extra_fine' ? 20 : 15,
      infillPattern: quality === 'extra_fine' ? 'gyroid' : 'grid',
    };
  }

  if (quality === 'draft') {
    return {
      quality: 'draft',
      nozzleDiameter: 0.4,
      layerHeight: isTechnical ? 0.24 : 0.28,
      infillDensity: 15,
      infillPattern: 'grid',
    };
  }

  if (quality === 'extra_fine') {
    return {
      quality: 'extra_fine',
      nozzleDiameter: 0.4,
      layerHeight: isTechnical ? 0.16 : 0.12,
      infillDensity: 20,
      infillPattern: 'gyroid',
    };
  }

  return {
    quality: 'standard',
    nozzleDiameter: 0.4,
    layerHeight: 0.2,
    infillDensity: 15,
    infillPattern: 'grid',
  };
}

export function isTpu85aMaterial(value: unknown): boolean {
  return (
    String(value || '')
      .toUpperCase()
      .replace(/[\s_-]/g, '') === 'TPU85A'
  );
}

export function normalizeQualityValue(value: unknown): string {
  const normalized = String(value || 'standard')
    .trim()
    .toLowerCase();
  if (normalized === 'high' || normalized === 'high_definition') {
    return 'extra_fine';
  }
  return normalized || 'standard';
}
