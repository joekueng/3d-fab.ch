import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { AppButtonComponent } from '../../shared/components/app-button/app-button.component';
import { AppCardComponent } from '../../shared/components/app-card/app-card.component';

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

  orderId: string | null = null;

  ngOnInit(): void {
    this.orderId = this.route.snapshot.paramMap.get('orderId');
  }

  goHome(): void {
    this.router.navigate(['/']);
  }
}
