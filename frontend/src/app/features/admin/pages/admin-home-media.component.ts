import { CommonModule } from '@angular/common';
import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { of, switchMap } from 'rxjs';
import {
  AdminCreateMediaUsagePayload,
  AdminMediaAsset,
  AdminMediaService,
  AdminMediaUsage,
} from '../services/admin-media.service';

type HomeSectionKey =
  | 'shop-gallery'
  | 'founders-gallery'
  | 'capability-prototyping'
  | 'capability-custom-parts'
  | 'capability-small-series'
  | 'capability-cad';

interface HomeMediaSectionConfig {
  usageType: 'HOME_SECTION';
  usageKey: HomeSectionKey;
  groupId: 'galleries' | 'capabilities';
  title: string;
  preferredVariantName: 'card' | 'hero';
}

interface HomeMediaFormState {
  file: File | null;
  previewUrl: string | null;
  title: string;
  altText: string;
  sortOrder: number;
  isPrimary: boolean;
  replacingUsageId: string | null;
  saving: boolean;
}

interface HomeMediaItem {
  usageId: string;
  mediaAssetId: string;
  originalFilename: string;
  title: string | null;
  altText: string | null;
  sortOrder: number;
  draftSortOrder: number;
  isPrimary: boolean;
  previewUrl: string | null;
  createdAt: string;
}

interface HomeMediaSectionView extends HomeMediaSectionConfig {
  items: HomeMediaItem[];
}

interface HomeMediaSectionGroup {
  id: HomeMediaSectionConfig['groupId'];
  title: string;
}

@Component({
  selector: 'app-admin-home-media',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-home-media.component.html',
  styleUrl: './admin-home-media.component.scss',
})
export class AdminHomeMediaComponent implements OnInit, OnDestroy {
  private readonly adminMediaService = inject(AdminMediaService);

  readonly sectionGroups: readonly HomeMediaSectionGroup[] = [
    {
      id: 'galleries',
      title: 'Gallery e visual principali',
    },
    {
      id: 'capabilities',
      title: 'Cosa puoi ottenere',
    },
  ];

  readonly sectionConfigs: readonly HomeMediaSectionConfig[] = [
    {
      usageType: 'HOME_SECTION',
      usageKey: 'shop-gallery',
      groupId: 'galleries',
      title: 'Home: gallery shop',
      preferredVariantName: 'card',
    },
    {
      usageType: 'HOME_SECTION',
      usageKey: 'founders-gallery',
      groupId: 'galleries',
      title: 'Home: gallery founders',
      preferredVariantName: 'hero',
    },
    {
      usageType: 'HOME_SECTION',
      usageKey: 'capability-prototyping',
      groupId: 'capabilities',
      title: 'Home: prototipazione veloce',
      preferredVariantName: 'card',
    },
    {
      usageType: 'HOME_SECTION',
      usageKey: 'capability-custom-parts',
      groupId: 'capabilities',
      title: 'Home: pezzi personalizzati',
      preferredVariantName: 'card',
    },
    {
      usageType: 'HOME_SECTION',
      usageKey: 'capability-small-series',
      groupId: 'capabilities',
      title: 'Home: piccole serie',
      preferredVariantName: 'card',
    },
    {
      usageType: 'HOME_SECTION',
      usageKey: 'capability-cad',
      groupId: 'capabilities',
      title: 'Home: consulenza e CAD',
      preferredVariantName: 'card',
    },
  ];

  sections: HomeMediaSectionView[] = [];
  loading = false;
  errorMessage: string | null = null;
  successMessage: string | null = null;
  actingUsageIds = new Set<string>();

  private readonly formStateByKey: Record<HomeSectionKey, HomeMediaFormState> =
    {
      'shop-gallery': this.createEmptyFormState(),
      'founders-gallery': this.createEmptyFormState(),
      'capability-prototyping': this.createEmptyFormState(),
      'capability-custom-parts': this.createEmptyFormState(),
      'capability-small-series': this.createEmptyFormState(),
      'capability-cad': this.createEmptyFormState(),
    };

  get configuredSectionCount(): number {
    return this.sectionConfigs.length;
  }

  get activeImageCount(): number {
    return this.sections.reduce(
      (total, section) => total + section.items.length,
      0,
    );
  }

  ngOnInit(): void {
    this.loadHomeMedia();
  }

  ngOnDestroy(): void {
    (Object.keys(this.formStateByKey) as HomeSectionKey[]).forEach((key) => {
      this.revokePreviewUrl(this.formStateByKey[key].previewUrl);
    });
  }

