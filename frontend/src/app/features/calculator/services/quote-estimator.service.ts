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
  error?: string;
  status: 'pending' | 'done' | 'error';
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

  deleteLineItem(sessionId: string, lineItemId: string): Observable<any> {
      const headers: any = {};
      // @ts-ignore
      if (environment.basicAuth) headers['Authorization'] = 'Basic ' + btoa(environment.basicAuth);
      return this.http.delete(`${environment.apiUrl}/api/quote-sessions/${sessionId}/line-items/${lineItemId}`, { headers });
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
                
                // Initialize items in pending state
                const currentItems: QuoteItem[] = request.items.map(item => ({
                    fileName: item.file.name,
                    unitPrice: 0,
                    unitTime: 0,
                    unitWeight: 0,
                    quantity: item.quantity,
                    status: 'pending',
                    color: item.color || 'White' // Default color for UI
                }));

                // Emit initial state
                const initialResult: QuoteResult = {
                    sessionId: sessionId,
                    items: [...currentItems],
                    setupCost: sessionSetupCost,
                    currency: 'CHF',
                    totalPrice: 0, // Will be calculated dynamically
                    totalTimeHours: 0,
                    totalTimeMinutes: 0,
                    totalWeight: 0,
                    notes: request.notes
                };
                observer.next(initialResult);
                
                // 2. Upload files to this session
                const totalItems = request.items.length;
                const allProgress: number[] = new Array(totalItems).fill(0);
                let completedRequests = 0;

                const emitUpdate = () => {
                     const avg = Math.round(allProgress.reduce((a, b) => a + b, 0) / totalItems);
                     observer.next(avg);
                     
                     // Helper to calculate totals for current items
                     let grandTotal = 0;
                     let totalTime = 0;
                     let totalWeight = 0;
                     let validCount = 0;
                     
                     currentItems.forEach(item => {
                         if (item.status === 'done') {
                             grandTotal += item.unitPrice * item.quantity;
                             totalTime += item.unitTime * item.quantity;
                             totalWeight += item.unitWeight * item.quantity;
                             validCount++;
                         }
                     });

                     if (validCount > 0) {
                         grandTotal += sessionSetupCost;
                     }

                     const result: QuoteResult = {
                         sessionId: sessionId,
                         items: [...currentItems], // Create copy to trigger change detection
                         setupCost: sessionSetupCost,
                         currency: 'CHF',
                         totalPrice: Math.round(grandTotal * 100) / 100,
                         totalTimeHours: Math.floor(totalTime / 3600),
                         totalTimeMinutes: Math.ceil((totalTime % 3600) / 60),
                         totalWeight: Math.ceil(totalWeight),
                         notes: request.notes
                     };
                     observer.next(result);

                     if (completedRequests === totalItems) {
                         observer.complete();
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
                                allProgress[index] = Math.round((70 * event.loaded) / event.total); // Upload is 70% of "progress" for user perception
                                emitUpdate();
                            } else if (event.type === HttpEventType.Response) { 
                                 allProgress[index] = 100;
                                 const resBody = event.body as any;
                                 
                                 // Update item in list
                                 currentItems[index] = {
                                     id: resBody.id,
                                     fileName: resBody.originalFilename, // use returned filename
                                     unitPrice: resBody.unitPriceChf || 0,
                                     unitTime: resBody.printTimeSeconds || 0,
                                     unitWeight: resBody.materialGrams || 0,
                                     quantity: item.quantity, // Keep original quantity
                                     material: request.material, 
                                     color: item.color || 'White',
                                     status: 'done'
                                 };

                                 completedRequests++;
                                 emitUpdate();
                            }
                        },
                        error: (err) => {
                            console.error('Item upload failed', err);
                            const errorMsg = err.error?.code === 'VIRUS_DETECTED' ? 'VIRUS_DETECTED' : 'UPLOAD_FAILED';
                            
                            currentItems[index] = {
                                ...currentItems[index],
                                status: 'error',
                                error: errorMsg
                            };
                            
                            allProgress[index] = 100; // Mark as done despite error
                            completedRequests++;
                            emitUpdate();
                        }
                    });
                });
            },
            error: (err) => {
                console.error('Failed to create session', err);
                observer.error('Could not initialize quote session');
            }
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
              color: item.colorCode,
              status: 'done'
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
