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
import { AppButtonComponent } from '../../../shared/components/app-button/app-button.component';
import { AppCheckboxComponent } from '../../../shared/components/app-checkbox/app-checkbox.component';
import { AppInputComponent } from '../../../shared/components/app-input/app-input.component';
import { AppTextareaComponent } from '../../../shared/components/app-textarea/app-textarea.component';

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
}

interface ProjectImageForm {
  file: File | null;
  previewUrl: string | null;
  activeLanguage: AdminMediaLanguage;
  translations: Record<AdminMediaLanguage, AdminMediaTranslation>;
  sortOrder: number;
  isPrimary: boolean;
  saving: boolean;
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
  selector: 'app-admin-home-projects',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    AppButtonComponent,
    AppCheckboxComponent,
    AppInputComponent,
    AppTextareaComponent,
  ],
  templateUrl: './admin-home-projects.component.html',
  styleUrl: './admin-home-projects.component.scss',
})
export class AdminHomeProjectsComponent implements OnInit, OnDestroy {
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly projectService = inject(AdminHomeProjectService);
  private readonly mediaService = inject(AdminMediaService);

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
    };
  }

  private createEmptyLocalizedTextMap(): LocalizedTextMap {
    return {
      it: '',
      en: '',
      de: '',
      fr: '',
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
