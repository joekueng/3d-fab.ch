import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

export interface AdminOrderItem {
  id: string;
  itemType: string;
  originalFilename: string;
  displayName?: string;
  materialCode: string;
  colorCode: string;
  filamentVariantId?: number;
  shopProductId?: string;
  shopProductVariantId?: string;
  shopProductSlug?: string;
  shopProductName?: string;
  shopVariantLabel?: string;
  shopVariantColorName?: string;
  shopVariantColorHex?: string;
  filamentVariantDisplayName?: string;
  filamentColorName?: string;
  filamentColorHex?: string;
  quality?: string;
  nozzleDiameterMm?: number;
  layerHeightMm?: number;
  infillPercent?: number;
  infillPattern?: string;
  supportsEnabled?: boolean;
  quantity: number;
  printTimeSeconds: number;
  materialGrams: number;
  unitPriceChf: number;
  lineTotalChf: number;
}

export interface AdminOrder {
  id: string;
  orderNumber: string;
  status: string;
  paymentStatus?: string;
  paymentMethod?: string;
  billingCustomerType?: string;
  customerEmail: string;
  totalChf: number;
  createdAt: string;
  paidAt?: string;
  isCadOrder?: boolean;
  sourceRequestId?: string;
  cadHours?: number;
  cadHourlyRateChf?: number;
  cadTotalChf?: number;
  printMaterialCode?: string;
  printNozzleDiameterMm?: number;
  printLayerHeightMm?: number;
  printInfillPattern?: string;
  printInfillPercent?: number;
  printSupportsEnabled?: boolean;
  items: AdminOrderItem[];
}

export interface AdminUpdateOrderStatusPayload {
  status: string;
}

@Injectable({
  providedIn: 'root',
})
export class AdminOrdersService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/api/admin/orders`;

  listOrders(): Observable<AdminOrder[]> {
    return this.http.get<AdminOrder[]>(this.baseUrl, { withCredentials: true });
  }

  getOrder(orderId: string): Observable<AdminOrder> {
    return this.http.get<AdminOrder>(`${this.baseUrl}/${orderId}`, {
      withCredentials: true,
    });
  }

  updatePaymentMethod(orderId: string, method: string): Observable<AdminOrder> {
    return this.http.post<AdminOrder>(
      `${this.baseUrl}/${orderId}/payments/confirm`,
      { method },
      { withCredentials: true },
    );
  }

  updateOrderStatus(
    orderId: string,
    payload: AdminUpdateOrderStatusPayload,
  ): Observable<AdminOrder> {
    return this.http.post<AdminOrder>(
      `${this.baseUrl}/${orderId}/status`,
      payload,
      { withCredentials: true },
    );
  }

  downloadOrderItemFile(
    orderId: string,
    orderItemId: string,
  ): Observable<Blob> {
    return this.http.get(
      `${this.baseUrl}/${orderId}/items/${orderItemId}/file`,
      {
        withCredentials: true,
        responseType: 'blob',
      },
    );
  }

  downloadOrderConfirmation(orderId: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${orderId}/documents/confirmation`, {
      withCredentials: true,
      responseType: 'blob',
    });
  }

  downloadOrderInvoice(orderId: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${orderId}/documents/invoice`, {
      withCredentials: true,
      responseType: 'blob',
    });
  }
}
