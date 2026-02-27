import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AdminAuthService } from '../services/admin-auth.service';
import { AdminOrder, AdminOrdersService } from '../services/admin-orders.service';

const SUPPORTED_LANGS = new Set(['it', 'en', 'de', 'fr']);

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './admin-dashboard.component.html',
  styleUrl: './admin-dashboard.component.scss'
})
export class AdminDashboardComponent implements OnInit {
  private readonly adminOrdersService = inject(AdminOrdersService);
  private readonly adminAuthService = inject(AdminAuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  orders: AdminOrder[] = [];
  selectedOrder: AdminOrder | null = null;
  loading = false;
  detailLoading = false;
  confirmingOrderId: string | null = null;
  errorMessage: string | null = null;

  ngOnInit(): void {
    this.loadOrders();
  }

  loadOrders(): void {
    this.loading = true;
    this.errorMessage = null;
    this.adminOrdersService.listOrders().subscribe({
      next: (orders) => {
        this.orders = orders;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'Impossibile caricare gli ordini.';
      }
    });
  }

  openDetails(orderId: string): void {
    this.detailLoading = true;
    this.adminOrdersService.getOrder(orderId).subscribe({
      next: (order) => {
        this.selectedOrder = order;
        this.detailLoading = false;
      },
      error: () => {
        this.detailLoading = false;
        this.errorMessage = 'Impossibile caricare il dettaglio ordine.';
      }
    });
  }

  confirmPayment(orderId: string): void {
    this.confirmingOrderId = orderId;
    this.adminOrdersService.confirmPayment(orderId).subscribe({
      next: (updatedOrder) => {
        this.confirmingOrderId = null;
        this.orders = this.orders.map((order) => order.id === updatedOrder.id ? updatedOrder : order);
        if (this.selectedOrder?.id === updatedOrder.id) {
          this.selectedOrder = updatedOrder;
        }
      },
      error: () => {
        this.confirmingOrderId = null;
        this.errorMessage = 'Conferma pagamento non riuscita.';
      }
    });
  }

  logout(): void {
    this.adminAuthService.logout().subscribe({
      next: () => {
        void this.router.navigate(['/', this.resolveLang(), 'admin', 'login']);
      },
      error: () => {
        void this.router.navigate(['/', this.resolveLang(), 'admin', 'login']);
      }
    });
  }

  private resolveLang(): string {
    for (const level of this.route.pathFromRoot) {
      const lang = level.snapshot.paramMap.get('lang');
      if (lang && SUPPORTED_LANGS.has(lang)) {
        return lang;
      }
    }
    return 'it';
  }
}
