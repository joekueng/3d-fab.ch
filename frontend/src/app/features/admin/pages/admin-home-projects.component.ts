import { CommonModule, isPlatformBrowser } from '@angular/common';
import {
  Component,
  OnDestroy,
  OnInit,
  PLATFORM_ID,
  inject,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { switchMap } from 'rxjs';
import {
  AdminCreateMediaUsagePayload,
  AdminMediaLanguage,
  AdminMediaService,
  AdminMediaTranslation,
  AdminMediaUsage,
} from '../services/admin-media.service';
import {
  AdminHomeProject,
  AdminHomeProjectService,
  AdminUpsertHomeProjectPayload,
} from '../services/admin-home-project.service';
import {
  AdminTranslateLocalizedTextPayload,
  AdminTranslateLocalizedTextResponse,
  AdminTranslationService,
} from '../services/admin-translation.service';
import { AppButtonComponent } from '../../../shared/components/app-button/app-button.component';
import { AppCheckboxComponent } from '../../../shared/components/app-checkbox/app-checkbox.component';
import { AppInputComponent } from '../../../shared/components/app-input/app-input.component';
import { AppTextareaComponent } from '../../../shared/components/app-textarea/app-textarea.component';
import { AdminLanguageToolbarComponent } from '../../../shared/components/admin-language-toolbar/admin-language-toolbar.component';
import {
  ADMIN_LANGUAGE_LABELS,
  ADMIN_LOCALIZED_LANGUAGES,
  AdminLanguageStatus,
  buildAdminLanguageStatusMap,
  createEmptyLocalizedTextMap,
  mergeLocalizedTextMap,
} from '../../../shared/utils/admin-localization.util';

type LocalizedTextMap = Record<AdminMediaLanguage, string>;

interface HomeProjectForm {
  id: string | null;
  activeLanguage: AdminMediaLanguage;
  slug: string;
  eyebrows: LocalizedTextMap;
  titles: LocalizedTextMap;
  descriptions: LocalizedTextMap;
  isActive: boolean;
  sortOrder: number;
  saving: boolean;
  translating: boolean;
  overwriteExistingTranslations: boolean;
}

interface ProjectImageForm {
  file: File | null;
  previewUrl: string | null;
  activeLanguage: AdminMediaLanguage;
  translations: Record<AdminMediaLanguage, AdminMediaTranslation>;
  sortOrder: number;
  isPrimary: boolean;
  saving: boolean;
  translating: boolean;
  overwriteExistingTranslations: boolean;
}

interface ProjectImageItem {
  usageId: string;
  mediaAssetId: string;
  sortOrder: number;
  draftSortOrder: number;
  isPrimary: boolean;
  previewUrl: string | null;
  translations: Record<AdminMediaLanguage, AdminMediaTranslation>;
  createdAt: string;
}

const SUPPORTED_MEDIA_LANGUAGES =
  ADMIN_LOCALIZED_LANGUAGES satisfies readonly AdminMediaLanguage[];

const MEDIA_LANGUAGE_LABELS = ADMIN_LANGUAGE_LABELS satisfies Readonly<
  Record<AdminMediaLanguage, string>
>;

@Component({
  selector: 'app-admin-home-projects',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    AppButtonComponent,
    AppCheckboxComponent,
    AppInputComponent,
    AppTextareaComponent,
    AdminLanguageToolbarComponent,
  ],
  templateUrl: './admin-home-projects.component.html',
  styleUrl: './admin-home-projects.component.scss',
})
export class AdminHomeProjectsComponent implements OnInit, OnDestroy {
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly projectService = inject(AdminHomeProjectService);
  private readonly mediaService = inject(AdminMediaService);
  private readonly translationService = inject(AdminTranslationService);

  readonly mediaLanguages = SUPPORTED_MEDIA_LANGUAGES;
  readonly mediaLanguageLabels = MEDIA_LANGUAGE_LABELS;

  projects: AdminHomeProject[] = [];
  loading = true;
  errorMessage = '';
  successMessage = '';
  busyUsageIds = new Set<string>();
  imageSortDrafts = new Map<string, number>();
  form: HomeProjectForm = this.createEmptyProjectForm();
  imageForm: ProjectImageForm = this.createEmptyImageForm();

  ngOnInit(): void {
    this.loadProjects();
  }

  ngOnDestroy(): void {
    this.revokeImagePreview();
  }

  get selectedProject(): AdminHomeProject | null {
    const selectedId = this.form.id;
    if (!selectedId) {
      return null;
    }
    return this.projects.find((project) => project.id === selectedId) ?? null;
  }

