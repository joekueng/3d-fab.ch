import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { AdminLocalizedLanguage } from '../../../shared/utils/admin-localization.util';

export interface AdminLocalizedTextFieldPayload {
  required?: boolean;
  values: Record<AdminLocalizedLanguage, string>;
}

export interface AdminTranslateLocalizedTextPayload {
  context: string;
  sourceLanguage: AdminLocalizedLanguage;
  overwriteExisting: boolean;
  fields: Record<string, AdminLocalizedTextFieldPayload>;
}

export interface AdminTranslateLocalizedTextResponse {
  sourceLanguage: AdminLocalizedLanguage;
  targetLanguages: AdminLocalizedLanguage[];
  fields: Record<string, Partial<Record<AdminLocalizedLanguage, string>>>;
}

@Injectable({
  providedIn: 'root',
})
export class AdminTranslationService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/api/admin/translations`;

  translateLocalizedText(
    payload: AdminTranslateLocalizedTextPayload,
  ): Observable<AdminTranslateLocalizedTextResponse> {
    return this.http.post<AdminTranslateLocalizedTextResponse>(
      `${this.baseUrl}/localized-text`,
      payload,
      {
        withCredentials: true,
      },
    );
  }
}
