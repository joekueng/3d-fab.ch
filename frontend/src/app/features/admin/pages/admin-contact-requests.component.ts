import { TranslateService } from '@ngx-translate/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import {
  ChangeDetectorRef,
  Component,
  DestroyRef,
  PLATFORM_ID,
  inject,
  OnInit,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import {
  AdminContactRequest,
  AdminContactRequestAttachment,
  AdminContactRequestDetail,
  AdminOperationsService,
} from '../services/admin-operations.service';
import { AdminEmailLog } from '../services/admin-email-log.model';
import { CopyOnClickDirective } from '../../../shared/directives/copy-on-click.directive';
import { AppButtonComponent } from '../../../shared/components/app-button/app-button.component';
import { AppPaginationComponent } from '../../../shared/components/app-pagination/app-pagination.component';
import { AppSelectComponent } from '../../../shared/components/app-select/app-select.component';
import { downloadBlobInBrowser } from '../../../core/utils/browser-download';
import { ModelPreviewDialogComponent } from '../../../shared/components/model-preview-dialog/model-preview-dialog.component';
import { ModelPreviewService } from '../../calculator/services/model-preview.service';
import { TranslatePipe } from '@ngx-translate/core';
import { Observable, of, Subscription, switchMap } from 'rxjs';
import { AdminPage, loadAdminPage } from '../services/admin-page.model';

@Component({
  selector: 'app-admin-contact-requests',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    CopyOnClickDirective,
    AppButtonComponent,
    AppPaginationComponent,
    AppSelectComponent,
    ModelPreviewDialogComponent,
    TranslatePipe,
  ],
  templateUrl: './admin-contact-requests.component.html',
  styleUrl: './admin-contact-requests.component.scss',
})
export class AdminContactRequestsComponent implements OnInit {
  preview: { filename: string; source: Observable<Blob> } | null = null;
  private readonly modelPreviews = inject(ModelPreviewService);

  canPreview(filename: string): boolean {
    return /\.(stl|3mf|obj|step|stp)$/i.test(filename.trim());
  }

  openPreview(attachment: AdminContactRequestAttachment): void {
    if (!this.selectedRequest || !this.canPreview(attachment.originalFilename))
      return;
    const filename = attachment.originalFilename;
    this.preview = {
      filename,
      source: this.adminOperationsService
        .downloadContactRequestAttachment(
          this.selectedRequest.id,
          attachment.id,
        )
        .pipe(
          switchMap((blob) =>
            /\.stl$/i.test(filename.trim())
              ? of(blob)
              : this.modelPreviews.createStlPreview(new File([blob], filename)),
          ),
        ),
    };
  }
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly translate = inject(TranslateService);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly adminOperationsService = inject(AdminOperationsService);
  private readonly destroyRef = inject(DestroyRef);
  private listRequest?: Subscription;
  private detailRequest?: Subscription;
  private requestedPage = 0;

  readonly statusOptions = ['NEW', 'PENDING', 'IN_PROGRESS', 'DONE', 'CLOSED'];
  readonly statusSelectOptions = this.statusOptions.map((status) => ({
    label: status,
    value: status,
  }));
  requestsPage: AdminPage<AdminContactRequest> | null = null;
  selectedRequest: AdminContactRequestDetail | null = null;
  selectedRequestId: string | null = null;
  loading = false;
  detailLoading = false;
  updatingStatus = false;
  selectedStatus = '';
  errorMessage: string | null = null;
  successMessage: string | null = null;
  resendingEmailLogIds = new Set<string>();

  ngOnInit(): void {
    this.loadRequests();
  }

