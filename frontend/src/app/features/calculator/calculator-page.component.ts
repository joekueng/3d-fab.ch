import { Component, signal, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';

import { AppCardComponent } from '../../shared/components/app-card/app-card.component';
import { AppAlertComponent } from '../../shared/components/app-alert/app-alert.component';
import { UploadFormComponent } from './components/upload-form/upload-form.component';
import { QuoteResultComponent } from './components/quote-result/quote-result.component';
import { QuoteRequest, QuoteResult, QuoteEstimatorService } from './services/quote-estimator.service';
import { UserDetailsComponent } from './components/user-details/user-details.component';
import { SuccessStateComponent } from '../../shared/components/success-state/success-state.component';
import { Router } from '@angular/router';

@Component({
  selector: 'app-calculator-page',
  standalone: true,
  imports: [CommonModule, TranslateModule, AppCardComponent, AppAlertComponent, UploadFormComponent, QuoteResultComponent, UserDetailsComponent, SuccessStateComponent],
  templateUrl: './calculator-page.component.html',
  styleUrl: './calculator-page.component.scss'
})
export class CalculatorPageComponent {
  mode = signal<any>('easy');
  step = signal<'upload' | 'quote' | 'details' | 'success'>('upload');
  
  loading = signal(false);
  uploadProgress = signal(0);
  result = signal<QuoteResult | null>(null);
  error = signal<boolean>(false);
  
  orderSuccess = signal(false);
  
  @ViewChild('uploadForm') uploadForm!: UploadFormComponent;
  @ViewChild('resultCol') resultCol!: ElementRef;

  constructor(private estimator: QuoteEstimatorService, private router: Router) {}

  onCalculate(req: QuoteRequest) {
    // ... (logic remains the same, simplified for diff)
    this.currentRequest = req;
    this.loading.set(true);
    this.uploadProgress.set(0);
    this.error.set(false);
    this.result.set(null);
    this.orderSuccess.set(false);

    // Auto-scroll on mobile to make analysis visible
    setTimeout(() => {
        if (this.resultCol && window.innerWidth < 768) {
             this.resultCol.nativeElement.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    }, 100);

    this.estimator.calculate(req).subscribe({
      next: (event) => {
        if (typeof event === 'number') {
            this.uploadProgress.set(event);
        } else {
            // It's the result
            this.result.set(event as QuoteResult);
            this.loading.set(false);
            this.uploadProgress.set(100);
            this.step.set('quote');
        }
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      }
    });
  }

  onProceed() {
    this.step.set('details');
  }

  onCancelDetails() {
    this.step.set('quote');
  }

  onSubmitOrder(orderData: any) {
    console.log('Order Submitted:', orderData);
    this.orderSuccess.set(true);
    this.step.set('success'); 
  }
  
  onNewQuote() {
      this.step.set('upload');
      this.result.set(null);
      this.orderSuccess.set(false);
      this.mode.set('easy'); // Reset to default
  }

  private currentRequest: QuoteRequest | null = null;

  onConsult() {
    if (!this.currentRequest) return;

    const req = this.currentRequest;
    let details = `Richiesta Preventivo:\n`;
    details += `- Materiale: ${req.material}\n`;
    details += `- Qualità: ${req.quality}\n`;
    
    details += `- File:\n`;
    req.items.forEach(item => {
        details += `  * ${item.file.name} (Qtà: ${item.quantity}`;
        if (item.color) {
            details += `, Colore: ${item.color}`;
        }
        details += `)\n`;
    });

    if (req.mode === 'advanced') {
       if (req.infillDensity) details += `- Infill: ${req.infillDensity}%\n`;
    }

    if (req.notes) details += `\nNote: ${req.notes}`;

    this.estimator.setPendingConsultation({
      files: req.items.map(i => i.file),
      message: details
    });

    this.router.navigate(['/contact']);
  }
}
