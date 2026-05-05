import { CommonModule, isPlatformBrowser } from '@angular/common';
import {
  Component,
  OnDestroy,
  OnInit,
  PLATFORM_ID,
  inject,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { of, switchMap } from 'rxjs';
import {
  AdminCreateMediaUsagePayload,
  AdminMediaAsset,
  AdminMediaLanguage,
  AdminMediaService,
  AdminMediaTranslation,
  AdminMediaUsage,
} from '../services/admin-media.service';
import {
  AdminTranslateLocalizedTextPayload,
  AdminTranslateLocalizedTextResponse,
  AdminTranslationService,
} from '../services/admin-translation.service';
import { FeaturePanelComponent } from '../../../shared/components/feature-panel/feature-panel.component';
import { AppButtonComponent } from '../../../shared/components/app-button/app-button.component';
import { AppCheckboxComponent } from '../../../shared/components/app-checkbox/app-checkbox.component';
import { AppInputComponent } from '../../../shared/components/app-input/app-input.component';
import { AdminLanguageToolbarComponent } from '../../../shared/components/admin-language-toolbar/admin-language-toolbar.component';
import {
  ADMIN_LANGUAGE_LABELS,
  ADMIN_LOCALIZED_LANGUAGES,
  AdminLanguageStatus,
  buildAdminLanguageStatusMap,
  mergeLocalizedTextMap,
} from '../../../shared/utils/admin-localization.util';

type MediaCollectionId = 'home' | 'materials' | 'about';
type MediaUsageType = 'HOME_SECTION' | 'ABOUT_MEMBER' | 'MATERIALS_PAGE';
type MediaVariantName = 'card' | 'hero';

interface MediaCollectionConfig {
  id: MediaCollectionId;
  title: string;
  description: string;
}

interface MediaSectionGroup {
  id: string;
  collectionId: MediaCollectionId;
  title: string;
  description: string;
}

interface MediaSectionConfig {
  id: string;
  usageType: MediaUsageType;
  usageKey: string;
  collectionId: MediaCollectionId;
  groupId: string;
  title: string;
  description: string;
  preferredVariantName: MediaVariantName;
}

interface MediaFormState {
  file: File | null;
  previewUrl: string | null;
  activeLanguage: AdminMediaLanguage;
  translations: Record<AdminMediaLanguage, AdminMediaTranslation>;
  sortOrder: number;
  isPrimary: boolean;
  replacingUsageId: string | null;
  saving: boolean;
  translating: boolean;
  overwriteExistingTranslations: boolean;
}

interface MediaItem {
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

interface MediaSectionView extends MediaSectionConfig {
  items: MediaItem[];
}

const SUPPORTED_MEDIA_LANGUAGES =
  ADMIN_LOCALIZED_LANGUAGES satisfies readonly AdminMediaLanguage[];

const MEDIA_LANGUAGE_LABELS =
  ADMIN_LANGUAGE_LABELS satisfies Readonly<Record<AdminMediaLanguage, string>>;

@Component({
  selector: 'app-admin-home-media',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    FeaturePanelComponent,
    AppButtonComponent,
    AppCheckboxComponent,
    AppInputComponent,
    AdminLanguageToolbarComponent,
  ],
  templateUrl: './admin-home-media.component.html',
  styleUrl: './admin-home-media.component.scss',
})
export class AdminHomeMediaComponent implements OnInit, OnDestroy {
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly adminMediaService = inject(AdminMediaService);
  private readonly adminTranslationService = inject(AdminTranslationService);

  readonly mediaLanguages = SUPPORTED_MEDIA_LANGUAGES;
  readonly mediaLanguageLabels = MEDIA_LANGUAGE_LABELS;

  readonly collections: readonly MediaCollectionConfig[] = [
    {
      id: 'home',
      title: 'Home',
      description: 'Gallery principali e card capability.',
    },
    {
      id: 'materials',
      title: 'Materiali e qualita',
      description: 'Guide visive per layer, ugelli e riempimento.',
    },
    {
      id: 'about',
      title: 'Chi siamo',
      description: 'Ritratti e visual del team.',
    },
  ];

  readonly sectionGroups: readonly MediaSectionGroup[] = [
    {
      id: 'home-galleries',
      collectionId: 'home',
      title: 'Gallery e visual principali',
      description: 'Elementi hero e gallery della homepage.',
    },
    {
      id: 'home-capabilities',
      collectionId: 'home',
      title: 'Capability',
      description: 'Card che spiegano i servizi principali in home.',
    },
    {
      id: 'materials-quality',
      collectionId: 'materials',
      title: 'Guide qualita',
      description: 'Confronti pratici usati nella pagina materiali.',
    },
    {
      id: 'about-members',
      collectionId: 'about',
      title: 'Team',
      description: 'Media associati ai membri della pagina Chi siamo.',
    },
  ];

  readonly sectionConfigs: readonly MediaSectionConfig[] = [
    {
      id: 'HOME_SECTION::shop-gallery',
      usageType: 'HOME_SECTION',
      usageKey: 'shop-gallery',
      collectionId: 'home',
      groupId: 'home-galleries',
      title: 'Home: gallery shop',
      description: 'Visual della gallery prodotti in home.',
      preferredVariantName: 'card',
    },
    {
      id: 'HOME_SECTION::founders-gallery',
      usageType: 'HOME_SECTION',
      usageKey: 'founders-gallery',
      collectionId: 'home',
      groupId: 'home-galleries',
      title: 'Home: gallery founders',
      description: 'Visual del blocco founders in homepage.',
      preferredVariantName: 'hero',
    },
    {
      id: 'HOME_SECTION::capability-prototyping',
      usageType: 'HOME_SECTION',
      usageKey: 'capability-prototyping',
      collectionId: 'home',
      groupId: 'home-capabilities',
      title: 'Home: prototipazione veloce',
      description: 'Card capability dedicata alla prototipazione.',
      preferredVariantName: 'card',
    },
    {
      id: 'HOME_SECTION::capability-custom-parts',
      usageType: 'HOME_SECTION',
      usageKey: 'capability-custom-parts',
      collectionId: 'home',
      groupId: 'home-capabilities',
      title: 'Home: pezzi personalizzati',
      description: 'Card capability dedicata ai pezzi custom.',
      preferredVariantName: 'card',
    },
    {
      id: 'HOME_SECTION::capability-small-series',
      usageType: 'HOME_SECTION',
      usageKey: 'capability-small-series',
      collectionId: 'home',
      groupId: 'home-capabilities',
      title: 'Home: piccole serie',
      description: 'Card capability dedicata alle piccole serie.',
      preferredVariantName: 'card',
    },
    {
      id: 'HOME_SECTION::capability-cad',
      usageType: 'HOME_SECTION',
      usageKey: 'capability-cad',
      collectionId: 'home',
      groupId: 'home-capabilities',
      title: 'Home: consulenza e CAD',
      description: 'Card capability dedicata a consulenza e CAD.',
      preferredVariantName: 'card',
    },
    {
      id: 'MATERIALS_PAGE::guide-layer-012',
      usageType: 'MATERIALS_PAGE',
      usageKey: 'guide-layer-012',
      collectionId: 'materials',
      groupId: 'materials-quality',
      title: 'Qualita: layer 0.12 mm',
      description: 'Confronto visivo per layer fine.',
      preferredVariantName: 'card',
    },
    {
      id: 'MATERIALS_PAGE::guide-layer-020',
      usageType: 'MATERIALS_PAGE',
      usageKey: 'guide-layer-020',
      collectionId: 'materials',
      groupId: 'materials-quality',
      title: 'Qualita: layer 0.20 mm',
      description: 'Confronto visivo per layer standard.',
      preferredVariantName: 'card',
    },
    {
      id: 'MATERIALS_PAGE::guide-layer-028',
      usageType: 'MATERIALS_PAGE',
      usageKey: 'guide-layer-028',
      collectionId: 'materials',
      groupId: 'materials-quality',
      title: 'Qualita: layer 0.28 mm',
      description: 'Confronto visivo per layer rapido.',
      preferredVariantName: 'card',
    },
    {
      id: 'MATERIALS_PAGE::guide-nozzle-025',
      usageType: 'MATERIALS_PAGE',
      usageKey: 'guide-nozzle-025',
      collectionId: 'materials',
      groupId: 'materials-quality',
      title: 'Qualita: ugello 0.25 mm',
      description: 'Esempio visivo per ugello piccolo.',
      preferredVariantName: 'card',
    },
    {
      id: 'MATERIALS_PAGE::guide-nozzle-060',
      usageType: 'MATERIALS_PAGE',
      usageKey: 'guide-nozzle-060',
      collectionId: 'materials',
      groupId: 'materials-quality',
      title: 'Qualita: ugello 0.60 mm',
      description: 'Esempio visivo per ugello orientato alla produttivita.',
      preferredVariantName: 'card',
    },
    {
      id: 'MATERIALS_PAGE::guide-infill-15',
      usageType: 'MATERIALS_PAGE',
      usageKey: 'guide-infill-15',
      collectionId: 'materials',
      groupId: 'materials-quality',
      title: 'Qualita: infill 15%',
      description: 'Confronto visivo per riempimento leggero.',
      preferredVariantName: 'card',
    },
    {
      id: 'MATERIALS_PAGE::guide-infill-40',
      usageType: 'MATERIALS_PAGE',
      usageKey: 'guide-infill-40',
      collectionId: 'materials',
      groupId: 'materials-quality',
      title: 'Qualita: infill 40%',
      description: 'Confronto visivo per riempimento piu strutturale.',
      preferredVariantName: 'card',
    },
    {
      id: 'ABOUT_MEMBER::joe',
      usageType: 'ABOUT_MEMBER',
      usageKey: 'joe',
      collectionId: 'about',
      groupId: 'about-members',
      title: 'Chi siamo: Joe',
      description: 'Media del profilo Joe.',
      preferredVariantName: 'card',
    },
    {
      id: 'ABOUT_MEMBER::matteo',
      usageType: 'ABOUT_MEMBER',
      usageKey: 'matteo',
      collectionId: 'about',
      groupId: 'about-members',
      title: 'Chi siamo: Matteo',
      description: 'Media del profilo Matteo.',
      preferredVariantName: 'card',
    },
  ];

  sections: MediaSectionView[] = [];
  selectedCollectionId: MediaCollectionId = 'home';
  loading = false;
  errorMessage: string | null = null;
  successMessage: string | null = null;
  actingUsageIds = new Set<string>();

  private readonly formStateById = new Map<string, MediaFormState>(
    this.sectionConfigs.map(
      (config) => [config.id, this.createEmptyFormState()] as const,
    ),
  );

  get configuredSectionCount(): number {
    return this.sectionConfigs.length;
  }

  get activeImageCount(): number {
    return this.sections.reduce(
      (total, section) => total + section.items.length,
      0,
    );
  }

  get selectedCollection(): MediaCollectionConfig {
    return (
      this.collections.find(
        (collection) => collection.id === this.selectedCollectionId,
      ) ?? this.collections[0]
    );
  }

  ngOnInit(): void {
    this.loadMedia();
  }

  ngOnDestroy(): void {
    Array.from(this.formStateById.values()).forEach((formState) => {
      this.revokePreviewUrl(formState.previewUrl);
    });
  }

  loadMedia(): void {
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
        this.sectionConfigs.forEach((config) => {
          if (!this.getFormState(config.id).saving) {
            this.resetForm(config.id);
          }
        });
      },
      error: (error) => {
        this.loading = false;
        this.errorMessage = this.extractErrorMessage(
          error,
          'Impossibile caricare i media.',
        );
      },
    });
  }

  selectCollection(collectionId: MediaCollectionId): void {
    this.selectedCollectionId = collectionId;
    this.errorMessage = null;
    this.successMessage = null;
  }

  collectionSectionCount(collectionId: MediaCollectionId): number {
    return this.sectionConfigs.filter(
      (config) => config.collectionId === collectionId,
    ).length;
  }

  collectionActiveImageCount(collectionId: MediaCollectionId): number {
    return this.sections
      .filter((section) => section.collectionId === collectionId)
      .reduce((total, section) => total + section.items.length, 0);
  }

  getVisibleGroups(): MediaSectionGroup[] {
    return this.sectionGroups.filter(
      (group) =>
        group.collectionId === this.selectedCollectionId &&
        this.getSectionsForGroup(group.id).length > 0,
    );
  }

  getVisibleSectionCount(): number {
    return this.sections.filter(
      (section) => section.collectionId === this.selectedCollectionId,
    ).length;
  }

  getVisibleActiveImageCount(): number {
    return this.sections
      .filter((section) => section.collectionId === this.selectedCollectionId)
      .reduce((total, section) => total + section.items.length, 0);
  }

  getFormState(sectionId: string): MediaFormState {
    let formState = this.formStateById.get(sectionId);
    if (!formState) {
      formState = this.createEmptyFormState();
      this.formStateById.set(sectionId, formState);
    }
    return formState;
  }

  onFileSelected(sectionId: string, event: Event): void {
    const input = event.target as HTMLInputElement | null;
    const file = input?.files?.[0] ?? null;
    const formState = this.getFormState(sectionId);

    this.revokePreviewUrl(formState.previewUrl);
    formState.file = file;
    formState.previewUrl =
      file && this.isBrowser ? URL.createObjectURL(file) : null;

    if (file && this.areAllTitlesBlank(formState.translations)) {
      const nextTitle = this.deriveDefaultTitle(file.name);
      for (const language of this.mediaLanguages) {
        formState.translations[language].title = nextTitle;
      }
    }
  }

  prepareAdd(sectionId: string): void {
    this.resetForm(sectionId);
  }

  prepareReplace(sectionId: string, item: MediaItem): void {
    const formState = this.getFormState(sectionId);
    this.revokePreviewUrl(formState.previewUrl);
    formState.file = null;
    formState.previewUrl = item.previewUrl;
    formState.translations = this.cloneTranslations(item.translations);
    formState.sortOrder = item.sortOrder;
    formState.isPrimary = item.isPrimary;
    formState.replacingUsageId = item.usageId;
  }

  cancelReplace(sectionId: string): void {
    this.resetForm(sectionId);
  }

  uploadForSection(sectionId: string): void {
    const section = this.sections.find((item) => item.id === sectionId);
    const formState = this.getFormState(sectionId);

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
            ? 'Media sostituito.'
            : 'Media caricato.';
          this.loadMedia();
        },
        error: (error) => {
          formState.saving = false;
          this.errorMessage = this.extractErrorMessage(
            error,
            'Upload media non riuscito.',
          );
        },
      });
  }

  translateForSection(sectionId: string): void {
    const section = this.sections.find((item) => item.id === sectionId);
    const formState = this.getFormState(sectionId);
    if (!section || formState.translating) {
      return;
    }

    const sourceLanguage = formState.activeLanguage;
    const sourceTranslation = formState.translations[sourceLanguage];
    if (!sourceTranslation.title.trim() || !sourceTranslation.altText.trim()) {
      this.errorMessage = `Titolo e alt text ${this.mediaLanguageLabels[sourceLanguage]} sono obbligatori per tradurre.`;
      this.successMessage = null;
      return;
    }

    const payload = this.buildMediaTranslationPayload(section, formState);
    formState.translating = true;
    this.errorMessage = null;
    this.successMessage = null;

    this.adminTranslationService.translateLocalizedText(payload).subscribe({
      next: (response) => {
        formState.translating = false;
        this.applyMediaTranslation(
          formState,
          response,
          payload.overwriteExisting,
        );
        this.successMessage = response.targetLanguages.length
          ? `Traduzioni ${response.targetLanguages
              .map((language) => this.mediaLanguageLabels[language])
              .join(' / ')} aggiornate nel form.`
          : 'Nessun campo da tradurre.';
      },
      error: (error) => {
        formState.translating = false;
        this.errorMessage = this.extractErrorMessage(
          error,
          'Traduzione media non riuscita.',
        );
      },
    });
  }

  canTranslateSection(sectionId: string): boolean {
    const formState = this.getFormState(sectionId);
    const translation = formState.translations[formState.activeLanguage];
    return (
      !formState.translating &&
      !!translation.title.trim() &&
      !!translation.altText.trim()
    );
  }

  setPrimary(item: MediaItem): void {
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
          this.successMessage = 'Media principale aggiornato.';
          this.loadMedia();
        },
        error: (error) => {
          this.actingUsageIds.delete(item.usageId);
          this.errorMessage = this.extractErrorMessage(
            error,
            'Aggiornamento media principale non riuscito.',
          );
        },
      });
  }

  saveSortOrder(item: MediaItem): void {
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
          this.successMessage = 'Ordine media aggiornato.';
          this.loadMedia();
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

  deactivateUsage(item: MediaItem): void {
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
          this.successMessage = 'Media disattivato.';
          this.loadMedia();
        },
        error: (error) => {
          this.actingUsageIds.delete(item.usageId);
          this.errorMessage = this.extractErrorMessage(
            error,
            'Disattivazione media non riuscita.',
          );
        },
      });
  }

  isUsageBusy(usageId: string): boolean {
    return this.actingUsageIds.has(usageId);
  }

  setActiveLanguage(sectionId: string, language: AdminMediaLanguage): void {
    this.getFormState(sectionId).activeLanguage = language;
  }

  getActiveTranslation(sectionId: string): AdminMediaTranslation {
    const formState = this.getFormState(sectionId);
    return formState.translations[formState.activeLanguage];
  }

  isLanguageComplete(sectionId: string, language: AdminMediaLanguage): boolean {
    return this.isTranslationComplete(
      this.getFormState(sectionId).translations[language],
    );
  }

  isLanguageStarted(sectionId: string, language: AdminMediaLanguage): boolean {
    return this.isTranslationStarted(
      this.getFormState(sectionId).translations[language],
    );
  }

  isLanguageIncomplete(
    sectionId: string,
    language: AdminMediaLanguage,
  ): boolean {
    return (
      this.isLanguageStarted(sectionId, language) &&
      !this.isLanguageComplete(sectionId, language)
    );
  }

  languageStatuses(
    sectionId: string,
  ): Record<AdminMediaLanguage, AdminLanguageStatus> {
    return buildAdminLanguageStatusMap(
      this.mediaLanguages,
      (language) => this.isLanguageComplete(sectionId, language),
      (language) => this.isLanguageStarted(sectionId, language),
    );
  }

  getItemTranslation(
    item: MediaItem,
    language: AdminMediaLanguage,
  ): AdminMediaTranslation {
    return item.translations[language];
  }

  getSectionsForGroup(groupId: string): MediaSectionView[] {
    return this.sections.filter((section) => section.groupId === groupId);
  }

  trackCollection(_: number, collection: MediaCollectionConfig): string {
    return collection.id;
  }

  trackGroup(_: number, group: MediaSectionGroup): string {
    return group.id;
  }

  trackSection(_: number, section: MediaSectionView): string {
    return section.id;
  }

  trackItem(_: number, item: MediaItem): string {
    return item.usageId;
  }

  private buildSectionItems(
    assets: readonly AdminMediaAsset[],
    config: MediaSectionConfig,
  ): MediaItem[] {
    return assets
      .flatMap((asset) =>
        asset.usages
          .filter(
            (usage) =>
              usage.isActive &&
              usage.usageType === config.usageType &&
              usage.usageKey === config.usageKey,
          )
          .map((usage) => this.toMediaItem(asset, usage, config)),
      )
      .sort((left, right) => {
        if (left.sortOrder !== right.sortOrder) {
          return left.sortOrder - right.sortOrder;
        }
        return left.createdAt.localeCompare(right.createdAt);
      });
  }

  private toMediaItem(
    asset: AdminMediaAsset,
    usage: AdminMediaUsage,
    config: MediaSectionConfig,
  ): MediaItem {
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
    preferredVariantName: MediaVariantName,
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

  private resetForm(sectionId: string): void {
    const formState = this.getFormState(sectionId);
    const section = this.sections.find((item) => item.id === sectionId);
    const nextSortOrder = (section?.items.at(-1)?.sortOrder ?? -1) + 1;

    this.revokePreviewUrl(formState.previewUrl);
    this.formStateById.set(sectionId, {
      file: null,
      previewUrl: null,
      activeLanguage: 'it',
      translations: this.createEmptyTranslations(),
      sortOrder: Math.max(0, nextSortOrder),
      isPrimary: (section?.items.length ?? 0) === 0,
      replacingUsageId: null,
      saving: false,
      translating: false,
      overwriteExistingTranslations: false,
    });
  }

  private revokePreviewUrl(previewUrl: string | null): void {
    if (!this.isBrowser) {
      return;
    }
    if (!previewUrl?.startsWith('blob:')) {
      return;
    }
    URL.revokeObjectURL(previewUrl);
  }

  private deriveDefaultTitle(filename: string): string {
    const normalized = filename.replace(/\.[^.]+$/, '').replace(/[-_]+/g, ' ');
    return normalized.trim();
  }

  private createEmptyFormState(): MediaFormState {
    return {
      file: null,
      previewUrl: null,
      activeLanguage: 'it',
      translations: this.createEmptyTranslations(),
      sortOrder: 0,
      isPrimary: false,
      replacingUsageId: null,
      saving: false,
      translating: false,
      overwriteExistingTranslations: false,
    };
  }

  private buildMediaTranslationPayload(
    section: MediaSectionView,
    formState: MediaFormState,
  ): AdminTranslateLocalizedTextPayload {
    return {
      context: `Media ${section.title} (${section.usageType} / ${section.usageKey})`,
      sourceLanguage: formState.activeLanguage,
      overwriteExisting: formState.overwriteExistingTranslations,
      fields: {
        title: {
          required: true,
          values: this.mediaLanguages.reduce(
            (values, language) => ({
              ...values,
              [language]: formState.translations[language].title,
            }),
            {} as Record<AdminMediaLanguage, string>,
          ),
        },
        altText: {
          required: true,
          values: this.mediaLanguages.reduce(
            (values, language) => ({
              ...values,
              [language]: formState.translations[language].altText,
            }),
            {} as Record<AdminMediaLanguage, string>,
          ),
        },
      },
    };
  }

  private applyMediaTranslation(
    formState: MediaFormState,
    response: AdminTranslateLocalizedTextResponse,
    overwriteExisting: boolean,
  ): void {
    const titles = this.mediaLanguages.reduce(
      (values, language) => ({
        ...values,
        [language]: formState.translations[language].title,
      }),
      {} as Record<AdminMediaLanguage, string>,
    );
    const altTexts = this.mediaLanguages.reduce(
      (values, language) => ({
        ...values,
        [language]: formState.translations[language].altText,
      }),
      {} as Record<AdminMediaLanguage, string>,
    );

    mergeLocalizedTextMap(titles, response.fields['title'], {
      overwriteExisting,
      targetLanguages: response.targetLanguages,
    });
    mergeLocalizedTextMap(altTexts, response.fields['altText'], {
      overwriteExisting,
      targetLanguages: response.targetLanguages,
    });

    for (const language of this.mediaLanguages) {
      formState.translations[language].title = titles[language];
      formState.translations[language].altText = altTexts[language];
    }
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
