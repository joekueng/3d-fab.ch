import { CommonModule } from '@angular/common';
import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { of, switchMap } from 'rxjs';
import {
  AdminCreateMediaUsagePayload,
  AdminMediaLanguage,
  AdminMediaAsset,
  AdminMediaService,
  AdminMediaTranslation,
  AdminMediaUsage,
} from '../services/admin-media.service';

type HomeSectionKey =
  | 'shop-gallery'
  | 'founders-gallery'
  | 'capability-prototyping'
  | 'capability-custom-parts'
  | 'capability-small-series'
  | 'capability-cad'
  | 'joe'
  | 'matteo';

type HomeMediaUsageType = 'HOME_SECTION' | 'ABOUT_MEMBER';

interface HomeMediaSectionConfig {
  usageType: HomeMediaUsageType;
  usageKey: HomeSectionKey;
  groupId: 'galleries' | 'capabilities' | 'about-members';
  title: string;
  preferredVariantName: 'card' | 'hero';
}

interface HomeMediaFormState {
  file: File | null;
  previewUrl: string | null;
  activeLanguage: AdminMediaLanguage;
  translations: Record<AdminMediaLanguage, AdminMediaTranslation>;
  sortOrder: number;
  isPrimary: boolean;
  replacingUsageId: string | null;
  saving: boolean;
}

interface HomeMediaItem {
  usageId: string;
  mediaAssetId: string;
  originalFilename: string;
  translations: Record<AdminMediaLanguage, AdminMediaTranslation>;
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

const SUPPORTED_MEDIA_LANGUAGES: readonly AdminMediaLanguage[] = [
  'it',
  'en',
  'de',
  'fr',
];

const MEDIA_LANGUAGE_LABELS: Readonly<Record<AdminMediaLanguage, string>> = {
  it: 'IT',
  en: 'EN',
  de: 'DE',
  fr: 'FR',
};

@Component({
  selector: 'app-admin-home-media',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-home-media.component.html',
  styleUrl: './admin-home-media.component.scss',
})
export class AdminHomeMediaComponent implements OnInit, OnDestroy {
  private readonly adminMediaService = inject(AdminMediaService);
  readonly mediaLanguages = SUPPORTED_MEDIA_LANGUAGES;
  readonly mediaLanguageLabels = MEDIA_LANGUAGE_LABELS;

