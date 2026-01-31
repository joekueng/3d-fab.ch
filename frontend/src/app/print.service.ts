import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class PrintService {
  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;

  calculateQuote(file: File, params?: any): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);
    
    // Append extra params if meant for backend
    if (params) {
        Object.keys(params).forEach(key => {
            if (params[key] !== null && params[key] !== undefined) {
                formData.append(key, params[key]);
            }
        });
    }

    return this.http.post(`${this.apiUrl}/api/quote`, formData);
  }

  getProfiles(): Observable<any> {
    return this.http.get(`${this.apiUrl}/api/profiles/available`);
  }
}

