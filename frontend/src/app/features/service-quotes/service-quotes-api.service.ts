import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import {
  AdminServiceQuote, QuoteAcceptRequest, QuoteConfirmation, QuoteDraftRequest,
  QuoteResume, ServiceQuoteOffer,
} from './service-quotes.models';

@Injectable({ providedIn: 'root' })
export class ServiceQuotesApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api`;
  private readonly admin = `${this.base}/admin/service-quotes`;
  list() { return this.http.get<AdminServiceQuote[]>(this.admin, { withCredentials: true }); }
  get(id: string) { return this.http.get<AdminServiceQuote>(`${this.admin}/${id}`, { withCredentials: true }); }
  save(id: string | null, payload: QuoteDraftRequest) {
    return id ? this.http.put<AdminServiceQuote>(`${this.admin}/${id}/draft`, payload, { withCredentials: true })
      : this.http.post<AdminServiceQuote>(this.admin, payload, { withCredentials: true });
  }
  publish(id: string, version: number) { return this.http.post<AdminServiceQuote>(`${this.admin}/${id}/publish`, { version }, { withCredentials: true }); }
  revoke(id: string, revision: string) { return this.http.post<AdminServiceQuote>(`${this.admin}/${id}/revisions/${revision}/revoke`, {}, { withCredentials: true }); }
  email(id: string, revision: string, email: string) { return this.http.post<{ status: string }>(`${this.admin}/${id}/revisions/${revision}/email`, { email }, { withCredentials: true }); }
  preview(id: string, revision?: string) {
    const path = revision ? `revisions/${revision}/pdf-preview` : 'draft/pdf-preview';
    return this.http.get(`${this.admin}/${id}/${path}`, { withCredentials: true, responseType: 'blob' });
  }
  adminPdf(id: string, acceptance: string) { return this.http.get(`${this.admin}/${id}/acceptances/${acceptance}/pdf`, { withCredentials: true, responseType: 'blob' }); }
  retryPdf(id: string, acceptance: string) { return this.http.post<AdminServiceQuote>(`${this.admin}/${id}/acceptances/${acceptance}/pdf-retry`, {}, { withCredentials: true }); }
  resendReceipt(id: string, acceptance: string) { return this.http.post<AdminServiceQuote>(`${this.admin}/${id}/acceptances/${acceptance}/receipt-email`, {}, { withCredentials: true }); }
  resume(link: string) { return this.http.post<QuoteResume>(`${this.base}/service-quote-links/${link}/resume`, {}); }
  offer(id: string, revision: string, token: string) { return this.http.get<ServiceQuoteOffer>(`${this.base}/service-quotes/${id}/revisions/${revision}`, { headers: { 'X-Service-Quote-Token': token } }); }
  accept(offer: ServiceQuoteOffer, token: string, payload: QuoteAcceptRequest) {
    return this.http.post<QuoteConfirmation>(`${this.base}/service-quotes/${offer.quoteId}/revisions/${offer.revisionId}/accept`, payload, { headers: { 'X-Service-Quote-Token': token } });
  }
  pdfStatus(acceptance: string, token: string) { return this.http.get<QuoteConfirmation>(`${this.base}/service-quote-acceptances/${acceptance}/pdf-status`, { headers: { 'X-Quote-Download-Token': token } }); }
  publicPdf(acceptance: string, token: string) { return this.http.get(`${this.base}/service-quote-acceptances/${acceptance}/pdf`, { headers: { 'X-Quote-Download-Token': token }, responseType: 'blob' }); }
}
