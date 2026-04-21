import { CommonModule, isPlatformBrowser } from '@angular/common';
import {
  Component,
  OnDestroy,
  OnInit,
  PLATFORM_ID,
  inject,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FeaturePanelComponent } from '../../../shared/components/feature-panel/feature-panel.component';
import { AppButtonComponent } from '../../../shared/components/app-button/app-button.component';
import { AppInputComponent } from '../../../shared/components/app-input/app-input.component';
import { AppTextareaComponent } from '../../../shared/components/app-textarea/app-textarea.component';
import {
  AppToggleSelectorComponent,
  ToggleOption,
} from '../../../shared/components/app-toggle-selector/app-toggle-selector.component';
import { CopyOnClickDirective } from '../../../shared/directives/copy-on-click.directive';
import {
  AdminQrDailyBreakdown,
  AdminQrLink,
  AdminQrLocationStat,
  AdminQrOverviewItem,
  AdminQrOverviewStats,
  AdminQrService,
  AdminUpsertQrLinkPayload,
} from '../services/admin-qr.service';

type QrForm = {
  name: string;
  slug: string;
  targetPath: string;
  isActive: boolean;
  notes: string;
};

type ViewMode = 'manage' | 'overview';

@Component({
  selector: 'app-admin-qr-links',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    FeaturePanelComponent,
    AppButtonComponent,
    AppInputComponent,
    AppTextareaComponent,
    AppToggleSelectorComponent,
    CopyOnClickDirective,
  ],
  templateUrl: './admin-qr-links.component.html',
  styleUrl: './admin-qr-links.component.scss',
})
export class AdminQrLinksComponent implements OnInit, OnDestroy {
  private static readonly QR_CHART_PALETTE = [
    '#12355b',
    '#1f7a8c',
    '#2d6a4f',
    '#b85c38',
    '#6b8e23',
    '#d17a22',
    '#8f3b76',
    '#6c757d',
    '#9c6644',
    '#3d5a80',
  ];

  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly adminQrService = inject(AdminQrService);

  readonly viewModeOptions: ToggleOption[] = [
    { label: 'Gestione QR', value: 'manage' },
    { label: 'Statistiche QR', value: 'overview' },
  ];

  viewMode: ViewMode = 'manage';
  qrLinks: AdminQrLink[] = [];
  selectedQrId: string | null = null;
  form: QrForm = this.createEmptyForm();
  originalSlug = '';
  loading = false;
  saving = false;
  overviewLoading = false;
  previewLoading = false;
  errorMessage: string | null = null;
  successMessage: string | null = null;
  overview: AdminQrOverviewStats | null = null;
  fromDate = this.formatDate(this.daysAgo(29));
  toDate = this.formatDate(new Date());
  previewUrl: string | null = null;

  ngOnInit(): void {
    this.loadQrLinks();
  }

  ngOnDestroy(): void {
    this.revokePreviewUrl();
  }

  setViewMode(mode: ViewMode): void {
    this.viewMode = mode;
    this.errorMessage = null;
    this.successMessage = null;
    if (mode === 'overview' && !this.overview) {
      this.loadOverviewStats();
    }
  }

  loadQrLinks(preferredId?: string | null): void {
    this.loading = true;
    this.errorMessage = null;
    this.adminQrService.listQrLinks().subscribe({
      next: (links) => {
        this.qrLinks = links;
        this.loading = false;

        if (this.viewMode === 'overview') {
          return;
        }

        const nextId =
          preferredId ?? this.selectedQrId ?? this.qrLinks[0]?.id ?? null;
        if (!nextId) {
          this.startCreateQr(false);
          return;
        }

        const nextLink = this.qrLinks.find((link) => link.id === nextId);
        if (nextLink) {
          this.selectQr(nextLink);
          return;
        }

        if (this.qrLinks.length > 0) {
          this.selectQr(this.qrLinks[0]);
        } else {
          this.startCreateQr(false);
        }
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'Impossibile caricare i QR.';
      },
    });
  }

