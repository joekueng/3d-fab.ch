import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { AdminMediaUsage } from './admin-media.service';
import { PublicMediaUsageDto } from '../../../core/services/public-media.service';

export interface AdminHomeProject {
  id: string;
  slug: string;
  eyebrowIt: string | null;
  eyebrowEn: string | null;
  eyebrowDe: string | null;
  eyebrowFr: string | null;
  titleIt: string | null;
  titleEn: string | null;
  titleDe: string | null;
  titleFr: string | null;
  descriptionIt: string | null;
  descriptionEn: string | null;
  descriptionDe: string | null;
  descriptionFr: string | null;
  isActive: boolean;
  sortOrder: number;
  mediaUsageType: string;
  mediaUsageKey: string;
  mediaUsages: AdminMediaUsage[];
  images: PublicMediaUsageDto[];
  createdAt: string;
  updatedAt: string;
}

export interface AdminUpsertHomeProjectPayload {
  slug?: string | null;
  eyebrowIt?: string | null;
  eyebrowEn?: string | null;
  eyebrowDe?: string | null;
  eyebrowFr?: string | null;
  titleIt: string;
  titleEn?: string | null;
  titleDe?: string | null;
  titleFr?: string | null;
  descriptionIt?: string | null;
  descriptionEn?: string | null;
  descriptionDe?: string | null;
  descriptionFr?: string | null;
  isActive: boolean;
  sortOrder: number;
}

@Injectable({
  providedIn: 'root',
})
export class AdminHomeProjectService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/api/admin/home-projects`;

  getProjects(): Observable<AdminHomeProject[]> {
    return this.http.get<AdminHomeProject[]>(this.baseUrl, {
      withCredentials: true,
    });
  }

  createProject(
    payload: AdminUpsertHomeProjectPayload,
  ): Observable<AdminHomeProject> {
    return this.http.post<AdminHomeProject>(this.baseUrl, payload, {
      withCredentials: true,
    });
  }

  updateProject(
    projectId: string,
    payload: AdminUpsertHomeProjectPayload,
  ): Observable<AdminHomeProject> {
    return this.http.put<AdminHomeProject>(
      `${this.baseUrl}/${projectId}`,
      payload,
      { withCredentials: true },
    );
  }

  deleteProject(projectId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${projectId}`, {
      withCredentials: true,
    });
  }
}
