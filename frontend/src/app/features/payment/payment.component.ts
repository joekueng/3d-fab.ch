import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { AppButtonComponent } from '../../shared/components/app-button/app-button.component';
import { AppCardComponent } from '../../shared/components/app-card/app-card.component';
import { QuoteEstimatorService } from '../calculator/services/quote-estimator.service';
import { TranslateModule } from '@ngx-translate/core';

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

  ngOnInit(): void {
    this.orderId = this.route.snapshot.paramMap.get('orderId');
    if (this.orderId) {
      this.loadOrder();
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
    if (!this.orderId) return;
    this.quoteService.getOrderInvoice(this.orderId).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `invoice-${this.orderId}.pdf`;
        a.click();
        window.URL.revokeObjectURL(url);
      },
      error: (err) => console.error('Failed to download invoice', err)
    });
  }

  completeOrder(): void {
    alert('Payment Simulated! Order marked as PAID.');
    this.router.navigate(['/']);
  }
}
