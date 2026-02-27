import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { AdminOrder, AdminOrdersService } from '../services/admin-orders.service';

@Component({
  selector: 'app-admin-orders-past',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './admin-orders-past.component.html',
  styleUrl: './admin-orders-past.component.scss'
})
export class AdminOrdersPastComponent implements OnInit {
  private readonly adminOrdersService = inject(AdminOrdersService);

  orders: AdminOrder[] = [];
  loading = false;
  errorMessage: string | null = null;

  ngOnInit(): void {
    this.loadOrders();
  }

  loadOrders(): void {
    this.loading = true;
    this.errorMessage = null;
    this.adminOrdersService.listOrders().subscribe({
      next: (orders) => {
        this.orders = orders.filter((order) =>
          order.paymentStatus === 'COMPLETED' || order.status !== 'PENDING_PAYMENT'
        );
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'Impossibile caricare gli ordini passati.';
      }
    });
  }
}
