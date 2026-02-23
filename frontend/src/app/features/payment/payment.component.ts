import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { AppButtonComponent } from '../../shared/components/app-button/app-button.component';
import { AppCardComponent } from '../../shared/components/app-card/app-card.component';
import { QuoteEstimatorService } from '../calculator/services/quote-estimator.service';
import { TranslateModule } from '@ngx-translate/core';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-payment',
  standalone: true,
  imports: [CommonModule, AppButtonComponent, AppCardComponent, TranslateModule],
  templateUrl: './payment.component.html',
  styleUrl: './payment.component.scss'
})
export class PaymentComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private quoteService = inject(QuoteEstimatorService);

  orderId: string | null = null;
  selectedPaymentMethod: 'twint' | 'bill' | null = null;
  order = signal<any>(null);
  loading = signal(true);
  error = signal<string | null>(null);
  twintOpenUrl = signal<string | null>(null);
  twintQrUrl = signal<string | null>(null);

  ngOnInit(): void {
    this.orderId = this.route.snapshot.paramMap.get('orderId');
    if (this.orderId) {
      this.loadOrder();
      this.loadTwintPayment();
    } else {
      this.error.set('Order ID not found.');
      this.loading.set(false);
    }
  }

  loadOrder() {
    if (!this.orderId) return;
    this.quoteService.getOrder(this.orderId).subscribe({
      next: (order) => {
        this.order.set(order);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Failed to load order', err);
        this.error.set('Failed to load order details.');
        this.loading.set(false);
      }
    });
  }

  selectPayment(method: 'twint' | 'bill'): void {
    this.selectedPaymentMethod = method;
  }

  downloadInvoice() {
    const orderId = this.orderId;
    if (!orderId) return;
    this.quoteService.getOrderInvoice(orderId).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        const fallbackOrderNumber = this.extractOrderNumber(orderId);
        const orderNumber = this.order()?.orderNumber ?? fallbackOrderNumber;
        a.download = `invoice-${orderNumber}.pdf`;
        a.click();
        window.URL.revokeObjectURL(url);
      },
      error: (err) => console.error('Failed to download invoice', err)
    });
  }

  loadTwintPayment() {
    if (!this.orderId) return;
    this.quoteService.getTwintPayment(this.orderId).subscribe({
      next: (res) => {
        const qrPath = typeof res.qrImageUrl === 'string' ? `${res.qrImageUrl}?size=360` : null;
        const qrDataUri = typeof res.qrImageDataUri === 'string' ? res.qrImageDataUri : null;
        this.twintOpenUrl.set(this.resolveApiUrl(res.openUrl));
        this.twintQrUrl.set(qrDataUri ?? this.resolveApiUrl(qrPath));
      },
      error: (err) => {
        console.error('Failed to load TWINT payment details', err);
      }
    });
  }

  openTwintPayment(): void {
    const openUrl = this.twintOpenUrl();
    if (typeof window !== 'undefined' && openUrl) {
      window.open(openUrl, '_blank');
    }
  }

  getTwintQrUrl(): string {
    return this.twintQrUrl() ?? '';
  }

  onTwintQrError(): void {
    this.twintQrUrl.set(null);
  }

  private resolveApiUrl(urlOrPath: string | null | undefined): string | null {
    if (!urlOrPath) return null;
    if (urlOrPath.startsWith('http://') || urlOrPath.startsWith('https://')) {
      return urlOrPath;
    }
    const base = (environment.apiUrl || '').replace(/\/$/, '');
    const path = urlOrPath.startsWith('/') ? urlOrPath : `/${urlOrPath}`;
    return `${base}${path}`;
  }

  completeOrder(): void {
    if (!this.orderId || !this.selectedPaymentMethod) {
      return;
    }
    
    this.quoteService.reportPayment(this.orderId, this.selectedPaymentMethod).subscribe({
      next: (order) => {
        this.order.set(order);
        // The UI will re-render and show the 'REPORTED' state.
        // We stay on this page to let the user see the "In verifica" 
        // status along with payment instructions.
      },
      error: (err) => {
        console.error('Failed to report payment', err);
        this.error.set('Failed to report payment. Please try again.');
      }
    });
  }

  getDisplayOrderNumber(order: any): string {
    if (order?.orderNumber) {
      return order.orderNumber;
    }
    if (order?.id) {
      return this.extractOrderNumber(order.id);
    }
    return 'N/A';
  }

  private extractOrderNumber(orderId: string): string {
    return orderId.split('-')[0];
  }
}
