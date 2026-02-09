import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, forkJoin, of } from 'rxjs';
import { map, catchError } from 'rxjs/operators';
import { environment } from '../../../../environments/environment';

export interface QuoteRequest {
  items: { file: File, quantity: number, color?: string }[];
  material: string;
  quality: string;
  notes?: string;
  // color removed from global scope
  infillDensity?: number;
  infillPattern?: string;
  supportEnabled?: boolean;
  layerHeight?: number;
  nozzleDiameter?: number;
  mode: 'easy' | 'advanced';
}

export interface QuoteItem {
  fileName: string;
  unitPrice: number;
  unitTime: number; // seconds
  unitWeight: number; // grams
  quantity: number;
  // Computed values for UI convenience (optional, can be done in component)
}

export interface QuoteResult {
  items: QuoteItem[];
  setupCost: number;
  currency: string;
  // The following are aggregations that can be re-calculated
  totalPrice: number;
  totalTimeHours: number;
  totalTimeMinutes: number;
  totalWeight: number;
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
  
  calculate(request: QuoteRequest): Observable<number | QuoteResult> {
    if (request.items.length === 0) return of();
    
    return new Observable(observer => {
        const totalItems = request.items.length;
        const allProgress: number[] = new Array(totalItems).fill(0);
        const finalResponses: any[] = [];
        let completedRequests = 0;

        const uploads = request.items.map((item, index) => {
             const formData = new FormData();
             formData.append('file', item.file);
             formData.append('machine', 'bambu_a1'); 
             formData.append('filament', this.mapMaterial(request.material));
             formData.append('quality', this.mapQuality(request.quality));
             
             // Send color for both modes if present, defaulting to Black
             formData.append('material_color', item.color || 'Black');

             if (request.mode === 'advanced') {
                if (request.infillDensity) formData.append('infill_density', request.infillDensity.toString());
                if (request.infillPattern) formData.append('infill_pattern', request.infillPattern);
                if (request.supportEnabled) formData.append('support_enabled', 'true');
                if (request.layerHeight) formData.append('layer_height', request.layerHeight.toString());
                if (request.nozzleDiameter) formData.append('nozzle_diameter', request.nozzleDiameter.toString());
             }
             
             const headers: any = {};
             // @ts-ignore
             if (environment.basicAuth) headers['Authorization'] = 'Basic ' + btoa(environment.basicAuth);

             return this.http.post<BackendResponse>(`${environment.apiUrl}/api/quote`, formData, { 
                 headers,
                 reportProgress: true,
                 observe: 'events'
             }).pipe(
                 map(event => ({ item, event, index })),
                 catchError(err => of({ item, error: err, index }))
             );
        });

        // Subscribe to all
        uploads.forEach((obs) => {
            obs.subscribe({
                next: (wrapper: any) => {
                    const idx = wrapper.index;
                    
                    if (wrapper.error) {
                        finalResponses[idx] = { success: false, fileName: wrapper.item.file.name };
                        return;
                    }

                    const event = wrapper.event;
                    if (event.type === 1) { // HttpEventType.UploadProgress
                        if (event.total) {
                            const percent = Math.round((100 * event.loaded) / event.total);
                            allProgress[idx] = percent;
                            // Emit average progress
                            const avg = Math.round(allProgress.reduce((a, b) => a + b, 0) / totalItems);
                            observer.next(avg); 
                        }
                    } else if (event.type === 4) { // HttpEventType.Response
                        allProgress[idx] = 100;
                        finalResponses[idx] = { ...event.body, fileName: wrapper.item.file.name, originalQty: wrapper.item.quantity };
                        completedRequests++;
                        
                        if (completedRequests === totalItems) {
                            // All done
                            observer.next(100); 
                            
                            // Calculate Results
                            // Base setup cost
                            let setupCost = 10;
                            
                            // Surcharge for non-standard nozzle
                            if (request.nozzleDiameter && request.nozzleDiameter !== 0.4) {
                                setupCost += 2;
                            }
                            
                            const items: QuoteItem[] = [];
                            
                            finalResponses.forEach(res => {
                                if (res && res.success) {
                                    items.push({
                                        fileName: res.fileName,
                                        unitPrice: res.data.cost.total,
                                        unitTime: res.data.print_time_seconds,
                                        unitWeight: res.data.material_grams,
                                        quantity: res.originalQty // Use the requested quantity
                                    });
                                }
                            });

                            if (items.length === 0) {
                                observer.error('All calculations failed.');
                                return;
                            }
                            
                            // Initial Aggregation
                            let grandTotal = setupCost;
                            let totalTime = 0;
                            let totalWeight = 0;
                            
                            items.forEach(item => {
                                grandTotal += item.unitPrice * item.quantity;
                                totalTime += item.unitTime * item.quantity;
                                totalWeight += item.unitWeight * item.quantity;
                            });

                            const totalHours = Math.floor(totalTime / 3600);
                            const totalMinutes = Math.ceil((totalTime % 3600) / 60);

                            const result: QuoteResult = {
                                items,
                                setupCost,
                                currency: 'CHF',
                                totalPrice: Math.round(grandTotal * 100) / 100,
                                totalTimeHours: totalHours,
                                totalTimeMinutes: totalMinutes,
                                totalWeight: Math.ceil(totalWeight)
                            };
                            
                            observer.next(result);
                            observer.complete();
                        }
                    }
                },
                error: (err) => {
                    console.error('Error in request', err);
                    completedRequests++;
                    if (completedRequests === totalItems) {
                         observer.error('Requests failed');
                    }
                }
            });
        });
    });
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

  // Consultation Data Transfer
  private pendingConsultation = signal<{files: File[], message: string} | null>(null);

  setPendingConsultation(data: {files: File[], message: string}) {
      this.pendingConsultation.set(data);
  }

  getPendingConsultation() {
      const data = this.pendingConsultation();
      this.pendingConsultation.set(null); // Clear after reading
      return data;
  }
}
