import { inject, Injectable, Injector } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { HttpClient, HttpParams } from '@angular/common/http';
import {
  Observable,
  combineLatest,
  map,
  of,
  catchError,
  distinctUntilChanged,
  switchMap,
} from 'rxjs';
import { environment } from '../../../environments/environment';
import { LanguageService } from './language.service';

export type PublicMediaUsageType = string;
export type PublicMediaPreset = 'thumb' | 'card' | 'hero';

export interface PublicMediaVariantDto {
  avifUrl: string | null;
  webpUrl: string | null;
  jpegUrl: string | null;
  pngUrl: string | null;
}

export interface PublicMediaUsageDto {
  mediaAssetId: string;
  title: string | null;
  altText: string | null;
  usageType: string;
  usageKey: string;
  sortOrder: number;
  isPrimary: boolean;
  thumb: PublicMediaVariantDto | null;
  card: PublicMediaVariantDto | null;
  hero: PublicMediaVariantDto | null;
}

export interface PublicMediaSourceSet {
  preset: PublicMediaPreset;
  avifUrl: string | null;
  webpUrl: string | null;
  jpegUrl: string | null;
  pngUrl: string | null;
  fallbackUrl: string | null;
}

export interface PublicMediaResolvedSourceSet
  extends Omit<PublicMediaSourceSet, 'fallbackUrl'> {
  fallbackUrl: string;
}

export interface PublicMediaImage {
  mediaAssetId: string;
  title: string | null;
  altText: string | null;
  usageType: string;
  usageKey: string;
  sortOrder: number;
  isPrimary: boolean;
  thumb: PublicMediaSourceSet;
  card: PublicMediaSourceSet;
  hero: PublicMediaSourceSet;
}

export interface PublicMediaDisplayImage
  extends Omit<PublicMediaImage, 'thumb' | 'card' | 'hero'> {
  source: PublicMediaResolvedSourceSet;
}

export interface PublicMediaUsageRequest {
  usageType: PublicMediaUsageType;
  usageKey: string;
}

export type PublicMediaUsageCollectionMap = Record<
  string,
  readonly PublicMediaImage[]
>;

export function buildPublicMediaUsageScopeKey(
  usageType: string,
  usageKey: string,
): string {
  return `${usageType}::${usageKey}`;
}

@Injectable({
  providedIn: 'root',
})
export class PublicMediaService {
  private readonly http = inject(HttpClient);
  private readonly injector = inject(Injector);
  private readonly languageService = inject(LanguageService);
  private readonly baseUrl = `${environment.apiUrl}/api/public/media`;
  private readonly selectedLang$ = toObservable(
    this.languageService.currentLang,
    {
      injector: this.injector,
    },
  ).pipe(distinctUntilChanged());

  getUsageMedia(
    usageType: PublicMediaUsageType,
    usageKey: string,
  ): Observable<readonly PublicMediaImage[]> {
    return this.selectedLang$.pipe(
      switchMap((lang) => {
        const params = new HttpParams()
          .set('usageType', usageType)
          .set('usageKey', usageKey)
          .set('lang', lang);

        return this.http
          .get<PublicMediaUsageDto[]>(`${this.baseUrl}/usages`, { params })
          .pipe(
            map((items) =>
              items
                .map((item) => this.mapUsageDto(item))
                .filter((item) => this.hasAnyFallback(item)),
            ),
            catchError(() => of([])),
          );
      }),
    );
  }

  getUsageCollections(
    requests: readonly PublicMediaUsageRequest[],
  ): Observable<PublicMediaUsageCollectionMap> {
    if (requests.length === 0) {
      return of({});
    }

    return combineLatest(
      requests.map((request) =>
        this.getUsageMedia(request.usageType, request.usageKey).pipe(
          map(
            (items) =>
              [
                buildPublicMediaUsageScopeKey(
                  request.usageType,
                  request.usageKey,
                ),
                items,
              ] as const,
          ),
        ),
      ),
    ).pipe(
      map((entries) =>
        entries.reduce<PublicMediaUsageCollectionMap>((acc, [key, value]) => {
          acc[key] = value;
          return acc;
        }, {}),
      ),
    );
  }

  pickPrimaryUsage(
    items: readonly PublicMediaImage[],
  ): PublicMediaImage | null {
    if (items.length === 0) {
      return null;
    }
    return items.find((item) => item.isPrimary) ?? items[0] ?? null;
  }

  toDisplayImage(
    item: PublicMediaImage,
    preferredPreset: PublicMediaPreset,
  ): PublicMediaDisplayImage | null {
    const source = this.pickPresetSource(item, preferredPreset);
    if (!source) {
      return null;
    }

    return {
      mediaAssetId: item.mediaAssetId,
      title: item.title,
      altText: item.altText,
      usageType: item.usageType,
      usageKey: item.usageKey,
      sortOrder: item.sortOrder,
      isPrimary: item.isPrimary,
      source,
    };
  }

  private mapUsageDto(item: PublicMediaUsageDto): PublicMediaImage {
    return {
      mediaAssetId: item.mediaAssetId,
      title: item.title ?? null,
      altText: item.altText ?? null,
      usageType: item.usageType,
      usageKey: item.usageKey,
      sortOrder: item.sortOrder,
      isPrimary: item.isPrimary,
      thumb: this.mapPreset(item.thumb, 'thumb'),
      card: this.mapPreset(item.card, 'card'),
      hero: this.mapPreset(item.hero, 'hero'),
    };
  }

  private mapPreset(
    preset: PublicMediaVariantDto | null | undefined,
    presetName: PublicMediaPreset,
  ): PublicMediaSourceSet {
    const avifUrl = this.normalizeUrl(preset?.avifUrl);
    const webpUrl = this.normalizeUrl(preset?.webpUrl);
    const jpegUrl = this.normalizeUrl(preset?.jpegUrl);
    const pngUrl = this.normalizeUrl(preset?.pngUrl);

    return {
      preset: presetName,
      avifUrl,
      webpUrl,
      jpegUrl,
      pngUrl,
      fallbackUrl: jpegUrl ?? webpUrl ?? avifUrl ?? pngUrl,
    };
  }

  private pickPresetSource(
    item: PublicMediaImage,
    preferredPreset: PublicMediaPreset,
  ): PublicMediaResolvedSourceSet | null {
    const presetOrder = this.buildPresetFallbackOrder(preferredPreset);
    const source = presetOrder
      .map((preset) => item[preset])
      .find((sourceSet) => sourceSet.fallbackUrl !== null);

    if (!source || source.fallbackUrl === null) {
      return null;
    }

    return {
      preset: source.preset,
      avifUrl: source.avifUrl,
      webpUrl: source.webpUrl,
      jpegUrl: source.jpegUrl,
      pngUrl: source.pngUrl,
      fallbackUrl: source.fallbackUrl,
    };
  }

  private buildPresetFallbackOrder(
    preferredPreset: PublicMediaPreset,
  ): readonly PublicMediaPreset[] {
    switch (preferredPreset) {
      case 'thumb':
        return ['thumb', 'card', 'hero'];
      case 'card':
        return ['card', 'thumb', 'hero'];
      case 'hero':
        return ['hero', 'card', 'thumb'];
    }
  }

  private hasAnyFallback(item: PublicMediaImage): boolean {
    return [item.thumb, item.card, item.hero].some(
      (preset) => preset.fallbackUrl !== null,
    );
  }

  private normalizeUrl(value: string | null | undefined): string | null {
    return value && value.trim() ? value : null;
  }
}
