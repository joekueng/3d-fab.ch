import { CommonModule, isPlatformBrowser } from '@angular/common';
import { Component, PLATFORM_ID, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  AdminOrder,
  AdminOrderItem,
  AdminOrdersService,
} from '../services/admin-orders.service';
import { CopyOnClickDirective } from '../../../shared/directives/copy-on-click.directive';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, CopyOnClickDirective],
  templateUrl: './admin-dashboard.component.html',
  styleUrl: './admin-dashboard.component.scss',
})
export class AdminDashboardComponent implements OnInit {
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly adminOrdersService = inject(AdminOrdersService);

  orders: AdminOrder[] = [];
  filteredOrders: AdminOrder[] = [];
  selectedOrder: AdminOrder | null = null;
  selectedStatus = '';
  selectedPaymentMethod = 'OTHER';
  orderSearchTerm = '';
  paymentStatusFilter = 'ALL';
  orderStatusFilter = 'ALL';
  orderTypeFilter = 'ALL';
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
    'CANCELLED',
  ];
  readonly paymentMethodOptions = [
    'TWINT',
    'BANK_TRANSFER',
    'CARD',
    'CASH',
    'OTHER',
  ];
  readonly paymentStatusFilterOptions = [
    'ALL',
    'PENDING',
    'REPORTED',
    'COMPLETED',
  ];
  readonly orderStatusFilterOptions = [
    'ALL',
    'PENDING_PAYMENT',
    'PAID',
    'IN_PRODUCTION',
    'SHIPPED',
    'COMPLETED',
    'CANCELLED',
  ];
  readonly orderTypeFilterOptions = ['ALL', 'SHOP', 'CALCULATOR', 'MIXED'];

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
          const exists = orders.find(
            (order) => order.id === this.selectedOrder?.id,
          );
          const selectedIsVisible = this.filteredOrders.some(
            (order) => order.id === this.selectedOrder?.id,
          );
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
      },
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

  onOrderTypeFilterChange(value: string): void {
    this.orderTypeFilter = value || 'ALL';
    this.applyListFiltersAndSelection();
  }

  openDetails(orderId: string): void {
    this.detailLoading = true;
    this.adminOrdersService.getOrder(orderId).subscribe({
      next: (order) => {
        this.selectedOrder = order;
        this.selectedStatus = order.status;
        this.selectedPaymentMethod = order.paymentMethod || 'OTHER';
        this.showPrintDetails =
          this.showPrintDetails && this.hasPrintItems(order);
        this.detailLoading = false;
      },
      error: () => {
        this.detailLoading = false;
        this.errorMessage = 'Impossibile caricare il dettaglio ordine.';
      },
    });
  }

  updatePaymentMethod(): void {
    if (!this.selectedOrder || this.confirmingPayment) {
      return;
    }

    this.confirmingPayment = true;
    this.adminOrdersService
      .updatePaymentMethod(this.selectedOrder.id, this.selectedPaymentMethod)
      .subscribe({
        next: (updatedOrder) => {
          this.confirmingPayment = false;
          this.applyOrderUpdate(updatedOrder);
        },
        error: () => {
          this.confirmingPayment = false;
          this.errorMessage = 'Aggiornamento metodo pagamento non riuscito.';
        },
      });
  }

  updateStatus(): void {
    if (
      !this.selectedOrder ||
      this.updatingStatus ||
      !this.selectedStatus.trim()
    ) {
      return;
    }

    this.updatingStatus = true;
    this.adminOrdersService
      .updateOrderStatus(this.selectedOrder.id, {
        status: this.selectedStatus.trim(),
      })
      .subscribe({
        next: (updatedOrder) => {
          this.updatingStatus = false;
          this.applyOrderUpdate(updatedOrder);
        },
        error: () => {
          this.updatingStatus = false;
          this.errorMessage = 'Aggiornamento stato ordine non riuscito.';
        },
      });
  }

  downloadItemFile(itemId: string, filename: string): void {
    if (!this.selectedOrder) {
      return;
    }

    this.adminOrdersService
      .downloadOrderItemFile(this.selectedOrder.id, itemId)
      .subscribe({
        next: (blob) => {
          this.downloadBlob(blob, filename || `order-item-${itemId}`);
        },
        error: () => {
          this.errorMessage = 'Download file non riuscito.';
        },
      });
  }

  downloadConfirmation(): void {
    if (!this.selectedOrder) {
      return;
    }

    this.adminOrdersService
      .downloadOrderConfirmation(this.selectedOrder.id)
      .subscribe({
        next: (blob) => {
          this.downloadBlob(
            blob,
            `conferma-${this.selectedOrder?.orderNumber || this.selectedOrder?.id}.pdf`,
          );
        },
        error: () => {
          this.errorMessage = 'Download conferma ordine non riuscito.';
        },
      });
  }

  downloadInvoice(): void {
    if (!this.selectedOrder) {
      return;
    }

    this.adminOrdersService
      .downloadOrderInvoice(this.selectedOrder.id)
      .subscribe({
        next: (blob) => {
          this.downloadBlob(
            blob,
            `fattura-${this.selectedOrder?.orderNumber || this.selectedOrder?.id}.pdf`,
          );
        },
        error: () => {
          this.errorMessage = 'Download fattura non riuscito.';
        },
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
    if (!this.selectedOrder || !this.hasPrintItems(this.selectedOrder)) {
      return;
    }
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

  isShopItem(item: AdminOrderItem): boolean {
    return String(item?.itemType ?? '').toUpperCase() === 'SHOP_PRODUCT';
  }

  itemDisplayName(item: AdminOrderItem): string {
    const displayName = (item.displayName || '').trim();
    if (displayName) {
      return displayName;
    }

    const shopName = (item.shopProductName || '').trim();
    if (shopName) {
      return shopName;
    }

    return (item.originalFilename || '').trim() || '-';
  }

  itemVariantLabel(item: AdminOrderItem): string | null {
    const variantLabel = (item.shopVariantLabel || '').trim();
    if (variantLabel) {
      return variantLabel;
    }

    const colorName = (item.shopVariantColorName || '').trim();
    return colorName || null;
  }

  isHexColor(value?: string): boolean {
    return (
      typeof value === 'string' &&
      /^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/.test(value)
    );
  }

  getItemMaterialLabel(item: AdminOrderItem): string {
    const variantName = (item.filamentVariantDisplayName || '').trim();
    const materialCode = (item.materialCode || '').trim();
    if (!variantName) {
      return materialCode || '-';
    }
    if (!materialCode) {
      return variantName;
    }
    const normalizedVariant = variantName.toLowerCase();
    const normalizedCode = materialCode.toLowerCase();
    return normalizedVariant.includes(normalizedCode)
      ? variantName
      : `${variantName} (${materialCode})`;
  }

  getItemColorLabel(item: AdminOrderItem): string {
    const shopColorName = (item.shopVariantColorName || '').trim();
    if (shopColorName) {
      return shopColorName;
    }

    const colorName = (item.filamentColorName || '').trim();
    const colorCode = (item.colorCode || '').trim();
    return colorName || colorCode || '-';
  }

  getItemColorHex(item: AdminOrderItem): string | null {
    const shopColorHex = (item.shopVariantColorHex || '').trim();
    if (this.isHexColor(shopColorHex)) {
      return shopColorHex;
    }

    const variantHex = (item.filamentColorHex || '').trim();
    if (this.isHexColor(variantHex)) {
      return variantHex;
    }
    const code = (item.colorCode || '').trim();
    if (this.isHexColor(code)) {
      return code;
    }
    return null;
  }

  getItemColorCodeSuffix(item: AdminOrderItem): string | null {
    const colorHex = this.getItemColorHex(item);
    if (!colorHex) {
      return null;
    }
    return colorHex === this.getItemColorLabel(item) ? null : colorHex;
  }

  formatSupports(value?: boolean): string {
    if (value === true) {
      return 'Sì';
    }
    if (value === false) {
      return 'No';
    }
    return '-';
  }

  formatSupportsState(value?: boolean): string {
    if (value === true) {
      return 'Supporti ON';
    }
    if (value === false) {
      return 'Supporti OFF';
    }
    return 'Supporti -';
  }

  showItemMaterial(item: AdminOrderItem): boolean {
    return !this.isShopItem(item);
  }

  showItemPrintDetails(item: AdminOrderItem): boolean {
    return !this.isShopItem(item);
  }

  hasShopItems(order: AdminOrder | null): boolean {
    return (order?.items || []).some((item) => this.isShopItem(item));
  }

  hasPrintItems(order: AdminOrder | null): boolean {
    return (order?.items || []).some((item) => !this.isShopItem(item));
  }

  printItems(order: AdminOrder | null): AdminOrderItem[] {
    return (order?.items || []).filter((item) => !this.isShopItem(item));
  }

  orderKind(order: AdminOrder | null): 'SHOP' | 'CALCULATOR' | 'MIXED' {
    const hasShop = this.hasShopItems(order);
    const hasPrint = this.hasPrintItems(order);

    if (hasShop && hasPrint) {
      return 'MIXED';
    }
    if (hasShop) {
      return 'SHOP';
    }
    return 'CALCULATOR';
  }

  orderKindLabel(order: AdminOrder | null): string {
    switch (this.orderKind(order)) {
      case 'SHOP':
        return 'Shop';
      case 'MIXED':
        return 'Misto';
      default:
        return 'Calcolatore';
    }
  }

  downloadItemLabel(item: AdminOrderItem): string {
    return this.isShopItem(item) ? 'Scarica modello' : 'Scarica file';
  }

  isSelected(orderId: string): boolean {
    return this.selectedOrder?.id === orderId;
  }

  private applyOrderUpdate(updatedOrder: AdminOrder): void {
    this.orders = this.orders.map((order) =>
      order.id === updatedOrder.id ? updatedOrder : order,
    );
    this.applyListFiltersAndSelection();
    this.selectedOrder = updatedOrder;
    this.selectedStatus = updatedOrder.status;
    this.selectedPaymentMethod =
      updatedOrder.paymentMethod || this.selectedPaymentMethod;
    this.showPrintDetails =
      this.showPrintDetails && this.hasPrintItems(updatedOrder);
  }

  private applyListFiltersAndSelection(): void {
    this.refreshFilteredOrders();

    if (this.filteredOrders.length === 0) {
      this.selectedOrder = null;
      this.selectedStatus = '';
      return;
    }

    if (
      !this.selectedOrder ||
      !this.filteredOrders.some((order) => order.id === this.selectedOrder?.id)
    ) {
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

      const matchesSearch =
        !term || fullUuid.includes(term) || shortUuid.includes(term);
      const matchesPayment =
        this.paymentStatusFilter === 'ALL' ||
        paymentStatus === this.paymentStatusFilter;
      const matchesOrderStatus =
        this.orderStatusFilter === 'ALL' ||
        orderStatus === this.orderStatusFilter;
      const matchesOrderType =
        this.orderTypeFilter === 'ALL' ||
        this.orderKind(order) === this.orderTypeFilter;

      return (
        matchesSearch &&
        matchesPayment &&
        matchesOrderStatus &&
        matchesOrderType
      );
    });
  }

  private downloadBlob(blob: Blob, filename: string): void {
    if (!this.isBrowser) {
      return;
    }
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    window.URL.revokeObjectURL(url);
  }
}
