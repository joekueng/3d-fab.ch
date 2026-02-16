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
  id?: string;
  fileName: string;
  unitPrice: number;
  unitTime: number; // seconds
  unitWeight: number; // grams
  quantity: number;
  material?: string;
  color?: string;
}

export interface QuoteResult {
  sessionId?: string;
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

  // NEW METHODS for Order Flow
  
  getQuoteSession(sessionId: string): Observable<any> {
      const headers: any = {};
      // @ts-ignore
      if (environment.basicAuth) headers['Authorization'] = 'Basic ' + btoa(environment.basicAuth);
      return this.http.get(`${environment.apiUrl}/api/quote-sessions/${sessionId}`, { headers });
  }

  updateLineItem(lineItemId: string, changes: any): Observable<any> {
      const headers: any = {};
      // @ts-ignore
      if (environment.basicAuth) headers['Authorization'] = 'Basic ' + btoa(environment.basicAuth);
      return this.http.patch(`${environment.apiUrl}/api/quote-sessions/line-items/${lineItemId}`, changes, { headers });
  }

  createOrder(sessionId: string, orderDetails: any): Observable<any> {
      const headers: any = {};
      // @ts-ignore
      if (environment.basicAuth) headers['Authorization'] = 'Basic ' + btoa(environment.basicAuth);
      return this.http.post(`${environment.apiUrl}/api/orders/from-quote/${sessionId}`, orderDetails, { headers });
  }

  getOrder(orderId: string): Observable<any> {
      const headers: any = {};
      // @ts-ignore
      if (environment.basicAuth) headers['Authorization'] = 'Basic ' + btoa(environment.basicAuth);
      return this.http.get(`${environment.apiUrl}/api/orders/${orderId}`, { headers });
  }

  getOrderInvoice(orderId: string): Observable<Blob> {
      const headers: any = {};
      // @ts-ignore
      if (environment.basicAuth) headers['Authorization'] = 'Basic ' + btoa(environment.basicAuth);
      return this.http.get(`${environment.apiUrl}/api/orders/${orderId}/invoice`, {
          headers,
          responseType: 'blob'
      });
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
                
                // 2. Process items SEQUENTIALLY to avoid timeouts/overload
                const totalItems = request.items.length;
                const allProgress: number[] = new Array(totalItems).fill(0);
                const finalResponses: any[] = []; 
                let completedCount = 0;

                const processNextItem = (index: number) => {
                    if (index >= totalItems) {
                        // All done
                        finalize(finalResponses, sessionSetupCost, sessionId);
                        return;
                    }

                    const item = request.items[index];
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
                                reportProgress();
                            } else if (event.type === HttpEventType.Response) { 
                                allProgress[index] = 100;
                                finalResponses[index] = { ...event.body, success: true, fileName: item.file.name, originalQty: item.quantity, originalItem: item };
                                completedCount++;
                                reportProgress();
                                // Next
                                processNextItem(index + 1);
                            }
                        },
                        error: (err) => {
                            console.error('Item upload failed', err);
                            const errorMsg = err.error?.code === 'VIRUS_DETECTED' ? 'VIRUS_DETECTED' : 'UPLOAD_FAILED';
                            finalResponses[index] = { success: false, fileName: item.file.name, error: errorMsg };
                            completedCount++;
                            reportProgress();
                            // Next even if error
                            processNextItem(index + 1);
                        }
                    });
                };

                const reportProgress = () => {
                     const avg = Math.round(allProgress.reduce((a, b) => a + b, 0) / totalItems);
                     observer.next(avg);
                };

                // Start first item
                processNextItem(0);
            },
            error: (err) => {
                console.error('Failed to create session', err);
                observer.error('Could not initialize quote session');
            }
        });

        const finalize = (responses: any[], setupCost: number, sessionId: string) => {
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
                     id: res.id,
                     fileName: res.fileName,
                     unitPrice: unitPrice,
                     unitTime: res.printTimeSeconds || 0,
                     unitWeight: res.materialGrams || 0,
                     quantity: quantity,
                     material: this.mapMaterial(request.material), // Uses session material
                     color: res.originalItem.color || 'Default'
                 });
                 
                 grandTotal += unitPrice * quantity;
                 totalTime += (res.printTimeSeconds || 0) * quantity;
                 totalWeight += (res.materialGrams || 0) * quantity;
             });

             if (validCount === 0) {
                 const virusError = responses.find(r => r.error === 'VIRUS_DETECTED');
                 if (virusError) {
                     observer.error('VIRUS_DETECTED');
                 } else {
                     observer.error('All calculations failed.');
                 }
                 return;
             }
             
             grandTotal += setupCost;

             const result: QuoteResult = {
                 sessionId: sessionId,
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

  // Session File Retrieval
  getLineItemContent(sessionId: string, lineItemId: string): Observable<Blob> {
      const headers: any = {};
      // @ts-ignore
      if (environment.basicAuth) headers['Authorization'] = 'Basic ' + btoa(environment.basicAuth);
      return this.http.get(`${environment.apiUrl}/api/quote-sessions/${sessionId}/line-items/${lineItemId}/content`, {
          headers,
          responseType: 'blob'
      });
  }

  mapSessionToQuoteResult(sessionData: any): QuoteResult {
      const session = sessionData.session;
      const items = sessionData.items || [];
      const totalTime = items.reduce((acc: number, item: any) => acc + (item.printTimeSeconds || 0) * item.quantity, 0);
      const totalWeight = items.reduce((acc: number, item: any) => acc + (item.materialGrams || 0) * item.quantity, 0);

      return {
          sessionId: session.id,
          items: items.map((item: any) => ({
              id: item.id,
              fileName: item.originalFilename,
              unitPrice: item.unitPriceChf,
              unitTime: item.printTimeSeconds,
              unitWeight: item.materialGrams,
              quantity: item.quantity,
              material: session.materialCode, // Assumption: session has one material for all? or items have it? 
              // Backend model QuoteSession has materialCode. 
              // But line items might have different colors. 
              color: item.colorCode
          })),
          setupCost: session.setupCostChf,
          currency: 'CHF', // Fixed for now
          totalPrice: sessionData.grandTotalChf,
          totalTimeHours: Math.floor(totalTime / 3600),
          totalTimeMinutes: Math.ceil((totalTime % 3600) / 60),
          totalWeight: Math.ceil(totalWeight),
          notes: session.notes
      };
  }
}
