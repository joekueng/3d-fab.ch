import { Injectable, inject, signal } from '@angular/core';
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
  printTimeMinutes: number;
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
  
  calculate(request: QuoteRequest): Observable<number | QuoteResult> {
    const formData = new FormData();
    // Assuming single file primarily for now, or aggregating. 
    // The current UI seems to select one "active" file or handle multiple. 
    // The logic below was mapping multiple files to multiple requests. 
    // To support progress seamlessly for the "main" action, let's focus on the processing flow.
    // If multiple files, we might need a more complex progress tracking or just track the first/total.
    // Given the UI shows one big "Analyse" button, let's treat it as a batch or single.
    
    // NOTE: The previous logic did `request.files.map(...)`.
    // If we want a global progress, we can mistakenly complexity it. 
    // Let's assume we upload all files in one request if the API supported it, but the API seems to be 1 file per request from previous code?
    // "formData.append('file', file)" inside the map implies multiple requests.
    // To keep it simple and working with the progress bar which is global:
    // We will emit progress for the *current* file being processed or average them.
    // OR simpler: The user typically uploads one file for a quote?
    // The UI `files: File[]` allows multiple.
    // Let's stick to the previous logic but wrap it to emit progress.
    // However, forkJoin waits for all. We can't easily get specialized progress for "overall upload" with forkJoin of distinct requests easily without merging.
    
    // Refined approach:
    // We will process files IN PARALLEL (forkJoin) but we can't easily track aggregated upload progress of multiple requests in a single simple number without extra code.
    // BUT, the user wants "la barra di upload".
    // If we assume standard use case is 1 file, it's easy.
    // If multiple, we can emit progress as "average of all uploads" or just "uploading...".
    // Let's modify the signature to return `Observable<{ type: 'progress' | 'result', value: any }>` or similar?
    // The plan said `Observable<QuoteResult>` originally, now we need progress.
    // Let's change return type to `Observable<any>` or a specific union.
    
    // Let's handle just the first file for progress visualization simplicity if multiple are present, 
    // or better, create a wrapper that merges the progress.
    
    // Actually, looking at the previous code: `const requests = request.files.map(...)`.
    // If we have 3 files, we have 3 requests.
    // We can emit progress events.
    
    // START implementation for generalized progress:
    
    const file = request.files[0]; // Primary target for now to ensure we have a progress to show.
    // Ideally we should upload all.
    
    // For this task, to satisfy "bar disappears after upload", we really need to know when upload finishes.
    
    // Let's keep it robust:
    // If multiple files, we likely want to just process them.
    // Let's stick to the previous logic but capture progress events for at least one or all.
    
    if (request.files.length === 0) return of();

    // We will change the architecture slightly: 
    // We will execute requests and for EACH, we track progress. 
    // But we only have one boolean 'loading' and one 'progress' bar in UI.
    // Let's average the progress?
    
    // Simplification: The user probably uploads one file to check quote.
    // Let's implement support for the first file's progress to drive the UI bar, handling the rest in background/parallel.
    
    // Re-implementing the single file logic from the map, but enabled for progress.
    
    return new Observable(observer => {
        let completed = 0;
        let total = request.files.length;
        const results: BackendResponse[] = [];
        let grandTotal = 0; // For progress calculation if we wanted to average
        
        // We'll just track the "upload phase" of the bundle.
        // Actually, let's just use `concat` or `merge`?
        // Let's simplify: We will only track progress for the first file or "active" file.
        // But the previous code sent ALL files.
        
        // Let's change the return type to emit events.
        
        const uploads = request.files.map(file => {
             const formData = new FormData();
             formData.append('file', file);
             formData.append('machine', 'bambu_a1'); 
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
             if (environment.basicAuth) headers['Authorization'] = 'Basic ' + btoa(environment.basicAuth);

             return this.http.post<BackendResponse>(`${environment.apiUrl}/api/quote`, formData, { 
                 headers,
                 reportProgress: true,
                 observe: 'events'
             }).pipe(
                 map(event => ({ file, event })),
                 catchError(err => of({ file, error: err }))
             );
        });

        // We process all uploads.
        // We want to emit:
        // 1. Progress updates (average of all files?)
        // 2. Final QuoteResult
        
        const allProgress: number[] = new Array(request.files.length).fill(0);
        let completedRequests = 0;
        const finalResponses: any[] = [];

        // Subscribe to all
        uploads.forEach((obs, index) => {
            obs.subscribe({
                next: (wrapper: any) => {
                    if (wrapper.error) {
                        // handled in final calculation
                        finalResponses[index] = { success: false, data: { cost: { total:0 }, print_time_seconds:0, material_grams:0 } };
                        return;
                    }

                    const event = wrapper.event;
                    if (event.type === 1) { // HttpEventType.UploadProgress
                        if (event.total) {
                            const percent = Math.round((100 * event.loaded) / event.total);
                            allProgress[index] = percent;
                            // Emit average progress
                            const avg = Math.round(allProgress.reduce((a, b) => a + b, 0) / total);
                            observer.next(avg); // Emit number for progress
                        }
                    } else if (event.type === 4) { // HttpEventType.Response
                        allProgress[index] = 100;
                        finalResponses[index] = event.body;
                        completedRequests++;
                        
                        if (completedRequests === total) {
                            // All done
                            observer.next(100); // Ensure complete
                            
                            // Calculate Totals
                            const valid = finalResponses.filter(r => r && r.success);
                            if (valid.length === 0 && finalResponses.length > 0) {
                                observer.error('All calculations failed.');
                                return;
                            }
                            
                            let totalPrice = 0;
                            let totalTime = 0;
                            let totalWeight = 0;
                            let setupCost = 10;

                            valid.forEach(res => {
                                totalPrice += res.data.cost.total;
                                totalTime += res.data.print_time_seconds;
                                totalWeight += res.data.material_grams;
                            });

                            totalPrice = (totalPrice * request.quantity) + setupCost;
                            totalWeight = totalWeight * request.quantity;
                            totalTime = totalTime * request.quantity;

                            const totalHours = Math.floor(totalTime / 3600);
                            const totalMinutes = Math.ceil((totalTime % 3600) / 60);

                            const result: QuoteResult = {
                                price: Math.round(totalPrice * 100) / 100,
                                currency: 'CHF',
                                printTimeHours: totalHours,
                                printTimeMinutes: totalMinutes,
                                materialUsageGrams: Math.ceil(totalWeight),
                                setupCost
                            };
                            
                            observer.next(result); // Emit final object
                            observer.complete();
                        }
                    }
                },
                error: (err) => {
                    console.error('Error in request', err);
                    finalResponses[index] = { success: false };
                    completedRequests++;
                    if (completedRequests === total) {
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
