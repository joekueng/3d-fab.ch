import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import {
  AdminContactRequest,
  AdminContactRequestAttachment,
  AdminContactRequestDetail,
  AdminOperationsService
} from '../services/admin-operations.service';

@Component({
  selector: 'app-admin-contact-requests',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './admin-contact-requests.component.html',
  styleUrl: './admin-contact-requests.component.scss'
})
export class AdminContactRequestsComponent implements OnInit {
  private readonly adminOperationsService = inject(AdminOperationsService);

  requests: AdminContactRequest[] = [];
  selectedRequest: AdminContactRequestDetail | null = null;
  selectedRequestId: string | null = null;
  loading = false;
  detailLoading = false;
  errorMessage: string | null = null;

  ngOnInit(): void {
    this.loadRequests();
  }

  loadRequests(): void {
    this.loading = true;
    this.errorMessage = null;
    this.adminOperationsService.getContactRequests().subscribe({
      next: (requests) => {
        this.requests = requests;
        if (requests.length === 0) {
          this.selectedRequest = null;
          this.selectedRequestId = null;
        } else if (this.selectedRequestId && requests.some(r => r.id === this.selectedRequestId)) {
          this.openDetails(this.selectedRequestId);
        } else {
          this.openDetails(requests[0].id);
        }
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'Impossibile caricare le richieste di contatto.';
      }
    });
  }

  openDetails(requestId: string): void {
    this.selectedRequestId = requestId;
    this.detailLoading = true;
    this.adminOperationsService.getContactRequestDetail(requestId).subscribe({
      next: (detail) => {
        this.selectedRequest = detail;
        this.detailLoading = false;
      },
      error: () => {
        this.detailLoading = false;
        this.errorMessage = 'Impossibile caricare il dettaglio richiesta.';
      }
    });
  }

  isSelected(requestId: string): boolean {
    return this.selectedRequestId === requestId;
  }

  downloadAttachment(attachment: AdminContactRequestAttachment): void {
    if (!this.selectedRequest) {
      return;
    }

    this.adminOperationsService.downloadContactRequestAttachment(this.selectedRequest.id, attachment.id).subscribe({
      next: (blob) => this.downloadBlob(blob, attachment.originalFilename || `attachment-${attachment.id}`),
      error: () => {
        this.errorMessage = 'Download allegato non riuscito.';
      }
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
      return 'chip-warning';
    }
    if (['DONE', 'COMPLETED', 'RESOLVED', 'CLOSED'].includes(normalized)) {
      return 'chip-success';
    }
    if (['REJECTED', 'FAILED', 'ERROR', 'SPAM'].includes(normalized)) {
      return 'chip-danger';
    }
    return 'chip-light';
  }

  private downloadBlob(blob: Blob, filename: string): void {
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    window.URL.revokeObjectURL(url);
  }
}
