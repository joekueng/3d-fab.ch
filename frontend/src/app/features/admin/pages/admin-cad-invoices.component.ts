import {
  ServiceLine,
  serviceLineTotal,
} from '../../../shared/models/service-line';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { Component, OnInit, PLATFORM_ID, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { AppSelectComponent } from '../../../shared/components/app-select/app-select.component';
import { AppDialogComponent } from '../../../shared/components/app-dialog/app-dialog.component';
import {
  AdminCadInvoice,
  AdminOperationsService,
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

@Component({
  selector: 'app-admin-cad-invoices',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TranslatePipe,
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
export class AdminCadInvoicesComponent implements OnInit {
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
        next: () => {
          this.creating = false;
          this.successMessage = this.translate.instant(
            'CAD_ORGANIZATION.READY',
          );
          this.resetForm();
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
