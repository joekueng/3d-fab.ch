import {
  ServiceLine,
  serviceLineTotal,
} from '../../../shared/models/service-line';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { Component, OnDestroy, OnInit, PLATFORM_ID, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AppSelectComponent } from '../../../shared/components/app-select/app-select.component';
import { AppDialogComponent } from '../../../shared/components/app-dialog/app-dialog.component';
import {
  AdminCadInvoice,
  AdminOperationsService,
  AdminQuoteItemStats,
  AdminQuoteItemStatsUpdate,
  AdminQuoteItemStatsUpdatePayload,
  AdminQuoteItemsResponse,
  QuoteSessionAttachment,
} from '../services/admin-operations.service';
import { AdminOrdersService } from '../services/admin-orders.service';
import { CopyOnClickDirective } from '../../../shared/directives/copy-on-click.directive';
import { AppButtonComponent } from '../../../shared/components/app-button/app-button.component';
import { AppInputComponent } from '../../../shared/components/app-input/app-input.component';
import { AppTextareaComponent } from '../../../shared/components/app-textarea/app-textarea.component';
import { downloadBlobInBrowser } from '../../../core/utils/browser-download';

export function parseDecimalInput(value: unknown): number {
  const normalized = String(value ?? '')
    .trim()
    .replace(',', '.');

  if (!/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)$/.test(normalized)) {
    return Number.NaN;
  }

  return Number(normalized);
}

interface ServiceLineForm {
  description: string;
  billingType: 'HOURLY' | 'FIXED';
  quantity: string;
  unitPriceChf: string;
}

export interface HoursMinutes {
  hours: number;
  minutes: number;
}

export function secondsToHoursMinutes(seconds: number): HoursMinutes {
  const safeSeconds =
    Number.isFinite(seconds) && seconds > 0 ? Math.floor(seconds) : 0;
  const totalMinutes = Math.floor(safeSeconds / 60);
  return {
    hours: Math.floor(totalMinutes / 60),
    minutes: totalMinutes % 60,
  };
}

export function hoursMinutesToSeconds(
  hours: unknown,
  minutes: unknown,
): number {
  const parsedHours = parseDecimalInput(hours);
  const parsedMinutes = parseDecimalInput(minutes);
  if (
    !Number.isInteger(parsedHours) ||
    parsedHours < 0 ||
    !Number.isInteger(parsedMinutes) ||
    parsedMinutes < 0 ||
    parsedMinutes > 59
  ) {
    return Number.NaN;
  }
  return parsedHours * 3600 + parsedMinutes * 60;
}

interface ManagedItemRow {
  item: AdminQuoteItemStats;
  hours: string;
  minutes: string;
  grams: string;
}

@Component({
  selector: 'app-admin-cad-invoices',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TranslateModule,
    AppSelectComponent,
    AppDialogComponent,
    CopyOnClickDirective,
    AppButtonComponent,
    AppInputComponent,
    AppTextareaComponent,
  ],
  templateUrl: './admin-cad-invoices.component.html',
  styleUrl: './admin-cad-invoices.component.scss',
})
export class AdminCadInvoicesComponent implements OnInit, OnDestroy {
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly adminOperationsService = inject(AdminOperationsService);
  private readonly adminOrdersService = inject(AdminOrdersService);

  private readonly translate = inject(TranslateService);
  invoices: AdminCadInvoice[] = [];
  editingSessionId: string | null = null;
  metadata = { invoiceName: '', collaborationName: '', clientName: '' };
  savingMetadata = false;
  metadataError: string | null = null;

  get collaborationOptions(): { label: string; value: string }[] {
    return [
      { label: this.translate.instant('CAD_ORGANIZATION.NONE'), value: '' },
      ...[
        ...new Set(
          this.invoices
            .map((row) => row.collaborationName)
            .filter((name): name is string => Boolean(name)),
        ),
      ]
        .sort((a, b) => a.localeCompare(b))
        .map((name) => ({ label: name, value: name })),
    ];
  }

  get invoiceGroups(): {
    name: string;
    clientName: string;
    rows: AdminCadInvoice[];
  }[] {
    const groups = new Map<
      string,
      { name: string; clientName: string; rows: AdminCadInvoice[] }
    >();
    for (const row of this.invoices) {
      const name = row.collaborationName || '';
      const clientName = row.clientName || '';
      const key = JSON.stringify([clientName, name]);
      const group = groups.get(key) || { name, clientName, rows: [] };
      group.rows.push(row);
      groups.set(key, group);
    }
    return [...groups.values()].sort(
      (a, b) =>
        a.clientName.localeCompare(b.clientName) ||
        a.name.localeCompare(b.name),
    );
  }

