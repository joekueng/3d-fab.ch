import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface QuoteRequestDto {
  requestType: string;
  customerType: string;
  email: string;
  phone?: string;
  name?: string;
  companyName?: string;
  contactPerson?: string;
  message: string;
  acceptTerms: boolean;
  acceptPrivacy: boolean;
}

@Injectable({
  providedIn: 'root',
})
export class QuoteRequestService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/api/custom-quote-requests`;

  createRequest(request: QuoteRequestDto, files: File[]): Observable<any> {
    const formData = new FormData();

    // Append Request DTO as JSON Blob
    const requestBlob = new Blob([JSON.stringify(request)], {
      type: 'application/json',
    });
    formData.append('request', requestBlob);

    // Append Files
    files.forEach((file) => {
      formData.append('files', file);
    });

    return this.http.post(this.apiUrl, formData);
  }
}