  loadRequests(page = this.requestedPage): void {
    this.listRequest?.unsubscribe();
    this.requestedPage = page;
    this.loading = true;
    this.errorMessage = null;
    this.successMessage = null;
    this.listRequest = loadAdminPage(
      (index) => this.adminOperationsService.getContactRequests(index),
      page,
    )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.changeDetector.markForCheck();
          this.requestedPage = result.page;
          this.requestsPage = result;
          this.loading = false;
          const retained = result.items.find(
            (request) => request.id === this.selectedRequestId,
          );
          const selected = retained ?? result.items[0];
          if (selected) {
            this.openDetails(selected.id);
          } else {
            this.clearSelection();
          }
        },
        error: () => {
          this.changeDetector.markForCheck();
          this.loading = false;
          this.requestsPage = null;
          this.clearSelection();
          this.errorMessage = 'Impossibile caricare le richieste di contatto.';
        },
      });
  }

  openDetails(requestId: string): void {
    if (requestId !== this.selectedRequestId) {
      this.clearSelection();
    }
    this.detailRequest?.unsubscribe();
    this.selectedRequestId = requestId;
    this.detailLoading = true;
    this.errorMessage = null;
    this.detailRequest = this.adminOperationsService
      .getContactRequestDetail(requestId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (detail) => {
          this.changeDetector.markForCheck();
          this.selectedRequest = detail;
          this.selectedStatus = detail.status || '';
          this.detailLoading = false;
        },
        error: () => {
          this.changeDetector.markForCheck();
          this.detailLoading = false;
          this.errorMessage = 'Impossibile caricare il dettaglio richiesta.';
        },
      });
  }

  private clearSelection(): void {
    this.detailRequest?.unsubscribe();
    this.selectedRequest = null;
    this.selectedRequestId = null;
    this.selectedStatus = '';
    this.detailLoading = false;
    this.preview = null;
  }

  isSelected(requestId: string): boolean {
    return this.selectedRequestId === requestId;
  }

  downloadAttachment(attachment: AdminContactRequestAttachment): void {
    if (!this.selectedRequest) {
      return;
    }

    this.adminOperationsService
      .downloadContactRequestAttachment(this.selectedRequest.id, attachment.id)
      .subscribe({
        next: (blob) =>
          this.downloadBlob(
            blob,
            attachment.originalFilename || `attachment-${attachment.id}`,
          ),
        error: () => {
          this.changeDetector.markForCheck();
          this.errorMessage = 'Download allegato non riuscito.';
        },
      });
  }

  formatFileSize(bytes?: number): string {
    if (!bytes || bytes <= 0) {
      return '-';
    }
    const units = ['B', 'KB', 'MB', 'GB'];
    let value = bytes;
    let unitIndex = 0;
    while (value >= 1024 && unitIndex < units.length - 1) {
      value /= 1024;
      unitIndex += 1;
    }
    return `${value.toFixed(value >= 10 || unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
  }

  getStatusChipClass(status?: string): string {
    const normalized = (status || '').trim().toUpperCase();
    if (['PENDING', 'NEW', 'OPEN', 'IN_PROGRESS'].includes(normalized)) {
      return 'ui-status-chip--warning';
    }
    if (['DONE', 'COMPLETED', 'RESOLVED', 'CLOSED'].includes(normalized)) {
      return 'ui-status-chip--success';
    }
    if (['REJECTED', 'FAILED', 'ERROR', 'SPAM'].includes(normalized)) {
      return 'ui-status-chip--danger';
    }
    return 'ui-status-chip--neutral';
  }

  updateRequestStatus(): void {
    if (
      !this.selectedRequest ||
      !this.selectedRequestId ||
      !this.selectedStatus ||
      this.updatingStatus
    ) {
      return;
    }

    this.errorMessage = null;
    this.successMessage = null;
    this.updatingStatus = true;

    this.adminOperationsService
      .updateContactRequestStatus(this.selectedRequestId, {
        status: this.selectedStatus,
      })
      .subscribe({
        next: (updated) => {
          this.changeDetector.markForCheck();
          this.applyRequestDetail(updated);
          if (this.requestsPage) {
            this.requestsPage = {
              ...this.requestsPage,
              items: this.requestsPage.items.map((request) =>
                request.id === updated.id
                  ? { ...request, status: updated.status }
                  : request,
              ),
            };
          }
          this.updatingStatus = false;
          this.successMessage = 'Stato richiesta aggiornato.';
        },
        error: () => {
          this.changeDetector.markForCheck();
          this.updatingStatus = false;
          this.errorMessage =
            'Impossibile aggiornare lo stato della richiesta.';
        },
      });
  }

  resendEmail(log: AdminEmailLog): void {
    if (
      !this.selectedRequest ||
      !this.selectedRequestId ||
      this.resendingEmailLogIds.has(log.id)
    ) {
      return;
    }

    this.errorMessage = null;
    this.successMessage = null;
    this.resendingEmailLogIds.add(log.id);
    this.adminOperationsService
      .resendContactRequestEmail(this.selectedRequestId, log.id)
      .subscribe({
        next: (updated) => {
          this.changeDetector.markForCheck();
          this.resendingEmailLogIds.delete(log.id);
          this.applyRequestDetail(updated);
          this.successMessage = 'Email reinviata.';
        },
        error: () => {
          this.changeDetector.markForCheck();
          this.resendingEmailLogIds.delete(log.id);
          this.errorMessage = 'Reinvio email non riuscito.';
        },
      });
  }

  private applyRequestDetail(updated: AdminContactRequestDetail): void {
    if (this.selectedRequestId === updated.id) {
      this.detailRequest?.unsubscribe();
      this.detailLoading = false;
      this.selectedRequest = updated;
      this.selectedStatus = updated.status || this.selectedStatus;
    }
  }

  emailLogs(request: AdminContactRequestDetail | null): AdminEmailLog[] {
    return request?.emailLogs ?? [];
  }

  emailEventLabel(eventType?: string): string {
    switch ((eventType || '').trim().toUpperCase()) {
      case 'CONTACT_REQUEST_ADMIN':
        return 'Notifica richiesta admin';
      case 'CONTACT_REQUEST_CUSTOMER':
        return 'Conferma richiesta cliente';
      default:
        return eventType || '-';
    }
  }

  emailStatusLabel(status?: string): string {
    switch ((status || '').trim().toUpperCase()) {
      case 'SENT':
        return 'Inviata';
      case 'FAILED':
        return 'Fallita';
      case 'SKIPPED':
        return 'Saltata';
      case 'UNKNOWN':
        return this.translate.instant('ADMIN.EMAIL_STATUS_UNKNOWN');
      default:
        return status || '-';
    }
  }

  emailStatusClass(status?: string): string {
    switch ((status || '').trim().toUpperCase()) {
      case 'SENT':
        return 'email-status--sent';
      case 'FAILED':
        return 'email-status--failed';
      case 'SKIPPED':
        return 'email-status--skipped';
      default:
        return 'email-status--neutral';
    }
  }

  isResendingEmailLog(emailLogId: string): boolean {
    return this.resendingEmailLogIds.has(emailLogId);
  }

  private downloadBlob(blob: Blob, filename: string): void {
    if (!this.isBrowser) {
      return;
    }
    downloadBlobInBrowser(blob, filename);
  }
}
