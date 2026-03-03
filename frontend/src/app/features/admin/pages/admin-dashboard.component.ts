import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminOrder, AdminOrdersService } from '../services/admin-orders.service';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-dashboard.component.html',
  styleUrl: './admin-dashboard.component.scss'
})
export class AdminDashboardComponent implements OnInit {
  private readonly adminOrdersService = inject(AdminOrdersService);

  orders: AdminOrder[] = [];
  filteredOrders: AdminOrder[] = [];
  selectedOrder: AdminOrder | null = null;
  selectedStatus = '';
  selectedPaymentMethod = 'OTHER';
  orderSearchTerm = '';
  paymentStatusFilter = 'ALL';
  orderStatusFilter = 'ALL';
  showPrintDetails = false;
  loading = false;
  detailLoading = false;
  confirmingPayment = false;
  updatingStatus = false;
  errorMessage: string | null = null;
  readonly orderStatusOptions = [
    'PENDING_PAYMENT',
    'PAID',
    'IN_PRODUCTION',
    'SHIPPED',
    'COMPLETED',
    'CANCELLED'
  ];
  readonly paymentMethodOptions = ['TWINT', 'BANK_TRANSFER', 'CARD', 'CASH', 'OTHER'];
  readonly paymentStatusFilterOptions = ['ALL', 'PENDING', 'REPORTED', 'COMPLETED'];
  readonly orderStatusFilterOptions = [
    'ALL',
    'PENDING_PAYMENT',
    'PAID',
    'IN_PRODUCTION',
    'SHIPPED',
    'COMPLETED',
    'CANCELLED'
  ];

  ngOnInit(): void {
    this.loadOrders();
  }

  loadOrders(): void {
    this.loading = true;
    this.errorMessage = null;
    this.adminOrdersService.listOrders().subscribe({
      next: (orders) => {
        this.orders = orders;
        this.refreshFilteredOrders();

        if (!this.selectedOrder && this.filteredOrders.length > 0) {
          this.openDetails(this.filteredOrders[0].id);
        } else if (this.selectedOrder) {
          const exists = orders.find(order => order.id === this.selectedOrder?.id);
          const selectedIsVisible = this.filteredOrders.some(order => order.id === this.selectedOrder?.id);
          if (exists && selectedIsVisible) {
            this.openDetails(exists.id);
          } else if (this.filteredOrders.length > 0) {
            this.openDetails(this.filteredOrders[0].id);
          } else {
            this.selectedOrder = null;
            this.selectedStatus = '';
          }
        }
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'Impossibile caricare gli ordini.';
      }
    });
  }

  onSearchChange(value: string): void {
    this.orderSearchTerm = value;
    this.applyListFiltersAndSelection();
  }

  onPaymentStatusFilterChange(value: string): void {
    this.paymentStatusFilter = value || 'ALL';
    this.applyListFiltersAndSelection();
  }

  onOrderStatusFilterChange(value: string): void {
    this.orderStatusFilter = value || 'ALL';
    this.applyListFiltersAndSelection();
  }

  openDetails(orderId: string): void {
    this.detailLoading = true;
    this.adminOrdersService.getOrder(orderId).subscribe({
      next: (order) => {
        this.selectedOrder = order;
        this.selectedStatus = order.status;
        this.selectedPaymentMethod = order.paymentMethod || 'OTHER';
        this.detailLoading = false;
      },
      error: () => {
        this.detailLoading = false;
        this.errorMessage = 'Impossibile caricare il dettaglio ordine.';
      }
    });
  }

  confirmPayment(): void {
    if (!this.selectedOrder || this.confirmingPayment) {
      return;
    }

    this.confirmingPayment = true;
    this.adminOrdersService.confirmPayment(this.selectedOrder.id, this.selectedPaymentMethod).subscribe({
      next: (updatedOrder) => {
        this.confirmingPayment = false;
        this.applyOrderUpdate(updatedOrder);
      },
      error: () => {
        this.confirmingPayment = false;
        this.errorMessage = 'Conferma pagamento non riuscita.';
      }
    });
  }

  updateStatus(): void {
    if (!this.selectedOrder || this.updatingStatus || !this.selectedStatus.trim()) {
      return;
    }

    this.updatingStatus = true;
    this.adminOrdersService.updateOrderStatus(this.selectedOrder.id, {
      status: this.selectedStatus.trim()
    }).subscribe({
      next: (updatedOrder) => {
        this.updatingStatus = false;
        this.applyOrderUpdate(updatedOrder);
      },
      error: () => {
        this.updatingStatus = false;
        this.errorMessage = 'Aggiornamento stato ordine non riuscito.';
      }
    });
  }