  get activeProjectTranslation(): {
    eyebrow: string;
    title: string;
    description: string;
  } {
    const language = this.form.activeLanguage;
    return {
      eyebrow: this.form.eyebrows[language],
      title: this.form.titles[language],
      description: this.form.descriptions[language],
    };
  }

  get activeImageTranslation(): AdminMediaTranslation {
    return this.imageForm.translations[this.imageForm.activeLanguage];
  }

  loadProjects(): void {
    this.loading = true;
    this.projectService.getProjects().subscribe({
      next: (projects) => {
        this.projects = [...projects].sort((left, right) => {
          if ((left.sortOrder ?? 0) !== (right.sortOrder ?? 0)) {
            return (left.sortOrder ?? 0) - (right.sortOrder ?? 0);
          }
          return (left.titleIt ?? left.slug).localeCompare(
            right.titleIt ?? right.slug,
          );
        });
        this.imageSortDrafts.clear();
        this.loading = false;
        if (this.form.id) {
          const selected = this.projects.find(
            (project) => project.id === this.form.id,
          );
          if (selected) {
            this.editProject(selected, false);
            return;
          }
        }
        if (this.projects.length > 0) {
          this.editProject(this.projects[0], false);
        } else {
          this.startNewProject();
        }
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'Impossibile caricare i progetti home.';
      },
    });
  }

  startNewProject(): void {
    this.form = this.createEmptyProjectForm();
    this.form.sortOrder = Math.max(
      0,
      (this.projects.at(-1)?.sortOrder ?? -1) + 1,
    );
    this.resetImageForm();
  }

  editProject(project: AdminHomeProject, showMessage = false): void {
    this.form = {
      id: project.id,
      activeLanguage: this.form.activeLanguage ?? 'it',
      slug: project.slug ?? '',
      eyebrows: {
        it: project.eyebrowIt ?? '',
        en: project.eyebrowEn ?? '',
        de: project.eyebrowDe ?? '',
        fr: project.eyebrowFr ?? '',
      },
      titles: {
        it: project.titleIt ?? '',
        en: project.titleEn ?? '',
        de: project.titleDe ?? '',
        fr: project.titleFr ?? '',
      },
      descriptions: {
        it: project.descriptionIt ?? '',
        en: project.descriptionEn ?? '',
        de: project.descriptionDe ?? '',
        fr: project.descriptionFr ?? '',
      },
      isActive: project.isActive,
      sortOrder: project.sortOrder ?? 0,
      saving: false,
      translating: false,
      overwriteExistingTranslations: this.form.overwriteExistingTranslations,
    };
    this.resetImageForm();
    if (showMessage) {
      this.successMessage = 'Progetto selezionato.';
    }
  }

  setProjectLanguage(language: AdminMediaLanguage): void {
    this.form.activeLanguage = language;
  }

  setImageLanguage(language: AdminMediaLanguage): void {
    this.imageForm.activeLanguage = language;
  }

  projectLanguageStatuses(): Record<AdminMediaLanguage, AdminLanguageStatus> {
    return buildAdminLanguageStatusMap(
      this.mediaLanguages,
      (language) => this.isProjectLanguageComplete(language),
      (language) => this.isProjectLanguageStarted(language),
    );
  }

  imageLanguageStatuses(): Record<AdminMediaLanguage, AdminLanguageStatus> {
    return buildAdminLanguageStatusMap(
      this.mediaLanguages,
      (language) => this.isImageLanguageComplete(language),
      (language) => this.isImageLanguageStarted(language),
    );
  }

  translateProjectTexts(): void {
    if (this.form.translating) {
      return;
    }

    const sourceLanguage = this.form.activeLanguage;
    if (
      !this.form.titles[sourceLanguage].trim() ||
      !this.form.descriptions[sourceLanguage].trim()
    ) {
      this.errorMessage = `Titolo e descrizione ${this.mediaLanguageLabels[sourceLanguage]} sono obbligatori per tradurre.`;
      this.successMessage = '';
      return;
    }

    const payload = this.buildProjectTranslationPayload();
    this.form.translating = true;
    this.clearMessages();

    this.translationService.translateLocalizedText(payload).subscribe({
      next: (response) => {
        this.form.translating = false;
        this.applyProjectTranslation(response, payload.overwriteExisting);
        this.successMessage = response.targetLanguages.length
          ? `Traduzioni ${response.targetLanguages
              .map((language) => this.mediaLanguageLabels[language])
              .join(' / ')} aggiornate nel form.`
          : 'Nessun campo da tradurre.';
      },
      error: () => {
        this.form.translating = false;
        this.errorMessage = 'Traduzione progetto non riuscita.';
      },
    });
  }

