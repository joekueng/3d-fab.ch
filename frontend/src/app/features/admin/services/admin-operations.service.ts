import { ServiceLine } from '../../../shared/models/service-line';
import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { AdminEmailLog } from './admin-email-log.model';

export interface AdminFilamentStockRow {
  filamentVariantId: number;
  materialCode: string;
  variantDisplayName: string;
  colorName: string;
  stockSpools: number;
  spoolNetKg: number;
  stockKg: number;
  stockFilamentGrams: number;
  active: boolean;
}

export interface AdminFilamentMaterialType {
  id: number;
  materialCode: string;
  isFlexible: boolean;
  isTechnical: boolean;
  technicalTypeLabel?: string;
}

export interface AdminFilamentVariant {
  id: number;
  materialTypeId: number;
  materialCode: string;
  materialIsFlexible: boolean;
  materialIsTechnical: boolean;
  materialTechnicalTypeLabel?: string;
  variantDisplayName: string;
  colorName: string;
  colorLabelIt: string;
  colorLabelEn: string;
  colorLabelDe: string;
  colorLabelFr: string;
  colorHex?: string;
  finishType?: string;
  brand?: string;
  isMatte: boolean;
  isSpecial: boolean;
  costChfPerKg: number;
  stockSpools: number;
  spoolNetKg: number;
  stockKg: number;
  stockFilamentGrams: number;
  isActive: boolean;
  createdAt: string;
}

export interface AdminUpsertFilamentMaterialTypePayload {
  materialCode: string;
  isFlexible: boolean;
  isTechnical: boolean;
  technicalTypeLabel?: string;
}

export interface AdminUpsertFilamentVariantPayload {
  materialTypeId: number;
  variantDisplayName: string;
  colorName: string;
  colorLabelIt?: string;
  colorLabelEn?: string;
  colorLabelDe?: string;
  colorLabelFr?: string;
  colorHex?: string;
  finishType?: string;
  brand?: string;
  isMatte: boolean;
  isSpecial: boolean;
  costChfPerKg: number;
  stockSpools: number;
  spoolNetKg: number;
  isActive: boolean;
}

export interface AdminContactRequest {
  id: string;
  requestType: string;
  customerType: string;
  email: string;
  phone?: string;
  name?: string;
  companyName?: string;
  status: string;
  createdAt: string;
}

export interface AdminContactRequestAttachment {
  id: string;
  originalFilename: string;
  mimeType?: string;
  fileSizeBytes?: number;
  createdAt: string;
}

export interface AdminContactRequestDetail {
  id: string;
  requestType: string;
  customerType: string;
  email: string;
  phone?: string;
  name?: string;
  companyName?: string;
  contactPerson?: string;
  message: string;
  status: string;
  createdAt: string;
  updatedAt: string;
  attachments: AdminContactRequestAttachment[];
  emailLogs: AdminEmailLog[];
}

export interface AdminUpdateContactRequestStatusPayload {
  status: string;
}

export interface AdminQuoteSession {
  id: string;
  status: string;
  materialCode: string;
  createdAt: string;
  expiresAt: string;
  convertedOrderId?: string;
  sourceRequestId?: string;
  cadHours?: number;
  cadHourlyRateChf?: number;
  cadTotalChf?: number;
}

export interface AdminSessionStatistics {
  totalSessionCount: number;
  sessionsWithItemsCount: number;
  emptySessionCount: number;
  convertedSessionCount: number;
  paidConvertedSessionCount: number;
  modifiedSessionCount: number;
  expiredAbandonedSessionCount: number;
  averageItemsPerActiveSession: number;
  paidConversionRatePercent: number;
}

export interface AdminQuoteSessionDetailItem {
  id: string;
  originalFilename: string;
  quantity: number;
  printTimeSeconds?: number;
  materialGrams?: number;
  materialCode?: string;
  quality?: string;
  nozzleDiameterMm?: number;
  layerHeightMm?: number;
  infillPercent?: number;
  infillPattern?: string;
  supportsEnabled?: boolean;
  requiresSplitPrinting?: boolean;
  colorCode?: string;
  filamentVariantId?: number;
  status: string;
  unitPriceChf: number;
}

export interface AdminQuoteSessionDetail {
  session: {
    id: string;
    status: string;
    materialCode: string;
    setupCostChf?: number;
    supportsEnabled?: boolean;
    notes?: string;
    sourceRequestId?: string;
    cadHours?: number;
    cadHourlyRateChf?: number;
  };
  items: AdminQuoteSessionDetailItem[];
  printItemsTotalChf: number;
  cadTotalChf: number;
  itemsTotalChf: number;
  shippingCostChf: number;
  globalMachineCostChf: number;
  grandTotalChf: number;
}

