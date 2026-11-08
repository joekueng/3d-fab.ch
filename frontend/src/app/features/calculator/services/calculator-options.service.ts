import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type { OptionsResponse } from './quote-estimator.service';

/** Read-only catalogue access; never creates or resumes a quote session. */
@Injectable({ providedIn: 'root' })
export class CalculatorOptionsService {
  private readonly http = inject(HttpClient);

  getOptions(): Observable<OptionsResponse> {
    return this.http.get<OptionsResponse>(
      `${environment.apiUrl}/api/calculator/options`,
    );
  }
}
