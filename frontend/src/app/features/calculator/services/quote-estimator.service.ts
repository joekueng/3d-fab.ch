import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, forkJoin, of } from 'rxjs';
import { map, catchError } from 'rxjs/operators';
import { environment } from '../../../../environments/environment';

export interface QuoteRequest {
  files: File[];
  material: string;
  quality: string;
  quantity: number;
  notes?: string;
  color?: string;
  infillDensity?: number;
  infillPattern?: string;
  supportEnabled?: boolean;
  mode: 'easy' | 'advanced';
}

export interface QuoteResult {
  price: number;
  currency: string;
  printTimeHours: number;
  materialUsageGrams: number;
  setupCost: number;
}

interface BackendResponse {
  success: boolean;
  data: {
    print_time_seconds: number;
    material_grams: number;
    cost: {
      total: number;
    };
  };
  error?: string;
}

@Injectable({
  providedIn: 'root'
})
export class QuoteEstimatorService {
  private http = inject(HttpClient);
  
  calculate(request: QuoteRequest): Observable<QuoteResult> {
    const requests: Observable<BackendResponse>[] = request.files.map(file => {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('machine', 'bambu_a1'); // Hardcoded for now
      formData.append('filament', this.mapMaterial(request.material));
      formData.append('quality', this.mapQuality(request.quality));
      
      if (request.mode === 'advanced') {
          if (request.color) formData.append('material_color', request.color);
          if (request.infillDensity) formData.append('infill_density', request.infillDensity.toString());
          if (request.infillPattern) formData.append('infill_pattern', request.infillPattern);
          if (request.supportEnabled) formData.append('support_enabled', 'true');
      }

      const headers: any = {};
      // @ts-ignore
      if (environment.basicAuth) {
          // @ts-ignore
          headers['Authorization'] = 'Basic ' + btoa(environment.basicAuth);
      }
      
      console.log(`Sending file: ${file.name} to ${environment.apiUrl}/api/quote`);
      return this.http.post<BackendResponse>(`${environment.apiUrl}/api/quote`, formData, { headers }).pipe(
        map(res => {
             console.log('Response for', file.name, res);
             return res;
        }),
        catchError(err => {
            console.error('Error calculating quote for', file.name, err);
            return of({ success: false, data: { print_time_seconds: 0, material_grams: 0, cost: { total: 0 } }, error: err.message });
        })
      );
    });

    return forkJoin(requests).pipe(
      map(responses => {
        console.log('All responses:', responses);
        
        const validResponses = responses.filter(r => r.success);
        if (validResponses.length === 0 && responses.length > 0) {
            throw new Error('All calculations failed. Check backend connection.');
        }

        let totalPrice = 0;
        let totalTime = 0;
        let totalWeight = 0;
        let setupCost = 10; // Base setup

        validResponses.forEach(res => {
            totalPrice += res.data.cost.total;
            totalTime += res.data.print_time_seconds;
            totalWeight += res.data.material_grams;
        });

        // Apply quantity multiplier
        totalPrice = (totalPrice * request.quantity) + setupCost;
        totalWeight = totalWeight * request.quantity;
        // Total time usually parallel if we have multiple printers, but let's sum for now
        totalTime = totalTime * request.quantity;

        return {
          price: Math.round(totalPrice * 100) / 100,
          currency: 'CHF',
          printTimeHours: Math.ceil(totalTime / 3600), // Ceil hours
          materialUsageGrams: Math.ceil(totalWeight),
          setupCost
        };
      })
    );
  }

  private mapMaterial(mat: string): string {
    const m = mat.toUpperCase();
    if (m.includes('PLA')) return 'pla_basic';
    if (m.includes('PETG')) return 'petg_basic';
    if (m.includes('TPU')) return 'tpu_95a';
    return 'pla_basic';
  }

  private mapQuality(qual: string): string {
    const q = qual.toLowerCase();
    if (q.includes('draft')) return 'draft';
    if (q.includes('high')) return 'extra_fine';
    return 'standard';
  }
}