  startCreateQr(resetMessages = true): void {
    this.viewMode = 'manage';
    this.selectedQrId = null;
    this.originalSlug = '';
    this.form = this.createEmptyForm();
    this.revokePreviewUrl();
    if (resetMessages) {
      this.errorMessage = null;
      this.successMessage = null;
    }
  }

  selectQr(link: AdminQrLink): void {
    this.viewMode = 'manage';
    this.selectedQrId = link.id;
    this.originalSlug = link.slug;
    this.form = {
      name: link.name,
      slug: link.slug,
      targetPath: link.targetPath,
      isActive: !!link.isActive,
      notes: link.notes ?? '',
    };
    this.errorMessage = null;
    this.successMessage = null;
    this.loadPreview(link.id);
  }

  saveQr(): void {
    if (this.saving) {
      return;
    }

    const payload = this.buildPayload();
    if (!payload) {
      return;
    }

    if (
      this.selectedQrId &&
      payload.slug !== this.originalSlug &&
      this.isBrowser &&
      !window.confirm(
        'Stai cambiando lo slug. I QR gia stampati con il vecchio URL smetteranno di funzionare. Continuare?',
      )
    ) {
      return;
    }

    this.saving = true;
    this.errorMessage = null;
    this.successMessage = null;

    const request$ = this.selectedQrId
      ? this.adminQrService.updateQrLink(this.selectedQrId, payload)
      : this.adminQrService.createQrLink(payload);

    request$.subscribe({
      next: (saved) => {
        this.saving = false;
        this.successMessage = this.selectedQrId
          ? 'QR aggiornato.'
          : 'QR creato.';
        this.loadQrLinks(saved.id);
        this.loadOverviewStats(false);
      },
      error: (err) => {
        this.saving = false;
        this.errorMessage =
          err?.error?.message || 'Salvataggio QR non riuscito.';
      },
    });
  }

  loadOverviewStats(resetMessages = true): void {
    this.overviewLoading = true;
    if (resetMessages) {
      this.errorMessage = null;
      this.successMessage = null;
    }
    this.adminQrService.getOverviewStats(this.fromDate, this.toDate).subscribe({
      next: (overview) => {
        this.overview = overview;
        this.overviewLoading = false;
      },
      error: () => {
        this.overviewLoading = false;
        this.errorMessage =
          'Impossibile caricare le statistiche aggregate dei QR.';
      },
    });
  }

  loadPreview(qrLinkId = this.selectedQrId): void {
    if (!qrLinkId || !this.isBrowser) {
      this.revokePreviewUrl();
      return;
    }

    this.previewLoading = true;
    this.adminQrService.downloadQrSvg(qrLinkId).subscribe({
      next: (blob) => {
        this.revokePreviewUrl();
        this.previewUrl = URL.createObjectURL(blob);
        this.previewLoading = false;
      },
      error: () => {
        this.previewLoading = false;
        this.errorMessage = "Impossibile generare l'anteprima SVG.";
      },
    });
  }

