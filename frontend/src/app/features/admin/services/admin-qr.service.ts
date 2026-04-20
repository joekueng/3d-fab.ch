import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

export interface AdminQrLink {
  id: string;
  name: string;
  slug: string;
  targetPath: string;
  isActive: boolean;
  notes?: string | null;
  publicUrl: string;
  createdAt: string;
  updatedAt: string;
}

export interface AdminUpsertQrLinkPayload {
  name: string;
  slug: string;
  targetPath: string;
  isActive: boolean;
  notes?: string | null;
}

export interface AdminQrDailyStat {
  date: string;
  scans: number;
  uniqueVisitors: number;
}

export interface AdminQrLanguageStat {
  language: string;
  scans: number;
}

export interface AdminQrLocationStat {
  countryCode?: string | null;
  countryName?: string | null;
  cityName?: string | null;
  label: string;
  scans: number;
}

export interface AdminQrRecentScan {
  scannedAt: string;
  resolvedLang: string;
  finalPath: string;
  countryCode?: string | null;
  countryName?: string | null;
  cityName?: string | null;
}

export interface AdminQrLinkStats {
  qrLinkId: string;
  fromDate: string;
  toDate: string;
  rawScans: number;
  uniqueVisitors: number;
  excludedBotScans: number;
  lastScannedAt?: string | null;
  daily: AdminQrDailyStat[];
  languages: AdminQrLanguageStat[];
  locations: AdminQrLocationStat[];
  recentScans: AdminQrRecentScan[];
}

export interface AdminQrOverviewItem {
  qrLinkId: string;
  name: string;
  slug: string;
  targetPath: string;
  isActive: boolean;
  publicUrl: string;
  rawScans: number;
  uniqueVisitors: number;
  topLocationLabel?: string | null;
  topLocationScans: number;
  lastScannedAt?: string | null;
}

export interface AdminQrOverviewStats {
  fromDate: string;
  toDate: string;
  totalQrLinks: number;
  activeQrLinks: number;
  rawScans: number;
  uniqueVisitors: number;
  daily: AdminQrDailyStat[];
  locations: AdminQrLocationStat[];
  qrLinks: AdminQrOverviewItem[];
}

@Injectable({
  providedIn: 'root',
})
export class AdminQrService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/api/admin/qr-links`;

  listQrLinks(): Observable<AdminQrLink[]> {
    return this.http.get<AdminQrLink[]>(this.baseUrl, {
      withCredentials: true,
    });
  }

  createQrLink(payload: AdminUpsertQrLinkPayload): Observable<AdminQrLink> {
    return this.http.post<AdminQrLink>(this.baseUrl, payload, {
      withCredentials: true,
    });
  }

  updateQrLink(
    qrLinkId: string,
    payload: AdminUpsertQrLinkPayload,
  ): Observable<AdminQrLink> {
    return this.http.patch<AdminQrLink>(
      `${this.baseUrl}/${qrLinkId}`,
      payload,
      {
        withCredentials: true,
      },
    );
  }

  getQrLinkStats(
    qrLinkId: string,
    from?: string | null,
    to?: string | null,
  ): Observable<AdminQrLinkStats> {
    const query = new URLSearchParams();
    if (from) {
      query.set('from', from);
    }
    if (to) {
      query.set('to', to);
    }
    const suffix = query.toString() ? `?${query.toString()}` : '';
    return this.http.get<AdminQrLinkStats>(
      `${this.baseUrl}/${qrLinkId}/stats${suffix}`,
      {
        withCredentials: true,
      },
    );
  }

  getOverviewStats(
    from?: string | null,
    to?: string | null,
  ): Observable<AdminQrOverviewStats> {
    const query = new URLSearchParams();
    if (from) {
      query.set('from', from);
    }
    if (to) {
      query.set('to', to);
    }
    const suffix = query.toString() ? `?${query.toString()}` : '';
    return this.http.get<AdminQrOverviewStats>(
      `${this.baseUrl}/overview${suffix}`,
      {
        withCredentials: true,
      },
    );
  }

  downloadQrSvg(qrLinkId: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${qrLinkId}/svg`, {
      withCredentials: true,
      responseType: 'blob',
    });
  }
}