export interface AdminCreateCadInvoicePayload {
  serviceLines?: ServiceLine[];
  clientName?: string;
  invoiceName?: string;
  collaborationName?: string;
  sessionId?: string;
  sourceRequestId?: string;
  cadHours?: number;
  cadHourlyRateChf?: number;
  notes?: string;
}

export interface AdminCadInvoice {
  serviceLines?: ServiceLine[];
  clientName?: string;
  invoiceName?: string;
  collaborationName?: string;
  sessionId: string;
  sessionStatus: string;
  sourceRequestId?: string;
  cadHours: number;
  cadHourlyRateChf: number;
  cadTotalChf: number;
  printItemsTotalChf: number;
  setupCostChf: number;
  shippingCostChf: number;
  grandTotalChf: number;
  convertedOrderId?: string;
  convertedOrderStatus?: string;
  checkoutPath: string;
  notes?: string;
  createdAt: string;
}

export interface AdminQuoteItemStats {
  id: string;
  displayName?: string;
  quantity: number;
  printTimeSeconds?: number | null;
  materialGrams?: number | null;
  unitPriceChf?: number | null;
  newUnitPriceChf?: number | null;
  status: string;
  lineItemType: string;
  editable: boolean;
}

export interface AdminQuoteItemsResponse {
  sessionId: string;
  sessionStatus: string;
  items: AdminQuoteItemStats[];
  printItemsTotalChf: number;
  globalMachineCostChf: number;
  cadTotalChf: number;
  itemsTotalChf: number;
  setupCostChf: number;
  shippingCostChf: number;
  grandTotalChf: number;
}

export interface AdminQuoteItemStatsUpdate {
  itemId: string;
  printTimeSeconds: number;
  materialGrams: number;
}

export interface AdminQuoteItemStatsUpdatePayload {
  persist: boolean;
  items: AdminQuoteItemStatsUpdate[];
}

export interface QuoteSessionAttachment {
  id: string;
  originalFilename: string;
  mimeType?: string;
  fileSizeBytes?: number;
  image: boolean;
  createdAt: string;
}

