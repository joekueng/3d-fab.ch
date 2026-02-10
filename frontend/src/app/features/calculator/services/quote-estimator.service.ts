import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpEventType } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { map, catchError, tap } from 'rxjs/operators';
import { environment } from '../../../../environments/environment';

export interface QuoteRequest {
  items: { file: File, quantity: number, color?: string }[];
  material: string;
  quality: string;
  notes?: string;
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
  material?: string;
  color?: string;
}

export interface QuoteResult {
  items: QuoteItem[];
  setupCost: number;
  currency: string;
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

// Options Interfaces
export interface MaterialOption {
    code: string;
    label: string;
    variants: VariantOption[];
}
export interface VariantOption {
    name: string;
    colorName: string;
    hexColor: string;
    isOutOfStock: boolean;
}
export interface QualityOption {
    id: string;
    label: string;
}
export interface InfillOption {
    id: string;
    label: string;
}
export interface NumericOption {
    value: number;
    label: string;
}

export interface OptionsResponse {
    materials: MaterialOption[];
    qualities: QualityOption[];
    infillPatterns: InfillOption[];
    layerHeights: NumericOption[];
    nozzleDiameters: NumericOption[];
}

// UI Option for Select Component
export interface SimpleOption {
    value: string | number;
    label: string;
}

@Injectable({
  providedIn: 'root'
})
export class QuoteEstimatorService {
  private http = inject(HttpClient);
  
  getOptions(): Observable<OptionsResponse> {
      console.log('QuoteEstimatorService: Requesting options...');
      const headers: any = {};
      // @ts-ignore
      if (environment.basicAuth) headers['Authorization'] = 'Basic ' + btoa(environment.basicAuth);
      return this.http.get<OptionsResponse>(`${environment.apiUrl}/api/calculator/options`, { headers }).pipe(
          tap({
              next: (res) => console.log('QuoteEstimatorService: Options loaded', res),
              error: (err) => console.error('QuoteEstimatorService: Options failed', err)
          })
      );
  }
  
  calculate(request: QuoteRequest): Observable<number | QuoteResult> {
    console.log('QuoteEstimatorService: Calculating quote...', request);
    if (request.items.length === 0) {
        console.warn('QuoteEstimatorService: No items to calculate');
        return of();
    }
    
    return new Observable(observer => {
        const totalItems = request.items.length;
        const allProgress: number[] = new Array(totalItems).fill(0);
        const finalResponses: any[] = [];
        let completedRequests = 0;

        const uploads = request.items.map((item, index) => {
             const formData = new FormData();
             formData.append('file', item.file);
             // machine param removed - backend uses default active
             
             // Map material? Or trust frontend to send correct code?
             // Since we fetch options now, we should send the code directly.
             // But for backward compat/safety/mapping logic in mapMaterial, let's keep it or update it.
             // If frontend sends 'PLA', mapMaterial returns 'pla_basic'.
             // We should check if request.material is already a code from options.
             // For now, let's assume request.material IS the code if it matches our new options,
             // or fallback to mapper if it's old legacy string.
             // Let's keep mapMaterial but update it to be smarter if needed, or rely on UploadForm to send correct codes.
             // For now, let's use mapMaterial as safety, assuming frontend sends short codes 'PLA'.
             // Wait, if we use dynamic options, the 'value' in select will be the 'code' from backend (e.g. 'PLA').
             // Backend expects 'pla_basic' or just 'PLA'? 
             // QuoteController -> processRequest -> SlicerService.slice -> assumes 'filament' is a profile name like 'pla_basic'.
             // So we MUST map 'PLA' to 'pla_basic' UNLESS backend options return 'pla_basic' as code.
             // Backend OptionsController returns type.getMaterialCode() which is 'PLA'.
             // So we still need mapping to slicer profile names.
             
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
                    }

                    const event = wrapper.event;
                    if (event && event.type === HttpEventType.UploadProgress) {
                        if (event.total) {
                            const percent = Math.round((100 * event.loaded) / event.total);
                            allProgress[idx] = percent;
                            // Emit average progress
                            const avg = Math.round(allProgress.reduce((a, b) => a + b, 0) / totalItems);
                            observer.next(avg); 
                        }
                    } else if ((event && event.type === HttpEventType.Response) || wrapper.error) { 
                        // It's done (either response or error caught above)
                        if (!finalResponses[idx]) { // only if not already set by error
                             allProgress[idx] = 100;
                             if (wrapper.error) {
                                 finalResponses[idx] = { success: false, fileName: wrapper.item.file.name };
                             } else {
                                finalResponses[idx] = { ...event.body, fileName: wrapper.item.file.name, originalQty: wrapper.item.quantity };
                             }
                             completedRequests++;
                        }
                        
                        if (completedRequests === totalItems) {
                            // All done
                            observer.next(100); 
                            
                            // Calculate Results
                            let setupCost = 10;
                            
                            if (request.nozzleDiameter && request.nozzleDiameter !== 0.4) {
                                setupCost += 2;
                            }
                            
                            const items: QuoteItem[] = [];
                            
                            finalResponses.forEach((res, idx) => {
                                if (res && res.success) {
                                    const originalItem = request.items[idx];
                                    items.push({
                                        fileName: res.fileName,
                                        unitPrice: res.data.cost.total,
                                        unitTime: res.data.print_time_seconds,
                                        unitWeight: res.data.material_grams,
                                        quantity: res.originalQty, // Use the requested quantity
                                        material: request.material,
                                        color: originalItem.color || 'Default'
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
                    console.error('Error in request subscription', err);
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
