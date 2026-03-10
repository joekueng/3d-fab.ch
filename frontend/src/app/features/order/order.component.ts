import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { AppButtonComponent } from '../../shared/components/app-button/app-button.component';
import { AppCardComponent } from '../../shared/components/app-card/app-card.component';
import { QuoteEstimatorService } from '../calculator/services/quote-estimator.service';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { environment } from '../../../environments/environment';
import {
  PriceBreakdownComponent,
  PriceBreakdownRow,
} from '../../shared/components/price-breakdown/price-breakdown.component';

interface PublicOrderItem {
  id: string;
  itemType?: string;
  originalFilename?: string;
  displayName?: string;
  materialCode?: string;
  colorCode?: string;
  filamentVariantId?: number;
  shopProductId?: string;
  shopProductVariantId?: string;
  shopProductSlug?: string;
  shopProductName?: string;
  shopVariantLabel?: string;
  shopVariantColorName?: string;
  shopVariantColorHex?: string;
  filamentVariantDisplayName?: string;
  filamentColorName?: string;
  filamentColorHex?: string;
  quality?: string;
  nozzleDiameterMm?: number;
  layerHeightMm?: number;
  infillPercent?: number;
  infillPattern?: string;
  supportsEnabled?: boolean;
  quantity: number;
  printTimeSeconds?: number;
  materialGrams?: number;
  unitPriceChf?: number;
  lineTotalChf?: number;
}

interface PublicOrder {
  id: string;
  orderNumber?: string;
  status?: string;
  paymentStatus?: string;
  paymentMethod?: string;
  subtotalChf?: number;
  shippingCostChf?: number;
  setupCostChf?: number;
  totalChf?: number;
  cadHours?: number;
  cadTotalChf?: number;
  items?: PublicOrderItem[];
}