  loadHomeMedia(): void {
    this.loading = true;
    this.errorMessage = null;
    this.successMessage = null;

    this.adminMediaService.listAssets().subscribe({
      next: (assets) => {
        this.sections = this.sectionConfigs.map((config) => ({
          ...config,
          items: this.buildSectionItems(assets, config),
        }));
        this.loading = false;
        (Object.keys(this.formStateByKey) as HomeSectionKey[]).forEach(
          (key) => {
            if (!this.formStateByKey[key].saving) {
              this.resetForm(key);
            }
          },
        );
      },
      error: (error) => {
        this.loading = false;
        this.errorMessage = this.extractErrorMessage(
          error,
          'Impossibile caricare i media della home.',
        );
      },
    });
  }

  getFormState(sectionKey: HomeSectionKey): HomeMediaFormState {
    return this.formStateByKey[sectionKey];
  }

  onFileSelected(sectionKey: HomeSectionKey, event: Event): void {
    const input = event.target as HTMLInputElement | null;
    const file = input?.files?.[0] ?? null;
    const formState = this.getFormState(sectionKey);

    this.revokePreviewUrl(formState.previewUrl);
    formState.file = file;
    formState.previewUrl = file ? URL.createObjectURL(file) : null;

    if (file && !formState.title.trim()) {
      formState.title = this.deriveDefaultTitle(file.name);
    }
  }

  prepareAdd(sectionKey: HomeSectionKey): void {
    this.resetForm(sectionKey);
  }

  prepareReplace(sectionKey: HomeSectionKey, item: HomeMediaItem): void {
    const formState = this.getFormState(sectionKey);
    this.revokePreviewUrl(formState.previewUrl);
    formState.file = null;
    formState.previewUrl = item.previewUrl;
    formState.title = item.title ?? '';
    formState.altText = item.altText ?? '';
    formState.sortOrder = item.sortOrder;
    formState.isPrimary = item.isPrimary;
    formState.replacingUsageId = item.usageId;
  }

  cancelReplace(sectionKey: HomeSectionKey): void {
    this.resetForm(sectionKey);
  }

  uploadForSection(sectionKey: HomeSectionKey): void {
    const section = this.sections.find((item) => item.usageKey === sectionKey);
    const formState = this.getFormState(sectionKey);

    if (!section || !formState.file || formState.saving) {
      return;
    }

    formState.saving = true;
    this.errorMessage = null;
    this.successMessage = null;

    const createUsagePayload = (
      mediaAssetId: string,
    ): AdminCreateMediaUsagePayload => ({
      usageType: section.usageType,
      usageKey: section.usageKey,
      mediaAssetId,
      sortOrder: formState.sortOrder,
      isPrimary: formState.isPrimary,
      isActive: true,
    });

    this.adminMediaService
      .uploadAsset(formState.file, {
        title: formState.title,
        altText: formState.altText,
        visibility: 'PUBLIC',
      })
      .pipe(
        switchMap((asset) =>
          this.adminMediaService.createUsage(createUsagePayload(asset.id)),
        ),
        switchMap(() => {
          if (!formState.replacingUsageId) {
            return of(null);
          }
          return this.adminMediaService.updateUsage(
            formState.replacingUsageId,
            {
              isActive: false,
              isPrimary: false,
            },
          );
        }),
      )
      .subscribe({
        next: () => {
          formState.saving = false;
          this.successMessage = formState.replacingUsageId
            ? 'Immagine home sostituita.'
            : 'Immagine home caricata.';
          this.loadHomeMedia();
        },
        error: (error) => {
          formState.saving = false;
          this.errorMessage = this.extractErrorMessage(
            error,
            'Upload immagine non riuscito.',
          );
        },
      });
  }

  setPrimary(item: HomeMediaItem): void {
    if (item.isPrimary || this.actingUsageIds.has(item.usageId)) {
      return;
    }

    this.errorMessage = null;
    this.successMessage = null;
    this.actingUsageIds.add(item.usageId);

    this.adminMediaService
      .updateUsage(item.usageId, { isPrimary: true, isActive: true })
      .subscribe({
        next: () => {
          this.actingUsageIds.delete(item.usageId);
          this.successMessage = 'Immagine principale aggiornata.';
          this.loadHomeMedia();
        },
        error: (error) => {
          this.actingUsageIds.delete(item.usageId);
          this.errorMessage = this.extractErrorMessage(
            error,
            'Aggiornamento immagine principale non riuscito.',
          );
        },
      });
  }

  saveSortOrder(item: HomeMediaItem): void {
    if (
      this.actingUsageIds.has(item.usageId) ||
      item.draftSortOrder === item.sortOrder
    ) {
      return;
    }

    this.errorMessage = null;
    this.successMessage = null;
    this.actingUsageIds.add(item.usageId);

    this.adminMediaService
      .updateUsage(item.usageId, { sortOrder: item.draftSortOrder })
      .subscribe({
        next: () => {
          this.actingUsageIds.delete(item.usageId);
          this.successMessage = 'Ordine immagine aggiornato.';
          this.loadHomeMedia();
        },
        error: (error) => {
          this.actingUsageIds.delete(item.usageId);
          this.errorMessage = this.extractErrorMessage(
            error,
            'Aggiornamento ordine non riuscito.',
          );
        },
      });
  }

