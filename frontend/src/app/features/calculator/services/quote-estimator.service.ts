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
  material?: string;
  color?: string;
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
// ... (skip down to calculate logic)
                            finalResponses.forEach((res, idx) => {
                                if (res && res.success) {
                                    // Find original item to get color
                                    const originalItem = request.items[idx]; 
                                    // Note: responses and request.items are index-aligned because we mapped them
                                    
                                    items.push({
                                        fileName: res.fileName,
                                        unitPrice: res.data.cost.total,
                                        unitTime: res.data.print_time_seconds,
                                        unitWeight: res.data.material_grams,
                                        quantity: res.originalQty,
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
