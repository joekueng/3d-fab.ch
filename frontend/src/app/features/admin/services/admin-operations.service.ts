import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

export interface AdminFilamentStockRow {
  filamentVariantId: number;
  materialCode: string;
  variantDisplayName: string;
  colorName: string;
  stockSpools: number;
  spoolNetKg: number;
  stockKg: number;
  active: boolean;
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

export interface AdminQuoteSession {
  id: string;
  status: string;
  materialCode: string;
  createdAt: string;
  expiresAt: string;
  convertedOrderId?: string;
}

@Injectable({
  providedIn: 'root'
})
export class AdminOperationsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/api/admin`;

  getFilamentStock(): Observable<AdminFilamentStockRow[]> {
    return this.http.get<AdminFilamentStockRow[]>(`${this.baseUrl}/filament-stock`, { withCredentials: true });
  }

  getContactRequests(): Observable<AdminContactRequest[]> {
    return this.http.get<AdminContactRequest[]>(`${this.baseUrl}/contact-requests`, { withCredentials: true });
  }

  getSessions(): Observable<AdminQuoteSession[]> {
    return this.http.get<AdminQuoteSession[]>(`${this.baseUrl}/sessions`, { withCredentials: true });
  }
}
