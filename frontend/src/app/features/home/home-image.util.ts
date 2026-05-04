import { HomeProject } from '../../core/services/home-project.service';
import {
  DEFAULT_HOME_PROJECT_GLOW,
} from './home-page.config';
import { HomeProjectGlow, Rgb } from './home-page.types';

type HomeProjectImage = HomeProject['image'];

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

  const sampleSize = 96;
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
    return {
      top:
        pickEdgeColor(imageData, sampleSize, 0.24, 0.04, 0.76, 0.2) ??
        DEFAULT_HOME_PROJECT_GLOW.top,
      right:
        pickEdgeColor(imageData, sampleSize, 0.8, 0.24, 0.96, 0.76) ??
        DEFAULT_HOME_PROJECT_GLOW.right,
      bottom:
        pickEdgeColor(imageData, sampleSize, 0.24, 0.8, 0.76, 0.96) ??
        DEFAULT_HOME_PROJECT_GLOW.bottom,
      left:
        pickEdgeColor(imageData, sampleSize, 0.04, 0.24, 0.2, 0.76) ??
        DEFAULT_HOME_PROJECT_GLOW.left,
      topLeft:
        pickEdgeColor(imageData, sampleSize, 0.04, 0.04, 0.28, 0.26) ??
        DEFAULT_HOME_PROJECT_GLOW.topLeft,
      topRight:
        pickEdgeColor(imageData, sampleSize, 0.72, 0.04, 0.96, 0.26) ??
        DEFAULT_HOME_PROJECT_GLOW.topRight,
      bottomRight:
        pickEdgeColor(imageData, sampleSize, 0.72, 0.74, 0.96, 0.96) ??
        DEFAULT_HOME_PROJECT_GLOW.bottomRight,
      bottomLeft:
        pickEdgeColor(imageData, sampleSize, 0.04, 0.74, 0.28, 0.96) ??
        DEFAULT_HOME_PROJECT_GLOW.bottomLeft,
    };
  } catch {
    return null;
  }
}

function pickEdgeColor(
  imageData: Uint8ClampedArray,
  size: number,
  xStartRatio: number,
  yStartRatio: number,
  xEndRatio: number,
  yEndRatio: number,
): string | null {
  const buckets = new Map<
    string,
    { count: number; color: Rgb; saturation: number; lightness: number }
  >();
  const xStart = Math.round(xStartRatio * (size - 1));
  const yStart = Math.round(yStartRatio * (size - 1));
  const xEnd = Math.round(xEndRatio * (size - 1));
  const yEnd = Math.round(yEndRatio * (size - 1));

  for (let y = yStart; y <= yEnd; y += 2) {
    for (let x = xStart; x <= xEnd; x += 2) {
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

  return pickRelevantColor(buckets);
}

function addDominantColorBucket(
  buckets: Map<
    string,
    { count: number; color: Rgb; saturation: number; lightness: number }
  >,
  color: Rgb,
): void {
  const quantized = {
    r: Math.round(color.r / 24) * 24,
    g: Math.round(color.g / 24) * 24,
    b: Math.round(color.b / 24) * 24,
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

function pickRelevantColor(
  buckets: Map<
    string,
    { count: number; color: Rgb; saturation: number; lightness: number }
  >,
): string | null {
  let best:
    | { count: number; color: Rgb; saturation: number; lightness: number }
    | null = null;
  let bestScore = -Infinity;

  for (const bucket of buckets.values()) {
    const saturationWeight = 0.72 + bucket.saturation * 0.38;
    const lightnessDistance = Math.abs(bucket.lightness - 0.52);
    const lightnessWeight = 1 - lightnessDistance * 0.28;
    const score = bucket.count * saturationWeight * lightnessWeight;
    if (score > bestScore) {
      best = bucket;
      bestScore = score;
    }
  }

  if (!best) {
    return null;
  }

  return `rgb(${best.color.r} ${best.color.g} ${best.color.b})`;
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
