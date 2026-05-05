import { HomeProject } from '../../core/services/home-project.service';
import {
  DEFAULT_HOME_PROJECT_GLOW,
} from './home-page.config';
import { HomeProjectGlow, Rgb } from './home-page.types';

type HomeProjectImage = HomeProject['image'];
type ColorBucket = {
  count: number;
  color: Rgb;
  saturation: number;
  lightness: number;
};

export function mediaFallbackUrl(image: HomeProjectImage): string | null {
  return (
    image?.hero?.jpegUrl ??
    image?.hero?.webpUrl ??
    image?.hero?.avifUrl ??
    image?.card?.jpegUrl ??
    image?.card?.webpUrl ??
    image?.card?.avifUrl ??
    image?.thumb?.jpegUrl ??
    image?.thumb?.webpUrl ??
    image?.thumb?.avifUrl ??
    null
  );
}

export function mediaAvifUrl(image: HomeProjectImage): string | null {
  return image?.hero?.avifUrl ?? image?.card?.avifUrl ?? null;
}

export function mediaWebpUrl(image: HomeProjectImage): string | null {
  return image?.hero?.webpUrl ?? image?.card?.webpUrl ?? null;
}

export function extractHomeProjectGlow(
  image: HTMLImageElement,
): HomeProjectGlow | null {
  const width = image.naturalWidth;
  const height = image.naturalHeight;
  if (width <= 0 || height <= 0) {
    return null;
  }

  const sampleSize = 112;
  const canvas = image.ownerDocument.createElement('canvas');
  canvas.width = sampleSize;
  canvas.height = sampleSize;
  const context = canvas.getContext('2d', { willReadFrequently: true });
  if (!context) {
    return null;
  }

  try {
    context.drawImage(image, 0, 0, sampleSize, sampleSize);
    const imageData = context.getImageData(0, 0, sampleSize, sampleSize).data;
    const globalBuckets = collectColorBuckets(
      imageData,
      sampleSize,
      0,
      0,
      1,
      1,
      3,
    );

    return {
      top:
        pickRegionColor(
          imageData,
          sampleSize,
          0.12,
          0.02,
          0.88,
          0.38,
          globalBuckets,
        ) ??
        DEFAULT_HOME_PROJECT_GLOW.top,
      right:
        pickRegionColor(
          imageData,
          sampleSize,
          0.58,
          0.16,
          0.98,
          0.84,
          globalBuckets,
        ) ??
        DEFAULT_HOME_PROJECT_GLOW.right,
      bottom:
        pickRegionColor(
          imageData,
          sampleSize,
          0.12,
          0.62,
          0.88,
          0.98,
          globalBuckets,
        ) ??
        DEFAULT_HOME_PROJECT_GLOW.bottom,
      left:
        pickRegionColor(
          imageData,
          sampleSize,
          0.02,
          0.16,
          0.42,
          0.84,
          globalBuckets,
        ) ??
        DEFAULT_HOME_PROJECT_GLOW.left,
      topLeft:
        pickRegionColor(
          imageData,
          sampleSize,
          0.02,
          0.02,
          0.42,
          0.42,
          globalBuckets,
        ) ??
        DEFAULT_HOME_PROJECT_GLOW.topLeft,
      topRight:
        pickRegionColor(
          imageData,
          sampleSize,
          0.58,
          0.02,
          0.98,
          0.42,
          globalBuckets,
        ) ??
        DEFAULT_HOME_PROJECT_GLOW.topRight,
      bottomRight:
        pickRegionColor(
          imageData,
          sampleSize,
          0.58,
          0.58,
          0.98,
          0.98,
          globalBuckets,
        ) ??
        DEFAULT_HOME_PROJECT_GLOW.bottomRight,
      bottomLeft:
        pickRegionColor(
          imageData,
          sampleSize,
          0.02,
          0.58,
          0.42,
          0.98,
          globalBuckets,
        ) ??
        DEFAULT_HOME_PROJECT_GLOW.bottomLeft,
    };
  } catch {
    return null;
  }
}