  trackGroup(
    _index: number,
    group: { name: string; clientName: string },
  ): string {
    return JSON.stringify([group.clientName, group.name]);
  }

  trackInvoice(_index: number, row: AdminCadInvoice): string {
    return row.sessionId;
  }

  get draftServicesTotal(): number {
    return this.serviceLines.reduce(
      (total, line) => total + this.lineTotal(line),
      0,
    );
  }

  newInterimInvoice(group: { name: string; clientName: string }): void {
    this.resetForm();
    this.form.clientName = group.clientName;
    this.form.collaborationName = group.name;
    if (this.isBrowser) window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  editMetadata(row: AdminCadInvoice): void {
    this.editingSessionId = row.sessionId;
    this.metadata = {
      invoiceName: row.invoiceName || '',
      clientName: row.clientName || '',
      collaborationName: row.collaborationName || '',
    };
    this.metadataError = null;
  }

  closeMetadata(): void {
    if (!this.savingMetadata) this.editingSessionId = null;
  }

  saveMetadata(): void {
    if (!this.editingSessionId || this.savingMetadata) return;
    if (!this.validLabels(this.metadata)) {
      this.metadataError = this.translate.instant('CAD_ORGANIZATION.TOO_LONG');
      return;
    }
    this.savingMetadata = true;
    this.metadataError = null;
    this.adminOperationsService
      .updateCadInvoiceMetadata(this.editingSessionId, this.metadata)
      .subscribe({
        next: () => {
          this.savingMetadata = false;
          this.editingSessionId = null;
          this.successMessage = this.translate.instant(
            'CAD_ORGANIZATION.SAVED',
          );
          this.loadCadInvoices();
        },
        error: () => {
          this.savingMetadata = false;
          this.metadataError = this.translate.instant(
            'CAD_ORGANIZATION.SAVE_ERROR',
          );
        },
      });
  }

  private validLabels(value: {
    invoiceName: string;
    collaborationName: string;
    clientName: string;
  }): boolean {
    return (
      value.invoiceName.length <= 160 &&
      value.collaborationName.length <= 160 &&
      value.clientName.length <= 160
    );
  }
  loading = false;
  creating = false;
  errorMessage: string | null = null;
  successMessage: string | null = null;

  managedSessionId: string | null = null;
  managedSessionStatus: string | null = null;
  managedItemsTotalChf: number | null = null;
  managedPreviewTotalChf: number | null = null;
  managedRows: ManagedItemRow[] = [];
  managedLoading = false;
  sessionFilesLoaded = false;
  previewing = false;
  savingManaged = false;
  managedPreviewActive = false;
  managedError: string | null = null;

  sessionAttachments: QuoteSessionAttachment[] = [];
  pendingAttachmentFiles: File[] = [];
  attachmentPreviews: Record<string, string> = {};
  attachmentError: string | null = null;
  attachmentUploading = false;
  deletingAttachmentId: string | null = null;
  readonly attachmentMaxFiles = 15;
  readonly attachmentMaxFileSizeBytes = 50 * 1024 * 1024;

  form = {
    clientName: '',
    invoiceName: '',
    collaborationName: '',
    sessionId: '',
    sourceRequestId: '',
    notes: '',
  };

  ngOnInit(): void {
    this.loadCadInvoices();
  }

  loadCadInvoices(): void {
    this.loading = true;
    this.errorMessage = null;
    this.adminOperationsService.listCadInvoices().subscribe({
      next: (rows) => {
        this.invoices = rows;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.errorMessage = this.translate.instant(
          'CAD_ORGANIZATION.LOAD_ERROR',
        );
      },
    });
  }

  serviceLines: ServiceLineForm[] = [this.newServiceLine()];

  get billingOptions(): { label: string; value: string }[] {
    return ['HOURLY', 'FIXED'].map((value) => ({
      label: this.translate.instant('CAD_ORGANIZATION.' + value),
      value,
    }));
  }

  newServiceLine(): ServiceLineForm {
    return {
      description: '',
      billingType: 'HOURLY',
      quantity: '1',
      unitPriceChf: '',
    };
  }

  lineTotal(line: ServiceLineForm): number {
    const quantity =
      line.billingType === 'FIXED' ? 1 : parseDecimalInput(line.quantity);
    const rate = parseDecimalInput(line.unitPriceChf);
    return Number.isFinite(quantity * rate)
      ? serviceLineTotal(quantity, rate)
      : 0;
  }

  servicesTotal(row: AdminCadInvoice): number {
    return (row.serviceLines || []).reduce(
      (total, line) =>
        total +
        (line.lineTotalChf ??
          serviceLineTotal(line.quantity, line.unitPriceChf)),
      row.cadTotalChf || 0,
    );
  }

  editInvoice(row: AdminCadInvoice): void {
    this.form = {
      invoiceName: row.invoiceName || '',
      clientName: row.clientName || '',
      collaborationName: row.collaborationName || '',
      sessionId: row.sessionId,
      sourceRequestId: row.sourceRequestId || '',
      notes: row.notes || '',
    };
    this.serviceLines = row.serviceLines?.length
      ? row.serviceLines.map((line) => ({
          ...line,
          quantity: String(line.quantity),
          unitPriceChf: String(line.unitPriceChf),
        }))
      : [
          {
            description: this.translate.instant('CHECKOUT.CAD_SERVICE'),
            billingType: 'HOURLY',
            quantity: String(row.cadHours),
            unitPriceChf: String(row.cadHourlyRateChf),
          },
        ];
    this.errorMessage = null;
    this.loadSessionFiles(row.sessionId);
    if (this.isBrowser) window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  resetForm(): void {
    this.form = {
      clientName: '',
      invoiceName: '',
      collaborationName: '',
      sessionId: '',
      sourceRequestId: '',
      notes: '',
    };
    this.serviceLines = [this.newServiceLine()];
    this.errorMessage = null;
    this.clearSessionFiles();
  }

  createCadInvoice(): void {
    if (this.creating) return;
    if (!this.validLabels(this.form)) {
      this.errorMessage = this.translate.instant('CAD_ORGANIZATION.TOO_LONG');
      return;
    }
    const serviceLines: ServiceLine[] = this.serviceLines.map((line) => ({
      description: line.description.trim(),
      billingType: line.billingType,
      quantity:
        line.billingType === 'FIXED' ? 1 : parseDecimalInput(line.quantity),
      unitPriceChf: parseDecimalInput(line.unitPriceChf),
    }));
    if (
      !serviceLines.length ||
      serviceLines.some(
        (line) =>
          !line.description ||
          line.description.length > 2000 ||
          !Number.isFinite(line.quantity) ||
          line.quantity < 0.01 ||
          line.quantity > 99999 ||
          !Number.isFinite(line.unitPriceChf) ||
          line.unitPriceChf < 0 ||
          line.unitPriceChf > 999999 ||
          Math.abs(line.quantity * 100 - Math.round(line.quantity * 100)) >
            0.000001 ||
          Math.abs(
            line.unitPriceChf * 100 - Math.round(line.unitPriceChf * 100),
          ) > 0.000001,
      )
    ) {
      this.errorMessage = this.translate.instant(
        'CAD_ORGANIZATION.INVALID_LINES',
      );
      return;
    }
    this.creating = true;
    this.errorMessage = null;
    this.successMessage = null;
    this.adminOperationsService
      .createCadInvoice({
        clientName: this.form.clientName.trim(),
        invoiceName: this.form.invoiceName.trim(),
        collaborationName: this.form.collaborationName.trim(),
        sessionId: this.form.sessionId.trim() || undefined,
        sourceRequestId: this.form.sourceRequestId.trim() || undefined,
        notes: this.form.notes.trim(),
        serviceLines,
      })
      .subscribe({
        next: (created) => {
          this.creating = false;
          this.successMessage = this.translate.instant(
            'CAD_ORGANIZATION.READY',
          );
          const pendingFiles = [...this.pendingAttachmentFiles];
          this.resetForm();
          if (created?.sessionId) {
            this.form.sessionId = created.sessionId;
            this.loadSessionFiles(created.sessionId);
            if (pendingFiles.length > 0) {
              this.uploadAttachments(created.sessionId, pendingFiles);
            }
          }
          this.loadCadInvoices();
        },
        error: () => {
          this.creating = false;
          this.errorMessage = this.translate.instant(
            'CAD_ORGANIZATION.CREATE_ERROR',
          );
        },
      });
  }

  onSessionIdChange(): void {
    const sessionId = String(this.form.sessionId ?? '').trim();
    if (!this.isUuid(sessionId)) {
      this.clearSessionFiles();
      return;
    }
    if (sessionId === this.managedSessionId) {
      return;
    }
    this.loadSessionFiles(sessionId);
  }

  manageItems(sessionId: string): void {
    this.form.sessionId = sessionId;
    this.loadSessionFiles(sessionId);
    if (this.isBrowser) window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  loadSessionFiles(sessionId?: string): void {
    const target = String(sessionId ?? this.form.sessionId ?? '').trim();
    if (!target) {
      this.managedError = this.translate.instant(
        'CAD_ITEM_PRICING.SESSION_REQUIRED',
      );
      return;
    }
    this.managedSessionId = target;
    this.sessionFilesLoaded = false;
    this.managedError = null;
    this.attachmentError = null;
    this.successMessage = null;
    this.attachmentUploading = false;
    this.deletingAttachmentId = null;
    this.clearAttachmentPreviews();
    this.loadManagedItems(target);
    this.loadSessionAttachments(target);
  }

  loadSessionAttachments(sessionId?: string): void {
    const target = String(sessionId ?? this.managedSessionId ?? '').trim();
    if (!target) {
      return;
    }
    this.adminOperationsService.listQuoteSessionAttachments(target).subscribe({
      next: (attachments) => {
        if (this.managedSessionId !== target) {
          return;
        }
        this.sessionAttachments = attachments;
        this.loadAttachmentPreviews(target);
      },
      error: (err) => {
        if (this.managedSessionId !== target) {
          return;
        }
        this.sessionAttachments = [];
        this.attachmentError =
          err?.error?.message ||
          this.translate.instant('CAD_ITEM_PRICING.ATTACH_ERROR');
      },
    });
  }

  onAttachmentFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    const files = Array.from(input?.files ?? []);
    if (input) {
      input.value = '';
    }
    if (
      files.length === 0 ||
      !this.canEditManagedItems() ||
      this.attachmentUploading
    ) {
      return;
    }
    if (
      this.sessionAttachments.length +
        this.pendingAttachmentFiles.length +
        files.length >
      this.attachmentMaxFiles
    ) {
      this.attachmentError = this.translate.instant(
        'CAD_ITEM_PRICING.ATTACH_TOO_MANY',
        { count: this.attachmentMaxFiles },
      );
      return;
    }
    const oversized = files.find(
      (file) => file.size > this.attachmentMaxFileSizeBytes,
    );
    if (oversized) {
      this.attachmentError = this.translate.instant(
        'CAD_ITEM_PRICING.ATTACH_TOO_LARGE',
        { name: oversized.name },
      );
      return;
    }
    this.attachmentError = null;

    if (!this.managedSessionId) {
      this.pendingAttachmentFiles = [...this.pendingAttachmentFiles, ...files];
      return;
    }
    this.uploadAttachments(this.managedSessionId, files);
  }

  removePendingAttachment(index: number): void {
    this.pendingAttachmentFiles = this.pendingAttachmentFiles.filter(
      (_file, fileIndex) => fileIndex !== index,
    );
  }

  private uploadAttachments(sessionId: string, files: File[]): void {
    if (files.length === 0) {
      return;
    }
    this.attachmentUploading = true;
    this.attachmentError = null;
    this.adminOperationsService
      .uploadQuoteSessionAttachments(sessionId, files)
      .subscribe({
        next: () => {
          if (this.managedSessionId !== sessionId) {
            return;
          }
          this.attachmentUploading = false;
          this.pendingAttachmentFiles = [];
          this.successMessage = this.translate.instant(
            'CAD_ITEM_PRICING.ATTACH_UPLOADED',
          );
          this.loadSessionAttachments(sessionId);
        },
        error: (err) => {
          if (this.managedSessionId !== sessionId) {
            return;
          }
          this.attachmentUploading = false;
          this.attachmentError =
            err?.error?.message ||
            this.translate.instant('CAD_ITEM_PRICING.ATTACH_ERROR');
        },
      });
  }

  deleteAttachment(attachment: QuoteSessionAttachment): void {
    if (!this.managedSessionId || this.deletingAttachmentId) {
      return;
    }
    const sessionId = this.managedSessionId;
    this.deletingAttachmentId = attachment.id;
    this.attachmentError = null;
    this.adminOperationsService
      .deleteQuoteSessionAttachment(sessionId, attachment.id)
      .subscribe({
        next: () => {
          if (this.managedSessionId !== sessionId) {
            return;
          }
          this.deletingAttachmentId = null;
          this.revokeAttachmentPreview(attachment.id);
          this.sessionAttachments = this.sessionAttachments.filter(
            (item) => item.id !== attachment.id,
          );
        },
        error: (err) => {
          if (this.managedSessionId !== sessionId) {
            return;
          }
          this.deletingAttachmentId = null;
          this.attachmentError =
            err?.error?.message ||
            this.translate.instant('CAD_ITEM_PRICING.ATTACH_ERROR');
        },
      });
  }

  attachmentPreviewUrl(attachmentId: string): string | null {
    return this.attachmentPreviews[attachmentId] ?? null;
  }

  ngOnDestroy(): void {
    this.clearAttachmentPreviews();
  }

  private loadManagedItems(sessionId: string): void {
    this.managedLoading = true;
    this.managedPreviewActive = false;
    this.managedPreviewTotalChf = null;
    this.adminOperationsService.getAdminQuoteItems(sessionId).subscribe({
      next: (response) => {
        if (this.managedSessionId !== sessionId) {
          return;
        }
        this.managedLoading = false;
        this.sessionFilesLoaded = true;
        this.applyManagedResponse(response);
      },
      error: (err) => {
        if (this.managedSessionId !== sessionId) {
          return;
        }
        this.managedLoading = false;
        this.sessionFilesLoaded = true;
        this.managedSessionStatus = null;
        this.managedItemsTotalChf = null;
        this.managedRows = [];
        this.managedError =
          err?.error?.message ||
          this.translate.instant('CAD_ITEM_PRICING.LOAD_ERROR');
      },
    });
  }

  private loadAttachmentPreviews(sessionId: string): void {
    if (!this.isBrowser) {
      return;
    }
    for (const attachment of this.sessionAttachments) {
      if (!attachment.image || this.attachmentPreviews[attachment.id]) {
        continue;
      }
      this.adminOperationsService
        .getQuoteSessionAttachmentPreview(sessionId, attachment.id)
        .subscribe({
          next: (blob) => {
            if (
              !this.isBrowser ||
              this.managedSessionId !== sessionId ||
              this.attachmentPreviews[attachment.id]
            ) {
              return;
            }
            this.attachmentPreviews = {
              ...this.attachmentPreviews,
              [attachment.id]: URL.createObjectURL(blob),
            };
          },
          error: () => {},
        });
    }
  }

  private clearAttachmentPreviews(): void {
    if (this.isBrowser) {
      for (const url of Object.values(this.attachmentPreviews)) {
        URL.revokeObjectURL(url);
      }
    }
    this.attachmentPreviews = {};
  }

  private revokeAttachmentPreview(attachmentId: string): void {
    const url = this.attachmentPreviews[attachmentId];
    if (url && this.isBrowser) {
      URL.revokeObjectURL(url);
    }
    const { [attachmentId]: _removed, ...rest } = this.attachmentPreviews;
    this.attachmentPreviews = rest;
  }

  private clearSessionFiles(): void {
    this.managedSessionId = null;
    this.managedSessionStatus = null;
    this.managedItemsTotalChf = null;
    this.managedPreviewTotalChf = null;
    this.managedRows = [];
    this.managedPreviewActive = false;
    this.managedError = null;
    this.sessionFilesLoaded = false;
    this.sessionAttachments = [];
    this.pendingAttachmentFiles = [];
    this.attachmentError = null;
    this.attachmentUploading = false;
    this.deletingAttachmentId = null;
    this.clearAttachmentPreviews();
  }

  private isUuid(value: string): boolean {
    return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
      value,
    );
  }

  onManagedDraftChange(): void {
    this.managedPreviewActive = false;
    this.managedPreviewTotalChf = null;
    this.managedRows = this.managedRows.map((row) => ({
      ...row,
      item: { ...row.item, newUnitPriceChf: null },
    }));
  }

  canEditManagedItems(): boolean {
    return this.managedSessionStatus !== 'CONVERTED';
  }

  isRowEditable(row: ManagedItemRow): boolean {
    return row.item.editable && this.canEditManagedItems();
  }

  previewManagedChanges(): void {
    this.submitManagedChanges(false);
  }

  saveManagedChanges(): void {
    if (!this.managedPreviewActive) {
      return;
    }
    this.submitManagedChanges(true);
  }

  openCheckout(path: string): void {
    if (!this.isBrowser) {
      return;
    }
    const url = this.toCheckoutUrl(path);
    window.open(url, '_blank');
  }

  copyCheckout(path: string): void {
    if (!this.isBrowser) {
      return;
    }
    const url = this.toCheckoutUrl(path);
    navigator.clipboard?.writeText(url);
    this.successMessage = this.translate.instant('CAD_ORGANIZATION.COPIED');
  }

  downloadInvoice(orderId?: string): void {
    if (!orderId) return;
    this.adminOrdersService.downloadOrderInvoice(orderId).subscribe({
      next: (blob) => {
        if (!this.isBrowser) {
          return;
        }
        downloadBlobInBrowser(blob, `fattura-cad-${orderId}.pdf`);
      },
      error: () => {
        this.errorMessage = this.translate.instant(
          'CAD_ORGANIZATION.DOWNLOAD_ERROR',
        );
      },
    });
  }

  private submitManagedChanges(persist: boolean): void {
    if (this.previewing || this.savingManaged || !this.canEditManagedItems()) {
      return;
    }
    const payload = this.buildManagedPayload(persist);
    if (!payload) {
      return;
    }

    const sessionId =
      this.managedSessionId ?? String(this.form.sessionId).trim();
    if (persist) {
      this.savingManaged = true;
    } else {
      this.previewing = true;
    }
    this.managedError = null;
    this.successMessage = null;

    this.adminOperationsService
      .updateAdminQuoteItemStats(sessionId, payload)
      .subscribe({
        next: (response) => {
          this.previewing = false;
          this.savingManaged = false;
          if (persist) {
            this.successMessage = this.translate.instant(
              'CAD_ITEM_PRICING.SAVED',
            );
            this.managedPreviewActive = false;
            this.managedPreviewTotalChf = null;
            this.applyManagedResponse(response);
            this.loadCadInvoices();
          } else {
            this.managedRows = this.managedRows.map((row) => {
              const updated = response.items.find(
                (item) => item.id === row.item.id,
              );
              return updated ? { ...row, item: updated } : row;
            });
            this.managedPreviewActive = true;
            this.managedPreviewTotalChf = response.grandTotalChf;
          }
        },
        error: (err) => {
          this.previewing = false;
          this.savingManaged = false;
          this.managedError =
            err?.error?.message ||
            this.translate.instant(
              persist
                ? 'CAD_ITEM_PRICING.SAVE_ERROR'
                : 'CAD_ITEM_PRICING.PREVIEW_ERROR',
            );
        },
      });
  }

  private buildManagedPayload(
    persist: boolean,
  ): AdminQuoteItemStatsUpdatePayload | null {
    const items: AdminQuoteItemStatsUpdate[] = [];
    for (const row of this.managedRows) {
      if (!row.item.editable) {
        continue;
      }
      const printTimeSeconds = hoursMinutesToSeconds(row.hours, row.minutes);
      const materialGrams = parseDecimalInput(row.grams);
      if (
        !Number.isFinite(printTimeSeconds) ||
        printTimeSeconds < 1 ||
        !Number.isFinite(materialGrams) ||
        materialGrams <= 0
      ) {
        this.managedError = this.translate.instant(
          'CAD_ITEM_PRICING.INVALID_VALUES',
          { name: row.item.displayName || row.item.id },
        );
        return null;
      }
      items.push({ itemId: row.item.id, printTimeSeconds, materialGrams });
    }

    if (items.length === 0) {
      this.managedError = this.translate.instant(
        'CAD_ITEM_PRICING.NO_EDITABLE_FILES',
      );
      return null;
    }
    return { persist, items };
  }

  private applyManagedResponse(response: AdminQuoteItemsResponse): void {
    this.managedSessionId = response.sessionId;
    this.managedSessionStatus = response.sessionStatus;
    this.managedItemsTotalChf = response.grandTotalChf;
    this.managedRows = response.items.map((item) => {
      const parts = secondsToHoursMinutes(item.printTimeSeconds ?? 0);
      return {
        item,
        hours: String(parts.hours),
        minutes: String(parts.minutes),
        grams: item.materialGrams != null ? String(item.materialGrams) : '',
      };
    });
  }

  private toCheckoutUrl(path: string): string {
    const safePath = path.startsWith('/') ? path : `/${path}`;
    const lang = this.resolveLang();
    if (!this.isBrowser) {
      return `/${lang}${safePath}`;
    }
    return `${window.location.origin}/${lang}${safePath}`;
  }

  private resolveLang(): string {
    if (!this.isBrowser) {
      return 'it';
    }
    const firstSegment = window.location.pathname
      .split('/')
      .filter(Boolean)
      .shift();
    if (firstSegment && ['it', 'en', 'de', 'fr'].includes(firstSegment)) {
      return firstSegment;
    }
    return 'it';
  }
}
