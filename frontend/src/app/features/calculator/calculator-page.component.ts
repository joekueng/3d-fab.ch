import { Component, signal, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';

import { AppCardComponent } from '../../shared/components/app-card/app-card.component';
import { AppAlertComponent } from '../../shared/components/app-alert/app-alert.component';
import { UploadFormComponent } from './components/upload-form/upload-form.component';
import { QuoteResultComponent } from './components/quote-result/quote-result.component';
import { QuoteEstimatorService, QuoteRequest, QuoteResult } from './services/quote-estimator.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-calculator-page',
  standalone: true,
  imports: [CommonModule, TranslateModule, AppCardComponent, AppAlertComponent, UploadFormComponent, QuoteResultComponent],
  templateUrl: './calculator-page.component.html',
  styleUrl: './calculator-page.component.scss'
})
export class CalculatorPageComponent {
  mode = signal<any>('easy');
  loading = signal(false);
  uploadProgress = signal(0);
  result = signal<QuoteResult | null>(null);
  error = signal<boolean>(false);
  
  @ViewChild('uploadForm') uploadForm!: UploadFormComponent;
  @ViewChild('resultCol') resultCol!: ElementRef;

  constructor(private estimator: QuoteEstimatorService, private router: Router) {}

  onCalculate(req: QuoteRequest) {
    this.currentRequest = req;
    this.loading.set(true);
    this.uploadProgress.set(0);
    this.error.set(false);
    this.result.set(null);

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
        }
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      }
    });
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