@Injectable({
  providedIn: 'root',
})
export class AdminOperationsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/api/admin`;

  getFilamentStock(): Observable<AdminFilamentStockRow[]> {
    return this.http.get<AdminFilamentStockRow[]>(
      `${this.baseUrl}/filament-stock`,
      { withCredentials: true },
    );
  }

  getFilamentMaterials(): Observable<AdminFilamentMaterialType[]> {
    return this.http.get<AdminFilamentMaterialType[]>(
      `${this.baseUrl}/filaments/materials`,
      { withCredentials: true },
    );
  }

  getFilamentVariants(): Observable<AdminFilamentVariant[]> {
    return this.http.get<AdminFilamentVariant[]>(
      `${this.baseUrl}/filaments/variants`,
      { withCredentials: true },
    );
  }

  createFilamentMaterial(
    payload: AdminUpsertFilamentMaterialTypePayload,
  ): Observable<AdminFilamentMaterialType> {
    return this.http.post<AdminFilamentMaterialType>(
      `${this.baseUrl}/filaments/materials`,
      payload,
      { withCredentials: true },
    );
  }

  updateFilamentMaterial(
    materialId: number,
    payload: AdminUpsertFilamentMaterialTypePayload,
  ): Observable<AdminFilamentMaterialType> {
    return this.http.put<AdminFilamentMaterialType>(
      `${this.baseUrl}/filaments/materials/${materialId}`,
      payload,
      { withCredentials: true },
    );
  }

  createFilamentVariant(
    payload: AdminUpsertFilamentVariantPayload,
  ): Observable<AdminFilamentVariant> {
    return this.http.post<AdminFilamentVariant>(
      `${this.baseUrl}/filaments/variants`,
      payload,
      { withCredentials: true },
    );
  }

  updateFilamentVariant(
    variantId: number,
    payload: AdminUpsertFilamentVariantPayload,
  ): Observable<AdminFilamentVariant> {
    return this.http.put<AdminFilamentVariant>(
      `${this.baseUrl}/filaments/variants/${variantId}`,
      payload,
      { withCredentials: true },
    );
  }

  deleteFilamentVariant(variantId: number): Observable<void> {
    return this.http.delete<void>(
      `${this.baseUrl}/filaments/variants/${variantId}`,
      { withCredentials: true },
    );
  }

  getContactRequests(): Observable<AdminContactRequest[]> {
    return this.http.get<AdminContactRequest[]>(
      `${this.baseUrl}/contact-requests`,
      { withCredentials: true },
    );
  }

  getContactRequestDetail(
    requestId: string,
  ): Observable<AdminContactRequestDetail> {
    return this.http.get<AdminContactRequestDetail>(
      `${this.baseUrl}/contact-requests/${requestId}`,
      { withCredentials: true },
    );
  }

  updateContactRequestStatus(
    requestId: string,
    payload: AdminUpdateContactRequestStatusPayload,
  ): Observable<AdminContactRequestDetail> {
    return this.http.patch<AdminContactRequestDetail>(
      `${this.baseUrl}/contact-requests/${requestId}/status`,
      payload,
      { withCredentials: true },
    );
  }

  resendContactRequestEmail(
    requestId: string,
    emailLogId: string,
  ): Observable<AdminContactRequestDetail> {
    return this.http.post<AdminContactRequestDetail>(
      `${this.baseUrl}/contact-requests/${requestId}/email-logs/${emailLogId}/resend`,
      {},
      { withCredentials: true },
    );
  }

  downloadContactRequestAttachment(
    requestId: string,
    attachmentId: string,
  ): Observable<Blob> {
    return this.http.get(
      `${this.baseUrl}/contact-requests/${requestId}/attachments/${attachmentId}/file`,
      {
        withCredentials: true,
        responseType: 'blob',
      },
    );
  }

  getSessions(): Observable<AdminQuoteSession[]> {
    return this.http.get<AdminQuoteSession[]>(`${this.baseUrl}/sessions`, {
      withCredentials: true,
    });
  }

  getSessionStatistics(): Observable<AdminSessionStatistics> {
    return this.http.get<AdminSessionStatistics>(
      `${this.baseUrl}/sessions/statistics`,
      { withCredentials: true },
    );
  }

  deleteSession(sessionId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/sessions/${sessionId}`, {
      withCredentials: true,
    });
  }

  getSessionDetail(sessionId: string): Observable<AdminQuoteSessionDetail> {
    return this.http.get<AdminQuoteSessionDetail>(
      `${environment.apiUrl}/api/quote-sessions/${sessionId}`,
      { withCredentials: true },
    );
  }

  listCadInvoices(): Observable<AdminCadInvoice[]> {
    return this.http.get<AdminCadInvoice[]>(`${this.baseUrl}/cad-invoices`, {
      withCredentials: true,
    });
  }

  updateCadInvoiceMetadata(
    sessionId: string,
    payload: {
      invoiceName: string;
      collaborationName: string;
      clientName: string;
    },
  ): Observable<void> {
    return this.http.patch<void>(
      `${this.baseUrl}/cad-invoices/${sessionId}/metadata`,
      payload,
      { withCredentials: true },
    );
  }

  createCadInvoice(
    payload: AdminCreateCadInvoicePayload,
  ): Observable<AdminCadInvoice> {
    return this.http.post<AdminCadInvoice>(
      `${this.baseUrl}/cad-invoices`,
      payload,
      { withCredentials: true },
    );
  }

  getAdminQuoteItems(sessionId: string): Observable<AdminQuoteItemsResponse> {
    return this.http.get<AdminQuoteItemsResponse>(
      `${this.baseUrl}/quote-sessions/${sessionId}/items`,
      { withCredentials: true },
    );
  }

  updateAdminQuoteItemStats(
    sessionId: string,
    payload: AdminQuoteItemStatsUpdatePayload,
  ): Observable<AdminQuoteItemsResponse> {
    return this.http.post<AdminQuoteItemsResponse>(
      `${this.baseUrl}/quote-sessions/${sessionId}/items/print-stats`,
      payload,
      { withCredentials: true },
    );
  }

  listQuoteSessionAttachments(
    sessionId: string,
  ): Observable<QuoteSessionAttachment[]> {
    return this.http.get<QuoteSessionAttachment[]>(
      `${this.baseUrl}/quote-sessions/${sessionId}/attachments`,
      { withCredentials: true },
    );
  }

  uploadQuoteSessionAttachments(
    sessionId: string,
    files: File[],
  ): Observable<QuoteSessionAttachment[]> {
    const formData = new FormData();
    for (const file of files) {
      formData.append('files', file);
    }
    return this.http.post<QuoteSessionAttachment[]>(
      `${this.baseUrl}/quote-sessions/${sessionId}/attachments`,
      formData,
      { withCredentials: true },
    );
  }

  deleteQuoteSessionAttachment(
    sessionId: string,
    attachmentId: string,
  ): Observable<void> {
    return this.http.delete<void>(
      `${this.baseUrl}/quote-sessions/${sessionId}/attachments/${attachmentId}`,
      { withCredentials: true },
    );
  }

  getQuoteSessionAttachmentPreview(
    sessionId: string,
    attachmentId: string,
  ): Observable<Blob> {
    return this.http.get(
      `${environment.apiUrl}/api/quote-sessions/${sessionId}/attachments/${attachmentId}/preview`,
      { withCredentials: true, responseType: 'blob' },
    );
  }
}
