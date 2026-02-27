import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

export interface AdminOrderItem {
  id: string;
  originalFilename: string;
  materialCode: string;
  colorCode: string;
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
  customerEmail: string;
  totalChf: number;
  createdAt: string;
  items: AdminOrderItem[];
}

@Injectable({
  providedIn: 'root'
})
export class AdminOrdersService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/api/admin/orders`;

  listOrders(): Observable<AdminOrder[]> {
    return this.http.get<AdminOrder[]>(this.baseUrl, { withCredentials: true });
  }

  getOrder(orderId: string): Observable<AdminOrder> {
    return this.http.get<AdminOrder>(`${this.baseUrl}/${orderId}`, { withCredentials: true });
  }

  confirmPayment(orderId: string): Observable<AdminOrder> {
    return this.http.post<AdminOrder>(`${this.baseUrl}/${orderId}/payments/confirm`, {}, { withCredentials: true });
  }
}
