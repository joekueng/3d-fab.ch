import { HomeProject } from '../../core/services/home-project.service';

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

export function mediaTransparentFallbackUrl(
  image: HomeProjectImage,
): string | null {
  return (
    image?.hero?.pngUrl ??
    image?.card?.pngUrl ??
    image?.thumb?.pngUrl ??
    image?.hero?.webpUrl ??
    image?.card?.webpUrl ??
    image?.thumb?.webpUrl ??
    image?.hero?.avifUrl ??
    image?.card?.avifUrl ??
    image?.thumb?.avifUrl ??
    image?.hero?.jpegUrl ??
    image?.card?.jpegUrl ??
    image?.thumb?.jpegUrl ??
    null
  );
}

export function mediaAvifUrl(image: HomeProjectImage): string | null {
  return image?.hero?.avifUrl ?? image?.card?.avifUrl ?? null;
}

export function mediaWebpUrl(image: HomeProjectImage): string | null {
  return image?.hero?.webpUrl ?? image?.card?.webpUrl ?? null;
}
