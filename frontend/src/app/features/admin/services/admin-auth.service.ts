import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

export interface AdminAuthResponse {
  authenticated: boolean;
  retryAfterSeconds?: number;
  expiresInMinutes?: number;
}

@Injectable({
  providedIn: 'root',
})
export class AdminAuthService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/api/admin/auth`;

  login(password: string): Observable<AdminAuthResponse> {
    return this.http.post<AdminAuthResponse>(
      `${this.baseUrl}/login`,
      { password },
      { withCredentials: true },
    );
  }

  logout(): Observable<void> {
    return this.http.post<void>(
      `${this.baseUrl}/logout`,
      {},
      { withCredentials: true },
    );
  }

  me(): Observable<boolean> {
    return this.http
      .get<AdminAuthResponse>(`${this.baseUrl}/me`, { withCredentials: true })
      .pipe(map((response) => Boolean(response?.authenticated)));
  }
}
