import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

export type AdminMediaLanguage = 'it' | 'en' | 'de' | 'fr';

export interface AdminMediaTranslation {
  title: string;
  altText: string;
}

export interface AdminMediaVariant {
  id: string;
  variantName: string;
  format: string;
  storageKey: string;
  mimeType: string;
  widthPx: number;
  heightPx: number;
  fileSizeBytes: number;
  isGenerated: boolean;
  publicUrl: string | null;
  createdAt: string;
}

export interface AdminMediaUsage {
  id: string;
  usageType: string;
  usageKey: string;
  ownerId: string | null;
  mediaAssetId: string;
  sortOrder: number;
  isPrimary: boolean;
  isActive: boolean;
  translations: Record<AdminMediaLanguage, AdminMediaTranslation>;
  createdAt: string;
}

export interface AdminMediaAsset {
  id: string;
  originalFilename: string;
  storageKey: string;
  mimeType: string;
  fileSizeBytes: number;
  sha256Hex: string;
  widthPx: number | null;
  heightPx: number | null;
  status: string;
  visibility: string;
  title: string | null;
  altText: string | null;
  createdAt: string;
  updatedAt: string;
  variants: AdminMediaVariant[];
  usages: AdminMediaUsage[];
}

export interface AdminMediaUploadPayload {
  title?: string;
  altText?: string;
  visibility?: 'PUBLIC' | 'PRIVATE';
}

export interface AdminCreateMediaUsagePayload {
  usageType: string;
  usageKey: string;
  ownerId?: string | null;
  mediaAssetId: string;
  sortOrder?: number;
  isPrimary?: boolean;
  isActive?: boolean;
  translations: Record<AdminMediaLanguage, AdminMediaTranslation>;
}

export interface AdminUpdateMediaUsagePayload {
  usageType?: string;
  usageKey?: string;
  ownerId?: string | null;
  mediaAssetId?: string;
  sortOrder?: number;
  isPrimary?: boolean;
  isActive?: boolean;
  translations?: Record<AdminMediaLanguage, AdminMediaTranslation>;
}

@Injectable({
  providedIn: 'root',
})
export class AdminMediaService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/api/admin/media`;

  listAssets(): Observable<AdminMediaAsset[]> {
    return this.http.get<AdminMediaAsset[]>(`${this.baseUrl}/assets`, {
      withCredentials: true,
    });
  }

  uploadAsset(
    file: File,
    payload: AdminMediaUploadPayload,
  ): Observable<AdminMediaAsset> {
    const formData = new FormData();
    formData.append('file', file);
    if (payload.title?.trim()) {
      formData.append('title', payload.title.trim());
    }
    if (payload.altText?.trim()) {
      formData.append('altText', payload.altText.trim());
    }
    if (payload.visibility?.trim()) {
      formData.append('visibility', payload.visibility.trim());
    }

    return this.http.post<AdminMediaAsset>(`${this.baseUrl}/assets`, formData, {
      withCredentials: true,
    });
  }

  createUsage(
    payload: AdminCreateMediaUsagePayload,
  ): Observable<AdminMediaUsage> {
    return this.http.post<AdminMediaUsage>(`${this.baseUrl}/usages`, payload, {
      withCredentials: true,
    });
  }

  updateUsage(
    usageId: string,
    payload: AdminUpdateMediaUsagePayload,
  ): Observable<AdminMediaUsage> {
    return this.http.patch<AdminMediaUsage>(
      `${this.baseUrl}/usages/${usageId}`,
      payload,
      { withCredentials: true },
    );
  }

  deleteUsage(usageId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/usages/${usageId}`, {
      withCredentials: true,
    });
  }
}