  downloadSvg(): void {
    const selected = this.selectedQr();
    if (!selected || !this.isBrowser) {
      return;
    }

    this.adminQrService.downloadQrSvg(selected.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `${selected.slug}-qr.svg`;
        anchor.click();
        URL.revokeObjectURL(url);
        this.successMessage = 'SVG scaricato.';
      },
      error: () => {
        this.errorMessage = 'Download SVG non riuscito.';
      },
    });
  }

  slugifyFromName(): void {
    const base = String(this.form.name ?? '')
      .trim()
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .replace(/-{2,}/g, '-');
    if (base) {
      this.form.slug = base;
    }
  }

  openQrFromOverview(qrLinkId: string): void {
    const qrLink = this.qrLinks.find((entry) => entry.id === qrLinkId);
    if (qrLink) {
      this.selectQr(qrLink);
    }
  }

  selectedQr(): AdminQrLink | null {
    if (!this.selectedQrId) {
      return null;
    }
    return this.qrLinks.find((link) => link.id === this.selectedQrId) ?? null;
  }

  activeQrCount(): number {
    return this.qrLinks.filter((link) => link.isActive).length;
  }

  maxDailyScans(): number {
    return Math.max(
      1,
      ...(this.overview?.daily.map((entry) => entry.scans) ?? [0]),
    );
  }

  maxOverviewScans(): number {
    return Math.max(
      1,
      ...(this.overview?.qrLinks.map((entry) => entry.rawScans) ?? [0]),
    );
  }

  maxLocationScans(): number {
    return Math.max(
      1,
      ...(this.overview?.locations.map((entry) => entry.scans) ?? [0]),
    );
  }

  dailyBarHeight(scans: number): string {
    if (scans <= 0) {
      return '0%';
    }
    return `${Math.max(10, (scans / this.maxDailyScans()) * 100)}%`;
  }

  overviewBarWidth(scans: number): string {
    return `${(scans / this.maxOverviewScans()) * 100}%`;
  }

  locationBarWidth(scans: number): string {
    return `${(scans / this.maxLocationScans()) * 100}%`;
  }

  topQrRows(): AdminQrOverviewItem[] {
    return this.overview?.qrLinks.slice(0, 12) ?? [];
  }

  dailyLegendRows(): AdminQrOverviewItem[] {
    return (
      this.overview?.qrLinks
        .filter((entry) => entry.rawScans > 0)
        .slice(0, 8) ?? []
    );
  }

  dailyBreakdownRows(row: {
    qrBreakdown: AdminQrDailyBreakdown[];
  }): AdminQrDailyBreakdown[] {
    return row.qrBreakdown ?? [];
  }

  qrColor(qrLinkId: string): string {
    const palette = AdminQrLinksComponent.QR_CHART_PALETTE;
    const hash = Array.from(String(qrLinkId ?? '')).reduce(
      (accumulator, character) =>
        (accumulator * 33 + character.charCodeAt(0)) >>> 0,
      5381,
    );
    return palette[hash % palette.length];
  }

  topLocationRows(): AdminQrLocationStat[] {
    return this.overview?.locations.slice(0, 12) ?? [];
  }

  formatTopLocation(row: AdminQrOverviewItem): string {
    if (!row.topLocationLabel) {
      return '-';
    }
    if (!row.topLocationScans) {
      return row.topLocationLabel;
    }
    return `${row.topLocationLabel} (${row.topLocationScans})`;
  }

  private buildPayload(): AdminUpsertQrLinkPayload | null {
    const name = this.form.name.trim();
    const slug = this.form.slug.trim();
    const targetPath = this.form.targetPath.trim();

    if (!name || !slug || !targetPath) {
      this.errorMessage = 'Nome, slug e target path sono obbligatori.';
      return null;
    }

    return {
      name,
      slug,
      targetPath,
      isActive: this.form.isActive,
      notes: this.form.notes.trim() || null,
    };
  }

  private createEmptyForm(): QrForm {
    return {
      name: '',
      slug: '',
      targetPath: '/',
      isActive: true,
      notes: '',
    };
  }

  private revokePreviewUrl(): void {
    if (this.previewUrl && this.isBrowser) {
      URL.revokeObjectURL(this.previewUrl);
    }
    this.previewUrl = null;
  }

  private daysAgo(days: number): Date {
    const date = new Date();
    date.setDate(date.getDate() - days);
    return date;
  }

  private formatDate(date: Date): string {
    const year = date.getFullYear();
    const month = `${date.getMonth() + 1}`.padStart(2, '0');
    const day = `${date.getDate()}`.padStart(2, '0');
    return `${year}-${month}-${day}`;
  }
}
