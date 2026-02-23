import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { AppButtonComponent } from '../../shared/components/app-button/app-button.component';
import { AppCardComponent } from '../../shared/components/app-card/app-card.component';
import { QuoteEstimatorService } from '../calculator/services/quote-estimator.service';

@Component({
  selector: 'app-order-confirmed',
  standalone: true,
  imports: [CommonModule, TranslateModule, AppButtonComponent, AppCardComponent],
  templateUrl: './order-confirmed.component.html',
  styleUrl: './order-confirmed.component.scss'
})
export class OrderConfirmedComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private quoteService = inject(QuoteEstimatorService);

  orderId: string | null = null;
  orderNumber: string | null = null;
  order = signal<any>(null);

  ngOnInit(): void {
    this.orderId = this.route.snapshot.paramMap.get('orderId');
    if (!this.orderId) {
      return;
    }

    this.orderNumber = this.extractOrderNumber(this.orderId);
    this.quoteService.getOrder(this.orderId).subscribe({
      next: (order) => {
        this.order.set(order);
        this.orderNumber = order?.orderNumber ?? this.orderNumber;
      },
      error: () => {
        // Keep fallback derived from UUID when API is unavailable.
      }
    });
  }

  goHome(): void {
    this.router.navigate(['/']);
  }

  private extractOrderNumber(orderId: string): string {
    return orderId.split('-')[0];
  }
}
