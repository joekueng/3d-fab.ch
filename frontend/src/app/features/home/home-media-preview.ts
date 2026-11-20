import {
  PublicMediaImage,
  PublicMediaPreset,
  PublicMediaSourceSet,
  PublicMediaUsageCollectionMap,
  PublicMediaUsageDto,
  PublicMediaVariantDto,
} from '../../core/services/public-media.service';
import preview from './home-media-preview.json';

// Public image references captured from the existing site on 2026-11-08.
// Used only by the local frontend preview when its media API has no images.
const snapshots: Record<
  string,
  Record<string, PublicMediaUsageDto[]>
> = preview;
function source(
  variants: PublicMediaVariantDto | null,
  preset: PublicMediaPreset,
): PublicMediaSourceSet {
  return {
    preset,
    avifUrl: variants?.avifUrl ?? null,
    webpUrl: variants?.webpUrl ?? null,
    jpegUrl: variants?.jpegUrl ?? null,
    pngUrl: variants?.pngUrl ?? null,
    fallbackUrl:
      variants?.jpegUrl ??
      variants?.webpUrl ??
      variants?.avifUrl ??
      variants?.pngUrl ??
      null,
  };
}
export function withHomeMediaPreview(
  media: PublicMediaUsageCollectionMap,
  lang: string,
): PublicMediaUsageCollectionMap {
  const result = { ...media };
  for (const [key, items] of Object.entries(
    snapshots[lang] ?? snapshots['en'],
  )) {
    if (result[key]?.length) continue;
    result[key] = items.map(
      (item): PublicMediaImage => ({
        ...item,
        thumb: source(item.thumb, 'thumb'),
        card: source(item.card, 'card'),
        hero: source(item.hero, 'hero'),
      }),
    );
  }
  return result;
}