function pickRegionColor(
  imageData: Uint8ClampedArray,
  size: number,
  xStartRatio: number,
  yStartRatio: number,
  xEndRatio: number,
  yEndRatio: number,
  globalBuckets: Map<string, ColorBucket>,
): string | null {
  const buckets = collectColorBuckets(
    imageData,
    size,
    xStartRatio,
    yStartRatio,
    xEndRatio,
    yEndRatio,
    2,
  );
  const local = pickRelevantBucket(buckets);
  const global = pickRelevantBucket(globalBuckets);

  if (!local) {
    return global ? bucketToCssColor(global) : null;
  }
  if (
    global &&
    local.saturation < 0.18 &&
    global.saturation > local.saturation + 0.12
  ) {
    return bucketToCssColor(global);
  }
  return bucketToCssColor(local);
}

function collectColorBuckets(
  imageData: Uint8ClampedArray,
  size: number,
  xStartRatio: number,
  yStartRatio: number,
  xEndRatio: number,
  yEndRatio: number,
  step: number,
): Map<string, ColorBucket> {
  const buckets = new Map<string, ColorBucket>();
  const xStart = Math.round(xStartRatio * (size - 1));
  const yStart = Math.round(yStartRatio * (size - 1));
  const xEnd = Math.round(xEndRatio * (size - 1));
  const yEnd = Math.round(yEndRatio * (size - 1));

  for (let y = yStart; y <= yEnd; y += step) {
    for (let x = xStart; x <= xEnd; x += step) {
      const offset = (y * size + x) * 4;
      const alpha = imageData[offset + 3] ?? 0;
      if (alpha < 220) {
        continue;
      }
      addDominantColorBucket(buckets, {
        r: imageData[offset] ?? 0,
        g: imageData[offset + 1] ?? 0,
        b: imageData[offset + 2] ?? 0,
      });
    }
  }

  return buckets;
}

function addDominantColorBucket(
  buckets: Map<string, ColorBucket>,
  color: Rgb,
): void {
  const quantized = {
    r: Math.round(color.r / 20) * 20,
    g: Math.round(color.g / 20) * 20,
    b: Math.round(color.b / 20) * 20,
  };
  const hsl = rgbToHsl(quantized);

  if (hsl.lightness < 0.08 || hsl.lightness > 0.94) {
    return;
  }

  const key = `${quantized.r},${quantized.g},${quantized.b}`;
  const bucket = buckets.get(key);
  if (bucket) {
    bucket.count += 1;
    return;
  }

  buckets.set(key, {
    count: 1,
    color: quantized,
    saturation: hsl.saturation,
    lightness: hsl.lightness,
  });
}

function pickRelevantBucket(buckets: Map<string, ColorBucket>): ColorBucket | null {
  let best: ColorBucket | null = null;
  let bestScore = -Infinity;

  for (const bucket of buckets.values()) {
    const frequencyWeight = Math.pow(bucket.count, 0.72);
    const saturationWeight = 0.3 + bucket.saturation * 1.9;
    const lightnessDistance = Math.abs(bucket.lightness - 0.52);
    const lightnessWeight = 1 - lightnessDistance * 0.34;
    const colorfulnessWeight = bucket.saturation < 0.1 ? 0.42 : 1;
    const score =
      frequencyWeight *
      saturationWeight *
      lightnessWeight *
      colorfulnessWeight;
    if (score > bestScore) {
      best = bucket;
      bestScore = score;
    }
  }

  return best;
}

function bucketToCssColor(bucket: ColorBucket): string {
  return `rgb(${bucket.color.r} ${bucket.color.g} ${bucket.color.b})`;
}

function rgbToHsl(color: Rgb): { saturation: number; lightness: number } {
  const r = color.r / 255;
  const g = color.g / 255;
  const b = color.b / 255;
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const lightness = (max + min) / 2;

  if (max === min) {
    return { saturation: 0, lightness };
  }

  const delta = max - min;
  const saturation =
    lightness > 0.5 ? delta / (2 - max - min) : delta / (max + min);
  return { saturation, lightness };
}
