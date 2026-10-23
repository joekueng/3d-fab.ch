import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type { ModelUnit } from './quote-estimator.service';

@Injectable({ providedIn: 'root' })
export class ModelPreviewService {
  private readonly http = inject(HttpClient);

  createStlPreview(file: File, modelUnit: ModelUnit = 'mm'): Observable<Blob> {
    const form = new FormData();
    form.append('file', file);
    form.append('modelUnit', modelUnit);
    return this.http.post(`${environment.apiUrl}/api/model-previews`, form, {
      responseType: 'blob',
    });
  }
}
