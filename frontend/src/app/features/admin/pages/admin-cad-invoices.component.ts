import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  AdminCadInvoice,
  AdminOperationsService,
} from '../services/admin-operations.service';
import { AdminOrdersService } from '../services/admin-orders.service';

@Component({
  selector: 'app-admin-cad-invoices',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-cad-invoices.component.html',
  styleUrl: './admin-cad-invoices.component.scss',
})
export class AdminCadInvoicesComponent implements OnInit {
  private readonly adminOperationsService = inject(AdminOperationsService);
  private readonly adminOrdersService = inject(AdminOrdersService);

  invoices: AdminCadInvoice[] = [];
  loading = false;
  creating = false;
  errorMessage: string | null = null;
  successMessage: string | null = null;

  form = {
    sessionId: '',
    sourceRequestId: '',
    cadHours: 1,
    cadHourlyRateChf: '',
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
        this.errorMessage = 'Impossibile caricare le fatture CAD.';
      },
    });
  }

  createCadInvoice(): void {
    if (this.creating) {
      return;
    }

    const cadHours = Number(this.form.cadHours);
    if (!Number.isFinite(cadHours) || cadHours <= 0) {
      this.errorMessage = 'Inserisci ore CAD valide (> 0).';
      return;
    }

    this.creating = true;
    this.errorMessage = null;
    this.successMessage = null;

    let payload: {
      sessionId?: string;
      sourceRequestId?: string;
      cadHours: number;
      cadHourlyRateChf?: number;
      notes?: string;
    };

    try {
      const sessionIdRaw = String(this.form.sessionId ?? '').trim();
      const sourceRequestIdRaw = String(this.form.sourceRequestId ?? '').trim();
      const cadRateRaw = String(this.form.cadHourlyRateChf ?? '').trim();
      const notesRaw = String(this.form.notes ?? '').trim();

      payload = {
        sessionId: sessionIdRaw || undefined,
        sourceRequestId: sourceRequestIdRaw || undefined,
        cadHours,
        cadHourlyRateChf:
          cadRateRaw.length > 0 && Number.isFinite(Number(cadRateRaw))
            ? Number(cadRateRaw)
            : undefined,
        notes: notesRaw.length > 0 ? notesRaw : undefined,
      };
    } catch {
      this.creating = false;
      this.errorMessage = 'Valori form non validi.';
      return;
    }

    this.adminOperationsService.createCadInvoice(payload).subscribe({
      next: (created) => {
        this.creating = false;
        this.successMessage = `Fattura CAD pronta. Sessione: ${created.sessionId}`;
        this.loadCadInvoices();
      },
      error: (err) => {
        this.creating = false;
        this.errorMessage =
          err?.error?.message || 'Creazione fattura CAD non riuscita.';
      },
    });
  }

  openCheckout(path: string): void {
    const url = this.toCheckoutUrl(path);
    window.open(url, '_blank');
  }

  copyCheckout(path: string): void {
    const url = this.toCheckoutUrl(path);
    navigator.clipboard?.writeText(url);
    this.successMessage = 'Link checkout CAD copiato negli appunti.';
  }

  downloadInvoice(orderId?: string): void {
    if (!orderId) return;
    this.adminOrdersService.downloadOrderInvoice(orderId).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `fattura-cad-${orderId}.pdf`;
        a.click();
        window.URL.revokeObjectURL(url);
      },
      error: () => {
        this.errorMessage = 'Download fattura non riuscito.';
      },
    });
  }

  private toCheckoutUrl(path: string): string {
    const safePath = path.startsWith('/') ? path : `/${path}`;
    const lang = this.resolveLang();
    return `${window.location.origin}/${lang}${safePath}`;
  }

  private resolveLang(): string {
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