@Component({
  selector: 'app-order',
  standalone: true,
  imports: [
    CommonModule,
    AppButtonComponent,
    AppCardComponent,
    TranslateModule,
    PriceBreakdownComponent,
  ],
  templateUrl: './order.component.html',
  styleUrl: './order.component.scss',
})
export class OrderComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private quoteService = inject(QuoteEstimatorService);
  private translate = inject(TranslateService);

  orderId: string | null = null;
  selectedPaymentMethod: 'twint' | 'bill' | null = 'twint';
  order = signal<PublicOrder | null>(null);
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
      this.error.set('ORDER.ERR_ID_NOT_FOUND');
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
        this.error.set('ORDER.ERR_LOAD_ORDER');
        this.loading.set(false);
      },
    });
  }

  selectPayment(method: 'twint' | 'bill'): void {
    this.selectedPaymentMethod = method;
  }

  downloadQrInvoice() {
    const orderId = this.orderId;
    if (!orderId) return;
    this.quoteService.getOrderConfirmation(orderId).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        const fallbackOrderNumber = this.extractOrderNumber(orderId);
        const orderNumber = this.order()?.orderNumber ?? fallbackOrderNumber;
        a.download = `qr-invoice-${orderNumber}.pdf`;
        a.click();
        window.URL.revokeObjectURL(url);
      },
      error: (err) => console.error('Failed to download QR invoice', err),
    });
  }

  loadTwintPayment() {
    if (!this.orderId) return;
    this.quoteService.getTwintPayment(this.orderId).subscribe({
      next: (res) => {
        const qrPath =
          typeof res.qrImageUrl === 'string'
            ? `${res.qrImageUrl}?size=360`
            : null;
        const qrDataUri =
          typeof res.qrImageDataUri === 'string' ? res.qrImageDataUri : null;
        this.twintOpenUrl.set(this.resolveApiUrl(res.openUrl));
        this.twintQrUrl.set(qrDataUri ?? this.resolveApiUrl(qrPath));
      },
      error: (err) => {
        console.error('Failed to load TWINT payment details', err);
      },
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

  getTwintButtonImageUrl(): string {
    const lang = this.translate.currentLang;
    if (lang === 'de') {
      return 'https://go.twint.ch/static/img/button_dark_de.svg';
    }
    if (lang === 'it') {
      return 'https://go.twint.ch/static/img/button_dark_it.svg';
    }
    if (lang === 'fr') {
      return 'https://go.twint.ch/static/img/button_dark_fr.svg';
    }
    // Default to EN for everything else (it, fr, en) as instructed or if not DE
    return 'https://go.twint.ch/static/img/button_dark_en.svg';
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

    this.quoteService
      .reportPayment(this.orderId, this.selectedPaymentMethod)
      .subscribe({
        next: (order) => {
          this.order.set(order);
          // The UI will re-render and show the 'REPORTED' state.
          // We stay on this page to let the user see the "In verifica"
          // status along with payment instructions.
        },
        error: (err) => {
          console.error('Failed to report payment', err);
          this.error.set('ORDER.ERR_REPORT_PAYMENT');
        },
      });
  }

  getDisplayOrderNumber(order: any): string {
    if (order?.orderNumber) {
      return order.orderNumber;
    }
    if (order?.id) {
      return this.extractOrderNumber(order.id);
    }
    return this.translate.instant('ORDER.NOT_AVAILABLE');
  }

  orderPriceBreakdownRows(order: any): PriceBreakdownRow[] {
    return [
      {
        labelKey: 'PAYMENT.SUBTOTAL',
        amount: order?.subtotalChf ?? 0,
      },
      {
        label: `Servizio CAD (${order?.cadHours || 0}h)`,
        amount: order?.cadTotalChf ?? 0,
        visible: (order?.cadTotalChf ?? 0) > 0,
      },
      {
        labelKey: 'PAYMENT.SHIPPING',
        amount: order?.shippingCostChf ?? 0,
      },
      {
        labelKey: 'PAYMENT.SETUP_FEE',
        amount: order?.setupCostChf ?? 0,
      },
    ];
  }

  private extractOrderNumber(orderId: string): string {
    return orderId.split('-')[0];
  }

  isShopItem(item: PublicOrderItem): boolean {
    return String(item?.itemType ?? '').toUpperCase() === 'SHOP_PRODUCT';
  }

  itemDisplayName(item: PublicOrderItem): string {
    const displayName = String(item?.displayName ?? '').trim();
    if (displayName) {
      return displayName;
    }

    const shopName = String(item?.shopProductName ?? '').trim();
    if (shopName) {
      return shopName;
    }

    return String(
      item?.originalFilename ?? this.translate.instant('ORDER.NOT_AVAILABLE'),
    );
  }

  itemVariantLabel(item: PublicOrderItem): string | null {
    const variantLabel = String(item?.shopVariantLabel ?? '').trim();
    if (variantLabel) {
      return variantLabel;
    }

    const colorName = String(item?.shopVariantColorName ?? '').trim();
    return colorName || null;
  }

  itemColorLabel(item: PublicOrderItem): string {
    const shopColor = String(item?.shopVariantColorName ?? '').trim();
    if (shopColor) {
      return shopColor;
    }

    const filamentColor = String(item?.filamentColorName ?? '').trim();
    if (filamentColor) {
      return filamentColor;
    }

    const rawColor = String(item?.colorCode ?? '').trim();
    return rawColor || this.translate.instant('ORDER.NOT_AVAILABLE');
  }

  itemColorHex(item: PublicOrderItem): string | null {
    const shopHex = String(item?.shopVariantColorHex ?? '').trim();
    if (this.isHexColor(shopHex)) {
      return shopHex;
    }

    const filamentHex = String(item?.filamentColorHex ?? '').trim();
    if (this.isHexColor(filamentHex)) {
      return filamentHex;
    }

    const rawColor = String(item?.colorCode ?? '').trim();
    if (this.isHexColor(rawColor)) {
      return rawColor;
    }

    return null;
  }

  showItemMaterial(item: PublicOrderItem): boolean {
    return !this.isShopItem(item);
  }

  showItemPrintMetrics(item: PublicOrderItem): boolean {
    return !this.isShopItem(item);
  }

  orderKind(order: PublicOrder | null): 'SHOP' | 'CALCULATOR' | 'MIXED' {
    const items = order?.items ?? [];
    const hasShop = items.some((item) => this.isShopItem(item));
    const hasPrint = items.some((item) => !this.isShopItem(item));

    if (hasShop && hasPrint) {
      return 'MIXED';
    }
    if (hasShop) {
      return 'SHOP';
    }
    return 'CALCULATOR';
  }

  orderKindLabel(order: PublicOrder | null): string {
    switch (this.orderKind(order)) {
      case 'SHOP':
        return this.translate.instant('ORDER.TYPE_SHOP');
      case 'MIXED':
        return this.translate.instant('ORDER.TYPE_MIXED');
      default:
        return this.translate.instant('ORDER.TYPE_CALCULATOR');
    }
  }

  private isHexColor(value?: string): boolean {
    return (
      typeof value === 'string' &&
      /^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/.test(value)
    );
  }
}
