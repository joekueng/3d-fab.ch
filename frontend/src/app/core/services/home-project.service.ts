import { inject, Injectable, Injector } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { HttpClient, HttpParams } from '@angular/common/http';
import { catchError, distinctUntilChanged, Observable, of, switchMap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LanguageService } from './language.service';
import { PublicMediaUsageDto } from './public-media.service';

export interface HomeProject {
  id: string;
  slug: string;
  eyebrow: string | null;
  title: string | null;
  description: string | null;
  sortOrder: number;
  image: PublicMediaUsageDto | null;
  detailImage: PublicMediaUsageDto | null;
}

@Injectable({
  providedIn: 'root',
})
export class HomeProjectService {
  private readonly http = inject(HttpClient);
  private readonly injector = inject(Injector);
  private readonly languageService = inject(LanguageService);
  private readonly baseUrl = `${environment.apiUrl}/api/public/home/projects`;
  private readonly selectedLang$ = toObservable(
    this.languageService.currentLang,
    {
      injector: this.injector,
    },
  ).pipe(distinctUntilChanged());

  getProjects(): Observable<readonly HomeProject[]> {
    return this.selectedLang$.pipe(
      switchMap((lang) => {
        const params = new HttpParams().set('lang', lang);
        return this.http
          .get<HomeProject[]>(this.baseUrl, { params })
          .pipe(catchError(() => of([])));
      }),
    );
  }
}