  downloadItemFile(itemId: string, filename: string): void {
    if (!this.selectedOrder) {
      return;
    }

    this.adminOrdersService.downloadOrderItemFile(this.selectedOrder.id, itemId).subscribe({
      next: (blob) => {
        this.downloadBlob(blob, filename || `order-item-${itemId}`);
      },
      error: () => {
        this.errorMessage = 'Download file non riuscito.';
      }
    });
  }

  downloadConfirmation(): void {
    if (!this.selectedOrder) {
      return;
    }

    this.adminOrdersService.downloadOrderConfirmation(this.selectedOrder.id).subscribe({
      next: (blob) => {
        this.downloadBlob(blob, `conferma-${this.selectedOrder?.orderNumber || this.selectedOrder?.id}.pdf`);
      },
      error: () => {
        this.errorMessage = 'Download conferma ordine non riuscito.';
      }
    });
  }

  downloadInvoice(): void {
    if (!this.selectedOrder) {
      return;
    }

    this.adminOrdersService.downloadOrderInvoice(this.selectedOrder.id).subscribe({
      next: (blob) => {
        this.downloadBlob(blob, `fattura-${this.selectedOrder?.orderNumber || this.selectedOrder?.id}.pdf`);
      },
      error: () => {
        this.errorMessage = 'Download fattura non riuscito.';
      }
    });
  }

  onStatusChange(event: Event): void {
    const value = (event.target as HTMLSelectElement | null)?.value ?? '';
    this.selectedStatus = value;
  }

  onPaymentMethodChange(event: Event): void {
    const value = (event.target as HTMLSelectElement | null)?.value ?? 'OTHER';
    this.selectedPaymentMethod = value;
  }

  openPrintDetails(): void {
    this.showPrintDetails = true;
  }

  closePrintDetails(): void {
    this.showPrintDetails = false;
  }

  getQualityLabel(layerHeight?: number): string {
    if (!layerHeight || Number.isNaN(layerHeight)) {
      return '-';
    }
    if (layerHeight <= 0.12) {
      return 'Alta';
    }
    if (layerHeight <= 0.2) {
      return 'Standard';
    }
    return 'Bozza';
  }

  isHexColor(value?: string): boolean {
    return typeof value === 'string' && /^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/.test(value);
  }

  isSelected(orderId: string): boolean {
    return this.selectedOrder?.id === orderId;
  }

  private applyOrderUpdate(updatedOrder: AdminOrder): void {
    this.orders = this.orders.map((order) => order.id === updatedOrder.id ? updatedOrder : order);
    this.applyListFiltersAndSelection();
    this.selectedOrder = updatedOrder;
    this.selectedStatus = updatedOrder.status;
    this.selectedPaymentMethod = updatedOrder.paymentMethod || this.selectedPaymentMethod;
  }

  private applyListFiltersAndSelection(): void {
    this.refreshFilteredOrders();

    if (this.filteredOrders.length === 0) {
      this.selectedOrder = null;
      this.selectedStatus = '';
      return;
    }

    if (!this.selectedOrder || !this.filteredOrders.some(order => order.id === this.selectedOrder?.id)) {
      this.openDetails(this.filteredOrders[0].id);
    }
  }

  private refreshFilteredOrders(): void {
    const term = this.orderSearchTerm.trim().toLowerCase();
    this.filteredOrders = this.orders.filter((order) => {
      const fullUuid = order.id.toLowerCase();
      const shortUuid = (order.orderNumber || '').toLowerCase();
      const paymentStatus = (order.paymentStatus || 'PENDING').toUpperCase();
      const orderStatus = (order.status || '').toUpperCase();

      const matchesSearch = !term || fullUuid.includes(term) || shortUuid.includes(term);
      const matchesPayment = this.paymentStatusFilter === 'ALL' || paymentStatus === this.paymentStatusFilter;
      const matchesOrderStatus = this.orderStatusFilter === 'ALL' || orderStatus === this.orderStatusFilter;

      return matchesSearch && matchesPayment && matchesOrderStatus;
    });
  }

  private downloadBlob(blob: Blob, filename: string): void {
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    window.URL.revokeObjectURL(url);
  }
}
