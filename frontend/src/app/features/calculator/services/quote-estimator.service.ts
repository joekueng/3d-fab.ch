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
  notes?: string;
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

interface BackendQuoteResult {
  totalPrice: number;
  currency: string;
  setupCost: number;
  stats: {
    printTimeSeconds: number;
    printTimeFormatted: string;
    filamentWeightGrams: number;
    filamentLengthMm: number;
  };
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
        // 1. Create Session first
        const headers: any = {};
        // @ts-ignore
        if (environment.basicAuth) headers['Authorization'] = 'Basic ' + btoa(environment.basicAuth);

        this.http.post<any>(`${environment.apiUrl}/api/quote-sessions`, {}, { headers }).subscribe({
            next: (sessionRes) => {
                const sessionId = sessionRes.id;
                const sessionSetupCost = sessionRes.setupCostChf || 0;
                
                // 2. Upload files to this session
                const totalItems = request.items.length;
                const allProgress: number[] = new Array(totalItems).fill(0);
                const finalResponses: any[] = []; 
                let completedRequests = 0;

                const checkCompletion = () => {
                     const avg = Math.round(allProgress.reduce((a, b) => a + b, 0) / totalItems);
                     observer.next(avg);
                     
                     if (completedRequests === totalItems) {
                         finalize(finalResponses, sessionSetupCost);
                     }
                };

                request.items.forEach((item, index) => {
                     const formData = new FormData();
                     formData.append('file', item.file);
                     
                     const settings = {
                         complexityMode: request.mode.toUpperCase(),
                         material: this.mapMaterial(request.material),
                         quality: request.quality,
                         supportsEnabled: request.supportEnabled,
                         color: item.color || '#FFFFFF',
                         layerHeight: request.mode === 'advanced' ? request.layerHeight : null,
                         infillDensity: request.mode === 'advanced' ? request.infillDensity : null,
                         infillPattern: request.mode === 'advanced' ? request.infillPattern : null,
                         nozzleDiameter: request.mode === 'advanced' ? request.nozzleDiameter : null
                     };
        
                     const settingsBlob = new Blob([JSON.stringify(settings)], { type: 'application/json' });
                     formData.append('settings', settingsBlob);

                     this.http.post<any>(`${environment.apiUrl}/api/quote-sessions/${sessionId}/line-items`, formData, { 
                         headers,
                         reportProgress: true,
                         observe: 'events'
                     }).subscribe({
                        next: (event) => {
                            if (event.type === HttpEventType.UploadProgress && event.total) {
                                allProgress[index] = Math.round((100 * event.loaded) / event.total);
                                checkCompletion();
                            } else if (event.type === HttpEventType.Response) { 
                                 allProgress[index] = 100;
                                 finalResponses[index] = { ...event.body, success: true, fileName: item.file.name, originalQty: item.quantity, originalItem: item };
                                 completedRequests++;
                                 checkCompletion();
                            }
                        },
                        error: (err) => {
                            console.error('Item upload failed', err);
                            finalResponses[index] = { success: false, fileName: item.file.name };
                            completedRequests++;
                            checkCompletion();
                        }
                    });
                });
            },
            error: (err) => {
                console.error('Failed to create session', err);
                observer.error('Could not initialize quote session');
            }
        });

        const finalize = (responses: any[], setupCost: number) => {
             observer.next(100); 
             const items: QuoteItem[] = [];
             let grandTotal = 0;
             let totalTime = 0;
             let totalWeight = 0;
             let validCount = 0;

             responses.forEach((res, idx) => {
                 if (!res || !res.success) return;
                 validCount++;
                 
                 const unitPrice = res.unitPriceChf || 0;
                 const quantity = res.originalQty || 1;
                 
                 items.push({
                     fileName: res.fileName,
                     unitPrice: unitPrice,
                     unitTime: res.printTimeSeconds || 0,
                     unitWeight: res.materialGrams || 0,
                     quantity: quantity,
                     material: request.material,
                     color: res.originalItem.color || 'Default'
                 });
                 
                 grandTotal += unitPrice * quantity;
                 totalTime += (res.printTimeSeconds || 0) * quantity;
                 totalWeight += (res.materialGrams || 0) * quantity;
             });

             if (validCount === 0) {
                 observer.error('All calculations failed.');
                 return;
             }
             
             grandTotal += setupCost;

             const result: QuoteResult = {
                 items,
                 setupCost: setupCost,
                 currency: 'CHF',
                 totalPrice: Math.round(grandTotal * 100) / 100,
                 totalTimeHours: Math.floor(totalTime / 3600),
                 totalTimeMinutes: Math.ceil((totalTime % 3600) / 60),
                 totalWeight: Math.ceil(totalWeight),
                 notes: request.notes
             };
             
             observer.next(result);
             observer.complete();
        };
    });
  }

  private mapMaterial(mat: string): string {
    const m = mat.toUpperCase();
    if (m.includes('PLA')) return 'pla_basic';
    if (m.includes('PETG')) return 'petg_basic';
    if (m.includes('TPU')) return 'tpu_95a';
    return 'pla_basic';
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
