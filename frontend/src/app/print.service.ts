import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class PrintService {
  private http = inject(HttpClient);
  private apiUrl = 'http://127.0.0.1:8000'; // Should be in environment

  calculateQuote(file: File, params?: any): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);
    
    // Append extra params if meant for backend
    if (params) {
        // for key in params...
    }

    return this.http.post(`${this.apiUrl}/calculate/stl`, formData);
  }
}
