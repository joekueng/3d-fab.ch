import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { delay } from 'rxjs/operators';

export interface QuoteRequest {
  file: File;
  material: string;
  quality: string;
  quantity: number;
  notes?: string;
  clientType: 'business' | 'private';
  mode: 'easy' | 'advanced';
}

export interface QuoteResult {
  price: number;
  currency: string;
  printTimeHours: number;
  materialUsageGrams: number;
  setupCost: number;
}

@Injectable({
  providedIn: 'root'
})
export class QuoteEstimatorService {
  
  calculate(request: QuoteRequest): Observable<QuoteResult> {
    // Mock logic
    const basePrice = request.clientType === 'business' ? 50 : 20;
    const materialCost = request.material === 'PETG' ? 1.5 : (request.material === 'TPU' ? 2 : 1);
    const qualityMult = request.quality === 'High' ? 1.5 : (request.quality === 'Draft' ? 0.8 : 1);
    
    const estimatedPrice = (basePrice * materialCost * qualityMult * request.quantity) + 10; // +10 setup
    
    return of({
      price: Math.round(estimatedPrice * 100) / 100,
      currency: 'EUR',
      printTimeHours: Math.floor(Math.random() * 24) + 2,
      materialUsageGrams: Math.floor(Math.random() * 500) + 50,
      setupCost: 10
    }).pipe(delay(1500)); // Simulate network latency
  }
}