  readonly sectionGroups: readonly HomeMediaSectionGroup[] = [
    {
      id: 'galleries',
      title: 'Gallery e visual principali',
    },
    {
      id: 'capabilities',
      title: 'Cosa puoi ottenere',
    },
    {
      id: 'about-members',
      title: 'Chi siamo',
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
    {
      usageType: 'ABOUT_MEMBER',
      usageKey: 'joe',
      groupId: 'about-members',
      title: 'Chi siamo: Joe',
      preferredVariantName: 'card',
    },
    {
      usageType: 'ABOUT_MEMBER',
      usageKey: 'matteo',
      groupId: 'about-members',
      title: 'Chi siamo: Matteo',
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
      joe: this.createEmptyFormState(),
      matteo: this.createEmptyFormState(),
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

    if (file && this.areAllTitlesBlank(formState.translations)) {
      const nextTitle = this.deriveDefaultTitle(file.name);
      for (const language of this.mediaLanguages) {
        formState.translations[language].title = nextTitle;
      }
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
    formState.translations = this.cloneTranslations(item.translations);
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

    const validationError = this.validateTranslations(formState.translations);
    if (validationError) {
      this.errorMessage = validationError;
      return;
    }

    const normalizedTranslations = this.normalizeTranslations(
      formState.translations,
    );

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
      translations: normalizedTranslations,
    });

    this.adminMediaService
      .uploadAsset(formState.file, {
        title: normalizedTranslations.it.title,
        altText: normalizedTranslations.it.altText,
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

  setActiveLanguage(
    sectionKey: HomeSectionKey,
    language: AdminMediaLanguage,
  ): void {
    this.getFormState(sectionKey).activeLanguage = language;
  }

  getActiveTranslation(sectionKey: HomeSectionKey): AdminMediaTranslation {
    const formState = this.getFormState(sectionKey);
    return formState.translations[formState.activeLanguage];
  }

  isLanguageComplete(
    sectionKey: HomeSectionKey,
    language: AdminMediaLanguage,
  ): boolean {
    return this.isTranslationComplete(
      this.getFormState(sectionKey).translations[language],
    );
  }

  isLanguageStarted(
    sectionKey: HomeSectionKey,
    language: AdminMediaLanguage,
  ): boolean {
    return this.isTranslationStarted(
      this.getFormState(sectionKey).translations[language],
    );
  }

  isLanguageIncomplete(
    sectionKey: HomeSectionKey,
    language: AdminMediaLanguage,
  ): boolean {
    return (
      this.isLanguageStarted(sectionKey, language) &&
      !this.isLanguageComplete(sectionKey, language)
    );
  }

  getItemTranslation(
    item: HomeMediaItem,
    language: AdminMediaLanguage,
  ): AdminMediaTranslation {
    return item.translations[language];
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
      translations: this.normalizeTranslations(usage.translations),
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
      activeLanguage: 'it',
      translations: this.createEmptyTranslations(),
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
      activeLanguage: 'it',
      translations: this.createEmptyTranslations(),
      sortOrder: 0,
      isPrimary: false,
      replacingUsageId: null,
      saving: false,
    };
  }

  private createEmptyTranslations(): Record<
    AdminMediaLanguage,
    AdminMediaTranslation
  > {
    return {
      it: { title: '', altText: '' },
      en: { title: '', altText: '' },
      de: { title: '', altText: '' },
      fr: { title: '', altText: '' },
    };
  }

  private cloneTranslations(
    translations: Record<AdminMediaLanguage, AdminMediaTranslation>,
  ): Record<AdminMediaLanguage, AdminMediaTranslation> {
    return this.normalizeTranslations(translations);
  }

  private normalizeTranslations(
    translations: Partial<
      Record<AdminMediaLanguage, Partial<AdminMediaTranslation>>
    >,
  ): Record<AdminMediaLanguage, AdminMediaTranslation> {
    return {
      it: {
        title: translations.it?.title?.trim() ?? '',
        altText: translations.it?.altText?.trim() ?? '',
      },
      en: {
        title: translations.en?.title?.trim() ?? '',
        altText: translations.en?.altText?.trim() ?? '',
      },
      de: {
        title: translations.de?.title?.trim() ?? '',
        altText: translations.de?.altText?.trim() ?? '',
      },
      fr: {
        title: translations.fr?.title?.trim() ?? '',
        altText: translations.fr?.altText?.trim() ?? '',
      },
    };
  }

  private areAllTitlesBlank(
    translations: Record<AdminMediaLanguage, AdminMediaTranslation>,
  ): boolean {
    return this.mediaLanguages.every(
      (language) => !translations[language].title.trim(),
    );
  }

  private isTranslationComplete(translation: AdminMediaTranslation): boolean {
    return !!translation.title.trim() && !!translation.altText.trim();
  }

  private isTranslationStarted(translation: AdminMediaTranslation): boolean {
    return !!translation.title.trim() || !!translation.altText.trim();
  }

  private validateTranslations(
    translations: Record<AdminMediaLanguage, AdminMediaTranslation>,
  ): string | null {
    for (const language of this.mediaLanguages) {
      const translation = translations[language];
      if (!translation.title.trim()) {
        return `Compila il titolo per ${this.mediaLanguageLabels[language]}.`;
      }
      if (!translation.altText.trim()) {
        return `Compila l'alt text per ${this.mediaLanguageLabels[language]}.`;
      }
    }
    return null;
  }

  private extractErrorMessage(error: unknown, fallback: string): string {
    const candidate = error as {
      error?: { message?: string };
      message?: string;
    };
    return candidate?.error?.message || candidate?.message || fallback;
  }
}