  canTranslateProjectTexts(): boolean {
    const language = this.form.activeLanguage;
    return (
      !this.form.translating &&
      !!this.form.titles[language].trim() &&
      !!this.form.descriptions[language].trim()
    );
  }

  translateProjectImageTexts(): void {
    if (this.imageForm.translating) {
      return;
    }

    const sourceLanguage = this.imageForm.activeLanguage;
    const sourceTranslation = this.imageForm.translations[sourceLanguage];
    if (!sourceTranslation.title.trim() || !sourceTranslation.altText.trim()) {
      this.errorMessage = `Titolo e alt text ${this.mediaLanguageLabels[sourceLanguage]} sono obbligatori per tradurre.`;
      this.successMessage = '';
      return;
    }

    const project = this.selectedProject;
    const payload = this.buildProjectImageTranslationPayload(project);
    this.imageForm.translating = true;
    this.clearMessages();

    this.translationService.translateLocalizedText(payload).subscribe({
      next: (response) => {
        this.imageForm.translating = false;
        this.applyProjectImageTranslation(response, payload.overwriteExisting);
        this.successMessage = response.targetLanguages.length
          ? `Traduzioni immagine ${response.targetLanguages
              .map((language) => this.mediaLanguageLabels[language])
              .join(' / ')} aggiornate nel form.`
          : 'Nessun campo immagine da tradurre.';
      },
      error: () => {
        this.imageForm.translating = false;
        this.errorMessage = 'Traduzione immagine progetto non riuscita.';
      },
    });
  }

  canTranslateProjectImageTexts(): boolean {
    const language = this.imageForm.activeLanguage;
    const translation = this.imageForm.translations[language];
    return (
      !this.imageForm.translating &&
      !!translation.title.trim() &&
      !!translation.altText.trim()
    );
  }

  saveProject(): void {
    this.clearMessages();
    const payload = this.buildProjectPayload();
    if (!payload) {
      return;
    }

    this.form.saving = true;
    const request$ = this.form.id
      ? this.projectService.updateProject(this.form.id, payload)
      : this.projectService.createProject(payload);

    request$.subscribe({
      next: (project) => {
        this.successMessage = 'Progetto salvato.';
        this.form.saving = false;
        this.form.id = project.id;
        this.loadProjects();
      },
      error: () => {
        this.form.saving = false;
        this.errorMessage = 'Impossibile salvare il progetto.';
      },
    });
  }

  deactivateSelectedProject(): void {
    if (!this.selectedProject) {
      return;
    }
    this.form.isActive = false;
    this.saveProject();
  }

  onImageFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.revokeImagePreview();
    this.imageForm.file = file;
    this.imageForm.previewUrl =
      file && this.isBrowser ? URL.createObjectURL(file) : null;
    input.value = '';
  }

  uploadProjectImage(): void {
    this.clearMessages();
    const project = this.selectedProject;
    if (!project || !this.imageForm.file) {
      this.errorMessage = 'Salva un progetto e scegli un file immagine.';
      return;
    }

    this.imageForm.saving = true;
    const translations = this.normalizeMediaTranslations(
      this.imageForm.translations,
      this.resolveProjectTitle(project),
    );
    const uploadTitle = this.firstNonBlank(
      translations.it.title,
      translations.en.title,
      translations.de.title,
      translations.fr.title,
      this.resolveProjectTitle(project),
    );
    const uploadAlt = this.firstNonBlank(
      translations.it.altText,
      translations.en.altText,
      translations.de.altText,
      translations.fr.altText,
      uploadTitle,
    );

    this.mediaService
      .uploadAsset(this.imageForm.file, {
        title: uploadTitle ?? undefined,
        altText: uploadAlt ?? undefined,
        visibility: 'PUBLIC',
      })
      .pipe(
        switchMap((asset) => {
          const payload: AdminCreateMediaUsagePayload = {
            usageType: project.mediaUsageType,
            usageKey: project.mediaUsageKey,
            ownerId: null,
            mediaAssetId: asset.id,
            sortOrder: Number(this.imageForm.sortOrder) || 0,
            isPrimary: this.imageForm.isPrimary,
            isActive: true,
            translations,
          };
          return this.mediaService.createUsage(payload);
        }),
      )
      .subscribe({
        next: () => {
          this.successMessage = 'Immagine progetto caricata.';
          this.imageForm.saving = false;
          this.resetImageForm();
          this.loadProjects();
        },
        error: () => {
          this.imageForm.saving = false;
          this.errorMessage = 'Impossibile caricare immagine progetto.';
        },
      });
  }

  projectImages(project: AdminHomeProject | null): ProjectImageItem[] {
    if (!project) {
      return [];
    }
    return (project.mediaUsages ?? [])
      .filter((usage) => usage.isActive)
      .map((usage) => ({
        usageId: usage.id,
        mediaAssetId: usage.mediaAssetId,
        sortOrder: usage.sortOrder ?? 0,
        draftSortOrder: usage.sortOrder ?? 0,
        isPrimary: usage.isPrimary,
        previewUrl: this.resolveProjectImagePreview(project, usage),
        translations: this.normalizeMediaTranslations(usage.translations),
        createdAt: usage.createdAt,
      }))
      .sort((left, right) => left.sortOrder - right.sortOrder);
  }

  saveImageSortOrder(item: ProjectImageItem): void {
    if (this.busyUsageIds.has(item.usageId)) {
      return;
    }
    this.busyUsageIds.add(item.usageId);
    const sortOrder = this.imageSortDrafts.get(item.usageId) ?? item.sortOrder;
    this.mediaService
      .updateUsage(item.usageId, { sortOrder: Number(sortOrder) || 0 })
      .subscribe({
        next: () => {
          this.busyUsageIds.delete(item.usageId);
          this.successMessage = 'Ordine immagine salvato.';
          this.loadProjects();
        },
        error: () => {
          this.busyUsageIds.delete(item.usageId);
          this.errorMessage = 'Impossibile salvare ordine immagine.';
        },
      });
  }

  setPrimaryImage(item: ProjectImageItem): void {
    if (this.busyUsageIds.has(item.usageId)) {
      return;
    }
    this.busyUsageIds.add(item.usageId);
    this.mediaService
      .updateUsage(item.usageId, { isPrimary: true, isActive: true })
      .subscribe({
        next: () => {
          this.busyUsageIds.delete(item.usageId);
          this.successMessage = 'Immagine primaria aggiornata.';
          this.loadProjects();
        },
        error: () => {
          this.busyUsageIds.delete(item.usageId);
          this.errorMessage = 'Impossibile aggiornare immagine primaria.';
        },
      });
  }

  deactivateImage(item: ProjectImageItem): void {
    if (this.busyUsageIds.has(item.usageId)) {
      return;
    }
    this.busyUsageIds.add(item.usageId);
    this.mediaService
      .updateUsage(item.usageId, { isActive: false, isPrimary: false })
      .subscribe({
        next: () => {
          this.busyUsageIds.delete(item.usageId);
          this.successMessage = 'Immagine disattivata.';
          this.loadProjects();
        },
        error: () => {
          this.busyUsageIds.delete(item.usageId);
          this.errorMessage = 'Impossibile disattivare immagine.';
        },
      });
  }

  isUsageBusy(usageId: string): boolean {
    return this.busyUsageIds.has(usageId);
  }

  imageSortOrderDraft(item: ProjectImageItem): number {
    return this.imageSortDrafts.get(item.usageId) ?? item.sortOrder;
  }

  setImageSortOrderDraft(item: ProjectImageItem, value: string | number): void {
    this.imageSortDrafts.set(item.usageId, Number(value) || 0);
  }

  trackProject(_: number, project: AdminHomeProject): string {
    return project.id;
  }

  trackImage(_: number, item: ProjectImageItem): string {
    return item.usageId;
  }

  private isProjectLanguageComplete(language: AdminMediaLanguage): boolean {
    return (
      !!this.form.titles[language].trim() &&
      !!this.form.descriptions[language].trim()
    );
  }

  private isProjectLanguageStarted(language: AdminMediaLanguage): boolean {
    return (
      !!this.form.eyebrows[language].trim() ||
      !!this.form.titles[language].trim() ||
      !!this.form.descriptions[language].trim()
    );
  }

  private isImageLanguageComplete(language: AdminMediaLanguage): boolean {
    return (
      !!this.imageForm.translations[language].title.trim() &&
      !!this.imageForm.translations[language].altText.trim()
    );
  }

  private isImageLanguageStarted(language: AdminMediaLanguage): boolean {
    return (
      !!this.imageForm.translations[language].title.trim() ||
      !!this.imageForm.translations[language].altText.trim()
    );
  }

  private buildProjectTranslationPayload(): AdminTranslateLocalizedTextPayload {
    return {
      context: `Home project ${this.form.slug || this.form.titles.it || 'draft project'}`,
      sourceLanguage: this.form.activeLanguage,
      overwriteExisting: this.form.overwriteExistingTranslations,
      fields: {
        eyebrow: {
          required: false,
          values: { ...this.form.eyebrows },
        },
        title: {
          required: true,
          values: { ...this.form.titles },
        },
        description: {
          required: true,
          values: { ...this.form.descriptions },
        },
      },
    };
  }

  private buildProjectImageTranslationPayload(
    project: AdminHomeProject | null,
  ): AdminTranslateLocalizedTextPayload {
    return {
      context: `Home project image ${project?.slug ?? this.form.slug ?? 'draft project'}`,
      sourceLanguage: this.imageForm.activeLanguage,
      overwriteExisting: this.imageForm.overwriteExistingTranslations,
      fields: {
        title: {
          required: true,
          values: this.mediaLanguages.reduce(
            (values, language) => ({
              ...values,
              [language]: this.imageForm.translations[language].title,
            }),
            {} as Record<AdminMediaLanguage, string>,
          ),
        },
        altText: {
          required: true,
          values: this.mediaLanguages.reduce(
            (values, language) => ({
              ...values,
              [language]: this.imageForm.translations[language].altText,
            }),
            {} as Record<AdminMediaLanguage, string>,
          ),
        },
      },
    };
  }

  private applyProjectTranslation(
    response: AdminTranslateLocalizedTextResponse,
    overwriteExisting: boolean,
  ): void {
    mergeLocalizedTextMap(this.form.eyebrows, response.fields['eyebrow'], {
      overwriteExisting,
      targetLanguages: response.targetLanguages,
    });
    mergeLocalizedTextMap(this.form.titles, response.fields['title'], {
      overwriteExisting,
      targetLanguages: response.targetLanguages,
    });
    mergeLocalizedTextMap(
      this.form.descriptions,
      response.fields['description'],
      {
        overwriteExisting,
        targetLanguages: response.targetLanguages,
      },
    );
  }

  private applyProjectImageTranslation(
    response: AdminTranslateLocalizedTextResponse,
    overwriteExisting: boolean,
  ): void {
    const titles = this.mediaLanguages.reduce(
      (values, language) => ({
        ...values,
        [language]: this.imageForm.translations[language].title,
      }),
      {} as Record<AdminMediaLanguage, string>,
    );
    const altTexts = this.mediaLanguages.reduce(
      (values, language) => ({
        ...values,
        [language]: this.imageForm.translations[language].altText,
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
      this.imageForm.translations[language].title = titles[language];
      this.imageForm.translations[language].altText = altTexts[language];
    }
  }

  private buildProjectPayload(): AdminUpsertHomeProjectPayload | null {
    const fallbackTitle = this.firstNonBlank(
      this.form.titles.it,
      this.form.titles.en,
      this.form.titles.de,
      this.form.titles.fr,
    );
    if (!fallbackTitle) {
      this.errorMessage = 'Inserisci almeno un titolo progetto.';
      return null;
    }

    return {
      slug: this.optionalValue(this.form.slug),
      eyebrowIt: this.optionalValue(this.form.eyebrows.it),
      eyebrowEn: this.optionalValue(this.form.eyebrows.en),
      eyebrowDe: this.optionalValue(this.form.eyebrows.de),
      eyebrowFr: this.optionalValue(this.form.eyebrows.fr),
      titleIt: this.firstNonBlank(this.form.titles.it, fallbackTitle) ?? '',
      titleEn: this.firstNonBlank(this.form.titles.en, fallbackTitle),
      titleDe: this.firstNonBlank(this.form.titles.de, fallbackTitle),
      titleFr: this.firstNonBlank(this.form.titles.fr, fallbackTitle),
      descriptionIt: this.firstNonBlank(
        this.form.descriptions.it,
        this.form.descriptions.en,
        this.form.descriptions.de,
        this.form.descriptions.fr,
      ),
      descriptionEn: this.optionalValue(this.form.descriptions.en),
      descriptionDe: this.optionalValue(this.form.descriptions.de),
      descriptionFr: this.optionalValue(this.form.descriptions.fr),
      isActive: this.form.isActive,
      sortOrder: Number(this.form.sortOrder) || 0,
    };
  }

  private resolveProjectImagePreview(
    project: AdminHomeProject,
    usage: AdminMediaUsage,
  ): string | null {
    const image = (project.images ?? []).find(
      (candidate) => candidate.mediaAssetId === usage.mediaAssetId,
    );
    return (
      image?.thumb?.jpegUrl ??
      image?.thumb?.webpUrl ??
      image?.card?.jpegUrl ??
      image?.card?.webpUrl ??
      image?.hero?.jpegUrl ??
      image?.hero?.webpUrl ??
      null
    );
  }

  private resolveProjectTitle(project: AdminHomeProject): string {
    return (
      this.firstNonBlank(
        project.titleIt,
        project.titleEn,
        project.titleDe,
        project.titleFr,
        project.slug,
      ) ?? 'Progetto home'
    );
  }

  private normalizeMediaTranslations(
    translations: Partial<
      Record<AdminMediaLanguage, Partial<AdminMediaTranslation>>
    > = {},
    fallback = '',
  ): Record<AdminMediaLanguage, AdminMediaTranslation> {
    const fallbackTitle = this.firstNonBlank(
      translations.it?.title,
      translations.en?.title,
      translations.de?.title,
      translations.fr?.title,
      fallback,
    );
    const fallbackAlt = this.firstNonBlank(
      translations.it?.altText,
      translations.en?.altText,
      translations.de?.altText,
      translations.fr?.altText,
      fallbackTitle,
      fallback,
    );

    return {
      it: {
        title: this.firstNonBlank(translations.it?.title, fallbackTitle) ?? '',
        altText:
          this.firstNonBlank(translations.it?.altText, fallbackAlt) ?? '',
      },
      en: {
        title: this.firstNonBlank(translations.en?.title, fallbackTitle) ?? '',
        altText:
          this.firstNonBlank(translations.en?.altText, fallbackAlt) ?? '',
      },
      de: {
        title: this.firstNonBlank(translations.de?.title, fallbackTitle) ?? '',
        altText:
          this.firstNonBlank(translations.de?.altText, fallbackAlt) ?? '',
      },
      fr: {
        title: this.firstNonBlank(translations.fr?.title, fallbackTitle) ?? '',
        altText:
          this.firstNonBlank(translations.fr?.altText, fallbackAlt) ?? '',
      },
    };
  }

  private createEmptyProjectForm(): HomeProjectForm {
    return {
      id: null,
      activeLanguage: 'it',
      slug: '',
      eyebrows: this.createEmptyLocalizedTextMap(),
      titles: this.createEmptyLocalizedTextMap(),
      descriptions: this.createEmptyLocalizedTextMap(),
      isActive: true,
      sortOrder: 0,
      saving: false,
      translating: false,
      overwriteExistingTranslations: false,
    };
  }

  private createEmptyImageForm(): ProjectImageForm {
    return {
      file: null,
      previewUrl: null,
      activeLanguage: 'it',
      translations: this.createEmptyTranslations(),
      sortOrder: 0,
      isPrimary: true,
      saving: false,
      translating: false,
      overwriteExistingTranslations: false,
    };
  }

  private createEmptyLocalizedTextMap(): LocalizedTextMap {
    return createEmptyLocalizedTextMap(this.mediaLanguages);
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

  private resetImageForm(): void {
    this.revokeImagePreview();
    this.imageForm = this.createEmptyImageForm();
    const selectedProject = this.selectedProject;
    if (selectedProject) {
      const nextSortOrder =
        (selectedProject.mediaUsages ?? [])
          .filter((usage) => usage.isActive)
          .reduce((max, usage) => Math.max(max, usage.sortOrder ?? 0), -1) + 1;
      this.imageForm.sortOrder = Math.max(0, nextSortOrder);
      this.imageForm.isPrimary = (selectedProject.mediaUsages ?? []).every(
        (usage) => !usage.isActive,
      );
    }
  }

  private revokeImagePreview(): void {
    if (this.isBrowser && this.imageForm.previewUrl) {
      URL.revokeObjectURL(this.imageForm.previewUrl);
    }
  }

  private optionalValue(value: string | null | undefined): string | null {
    if (value == null) {
      return null;
    }
    const normalized = String(value).trim();
    return normalized ? normalized : null;
  }

  private firstNonBlank(
    ...values: Array<string | null | undefined>
  ): string | null {
    for (const value of values) {
      if (value != null && String(value).trim()) {
        return String(value).trim();
      }
    }
    return null;
  }

  private clearMessages(): void {
    this.errorMessage = '';
    this.successMessage = '';
  }
}