  removeFromHome(item: HomeMediaItem): void {
    if (this.actingUsageIds.has(item.usageId)) {
      return;
    }

    this.errorMessage = null;
    this.successMessage = null;
    this.actingUsageIds.add(item.usageId);

    this.adminMediaService
      .updateUsage(item.usageId, { isActive: false, isPrimary: false })
      .subscribe({
        next: () => {
          this.actingUsageIds.delete(item.usageId);
          this.successMessage = 'Immagine rimossa dalla home.';
          this.loadHomeMedia();
        },
        error: (error) => {
          this.actingUsageIds.delete(item.usageId);
          this.errorMessage = this.extractErrorMessage(
            error,
            'Rimozione immagine dalla home non riuscita.',
          );
        },
      });
  }

  isUsageBusy(usageId: string): boolean {
    return this.actingUsageIds.has(usageId);
  }

  getSectionsForGroup(
    groupId: HomeMediaSectionGroup['id'],
  ): HomeMediaSectionView[] {
    return this.sections.filter((section) => section.groupId === groupId);
  }

  trackSection(_: number, section: HomeMediaSectionView): string {
    return section.usageKey;
  }

  trackItem(_: number, item: HomeMediaItem): string {
    return item.usageId;
  }

  private buildSectionItems(
    assets: readonly AdminMediaAsset[],
    config: HomeMediaSectionConfig,
  ): HomeMediaItem[] {
    return assets
      .flatMap((asset) =>
        asset.usages
          .filter(
            (usage) =>
              usage.isActive &&
              usage.usageType === config.usageType &&
              usage.usageKey === config.usageKey,
          )
          .map((usage) => this.toHomeMediaItem(asset, usage, config)),
      )
      .sort((left, right) => {
        if (left.sortOrder !== right.sortOrder) {
          return left.sortOrder - right.sortOrder;
        }
        return left.createdAt.localeCompare(right.createdAt);
      });
  }

  private toHomeMediaItem(
    asset: AdminMediaAsset,
    usage: AdminMediaUsage,
    config: HomeMediaSectionConfig,
  ): HomeMediaItem {
    return {
      usageId: usage.id,
      mediaAssetId: asset.id,
      originalFilename: asset.originalFilename,
      title: asset.title,
      altText: asset.altText,
      sortOrder: usage.sortOrder ?? 0,
      draftSortOrder: usage.sortOrder ?? 0,
      isPrimary: usage.isPrimary,
      previewUrl: this.resolvePreviewUrl(asset, config.preferredVariantName),
      createdAt: usage.createdAt,
    };
  }

  private resolvePreviewUrl(
    asset: AdminMediaAsset,
    preferredVariantName: 'card' | 'hero',
  ): string | null {
    const variantOrder =
      preferredVariantName === 'hero'
        ? ['hero', 'card', 'thumb']
        : ['card', 'thumb', 'hero'];
    const formatOrder = ['JPEG', 'WEBP', 'AVIF'];

    for (const variantName of variantOrder) {
      for (const format of formatOrder) {
        const match = asset.variants.find(
          (variant) =>
            variant.variantName === variantName &&
            variant.format === format &&
            !!variant.publicUrl,
        );
        if (match?.publicUrl) {
          return match.publicUrl;
        }
      }
    }

    return null;
  }

  private resetForm(sectionKey: HomeSectionKey): void {
    const formState = this.getFormState(sectionKey);
    const section = this.sections.find((item) => item.usageKey === sectionKey);
    const nextSortOrder = (section?.items.at(-1)?.sortOrder ?? -1) + 1;

    this.revokePreviewUrl(formState.previewUrl);
    this.formStateByKey[sectionKey] = {
      file: null,
      previewUrl: null,
      title: '',
      altText: '',
      sortOrder: Math.max(0, nextSortOrder),
      isPrimary: (section?.items.length ?? 0) === 0,
      replacingUsageId: null,
      saving: false,
    };
  }

  private revokePreviewUrl(previewUrl: string | null): void {
    if (!previewUrl?.startsWith('blob:')) {
      return;
    }
    URL.revokeObjectURL(previewUrl);
  }

  private deriveDefaultTitle(filename: string): string {
    const normalized = filename.replace(/\.[^.]+$/, '').replace(/[-_]+/g, ' ');
    return normalized.trim();
  }

  private createEmptyFormState(): HomeMediaFormState {
    return {
      file: null,
      previewUrl: null,
      title: '',
      altText: '',
      sortOrder: 0,
      isPrimary: false,
      replacingUsageId: null,
      saving: false,
    };
  }

  private extractErrorMessage(error: unknown, fallback: string): string {
    const candidate = error as {
      error?: { message?: string };
      message?: string;
    };
    return candidate?.error?.message || candidate?.message || fallback;
  }
}
