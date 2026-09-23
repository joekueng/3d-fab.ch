import { OrderInformationComponent } from '../order-information/order-information.component';
import {
  OrderInformationService,
  InformationModel,
} from '../order-information/order-information.service';
import { Component, OnInit, OnDestroy, HostListener, DestroyRef, PLATFORM_ID, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { AppButtonComponent } from '../../shared/components/app-button/app-button.component';
import { AppCardComponent } from '../../shared/components/app-card/app-card.component';
import { QuoteEstimatorService } from '../calculator/services/quote-estimator.service';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { environment } from '../../../environments/environment';
import {
  findColorHex,
  resolveLocalizedColorLabel,
} from '../../core/constants/colors.const';
import {
  PriceBreakdownComponent,
  PriceBreakdownRow,
} from '../../shared/components/price-breakdown/price-breakdown.component';
import { downloadBlobInBrowser } from '../../core/utils/browser-download';

interface PublicOrderItem {
  clientModelKey?: string;
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
  shopVariantColorLabelIt?: string;
  shopVariantColorLabelEn?: string;
  shopVariantColorLabelDe?: string;
  shopVariantColorLabelFr?: string;
  shopVariantColorHex?: string;
  filamentVariantDisplayName?: string;
  filamentColorName?: string;
  filamentColorLabelIt?: string;
  filamentColorLabelEn?: string;
  filamentColorLabelDe?: string;
  filamentColorLabelFr?: string;
  filamentColorHex?: string;
  quality?: string;
  nozzleDiameterMm?: number;
  layerHeightMm?: number;
  infillPercent?: number;
  infillPattern?: string;
  supportsEnabled?: boolean;
  requiresSplitPrinting?: boolean;
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
  isCadOrder?: boolean;
  cadHours?: number;
  cadTotalChf?: number;
  cadFileCount?: number;
  cadFileDownloadAvailable?: boolean;
  items?: PublicOrderItem[];
}

@Component({
  selector: 'app-order',
  standalone: true,
  imports: [
    OrderInformationComponent,
    CommonModule,
    AppButtonComponent,
    AppCardComponent,
    TranslateModule,
    PriceBreakdownComponent,
  ],
  templateUrl: './order.component.html',
  styleUrl: './order.component.scss',
})
export class OrderComponent implements OnInit, OnDestroy {
  private readonly destroyRef = inject(DestroyRef);
  private refreshTimer?: ReturnType<typeof setTimeout>;
  private requestVersion = 0;
  private loadingOrder = false;
  private reportingPayment = false;
  private destroyed = false;
  private readonly openedAt = Date.now();
  readonly trackingSteps = ['PENDING', 'REPORTED', 'PAID', 'PRODUCTION', 'SHIPPED'];
  readonly informationService = inject(OrderInformationService);
  get informationModels(): InformationModel[] {
    return (this.order()?.items || []).map((item, index) => ({
      label: `${index + 1}. ${item.originalFilename || item.displayName || item.id}`,
      value: item.clientModelKey || item.id,
    }));
  }
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private quoteService = inject(QuoteEstimatorService);
  private translate = inject(TranslateService);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

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
      const token = new URLSearchParams(this.route.snapshot.fragment || '').get(
        'informationKey',
      );
      if (token && this.isBrowser) {
        this.informationService.rememberOrder(this.orderId, token);
        void this.router.navigate([], {
          relativeTo: this.route,
          fragment: undefined,
          queryParamsHandling: 'preserve',
          replaceUrl: true,
        });
      }
      if (this.isBrowser) {
        void this.informationService
          .resumeOrder(this.orderId)
          .catch(() => undefined)
          .finally(() => {
            if (!this.destroyed) {
              this.loadOrder();
              this.loadTwintPayment();
            }
          });
      } else {
        this.loadOrder();
        this.loadTwintPayment();
      }
    } else {
      this.error.set('ORDER.ERR_ID_NOT_FOUND');
      this.loading.set(false);
    }
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    clearTimeout(this.refreshTimer);
  }

  @HostListener('document:visibilitychange')
  @HostListener('window:focus')
  refreshWhenVisible(): void {
    clearTimeout(this.refreshTimer);
    if (this.isBrowser && !document.hidden) this.loadOrder();
  }

  private scheduleRefresh(): void {
    clearTimeout(this.refreshTimer);
    if (!this.isBrowser || this.destroyed || document.hidden) return;
    if (['COMPLETED', 'CANCELLED'].includes(this.order()?.status ?? '')) return;
    const awaitingPayment = this.order()?.status === 'PENDING_PAYMENT';
    const delay = awaitingPayment && Date.now() - this.openedAt < 10 * 60_000 ? 10_000 : 60_000;
    this.refreshTimer = setTimeout(() => this.loadOrder(), delay);
  }

  loadOrder(): void {
    if (!this.orderId || this.destroyed || this.loadingOrder || this.reportingPayment) return;
    clearTimeout(this.refreshTimer);
    const version = ++this.requestVersion;
    this.loadingOrder = true;
    this.quoteService.getOrder(this.orderId).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => {
        this.loadingOrder = false;
        this.scheduleRefresh();
      }),
    ).subscribe({
      next: (order) => {
        if (version !== this.requestVersion) return;
        this.order.set(order);
        this.error.set(null);
        this.loading.set(false);
      },
      error: () => {
        if (version !== this.requestVersion) return;
        if (!this.order()) this.error.set('ORDER.ERR_LOAD_ORDER');
        this.loading.set(false);
      },
    });
  }

  trackingStep(order: PublicOrder): number {
    switch (order.status) {
      case 'PENDING_PAYMENT': return order.paymentStatus === 'REPORTED' ? 1 : 0;
      case 'PAID': return 2;
      case 'IN_PRODUCTION': return 3;
      case 'SHIPPED': return 4;
      case 'COMPLETED': return 5;
      default: return -1;
    }
  }

  selectPayment(method: 'twint' | 'bill'): void {
    this.selectedPaymentMethod = method;
  }

  downloadQrInvoice() {
    if (!this.isBrowser) {
      return;
    }
    const orderId = this.orderId;
    if (!orderId) return;
    this.quoteService.getOrderConfirmation(orderId).subscribe({
      next: (blob) => {
        const fallbackOrderNumber = this.extractOrderNumber(orderId);
        const orderNumber = this.order()?.orderNumber ?? fallbackOrderNumber;
        downloadBlobInBrowser(blob, `qr-invoice-${orderNumber}.pdf`);
      },
      error: (err) => console.error('Failed to download QR invoice', err),
    });
  }

  downloadCadFiles(): void {
    if (!this.isBrowser) {
      return;
    }
    const orderId = this.orderId;
    if (!orderId) return;

    this.quoteService.getOrderCadFiles(orderId).subscribe({
      next: (response) => {
        const blob = response.body;
        if (!blob) {
          return;
        }
        const order = this.order();
        const filename =
          this.filenameFromContentDisposition(
            response.headers.get('content-disposition'),
          ) ?? this.fallbackCadDownloadName(order, orderId);
        downloadBlobInBrowser(blob, filename);
      },
      error: (err) => {
        console.error('Failed to download CAD files', err);
        this.error.set('ORDER.ERR_DOWNLOAD_CAD_FILES');
      },
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
    if (this.isBrowser && openUrl) {
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
    if (!this.orderId || !this.selectedPaymentMethod || this.reportingPayment) {
      return;
    }

    this.reportingPayment = true;
    ++this.requestVersion; // Invalidate any GET started before this mutation.
    clearTimeout(this.refreshTimer);
    this.quoteService
      .reportPayment(this.orderId, this.selectedPaymentMethod)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => {
        this.reportingPayment = false;
        this.scheduleRefresh();
      }))
      .subscribe({
        next: (order) => {
          this.order.set(order);
          this.error.set(null);
        },
        error: (err) => {
          console.error('Failed to report payment', err);
          this.error.set('ORDER.ERR_REPORT_PAYMENT');
        },
      });
  }

  getDisplayOrderNumber(order: PublicOrder | null): string {
    if (order?.orderNumber) {
      return order.orderNumber;
    }
    if (order?.id) {
      return this.extractOrderNumber(order.id);
    }
    return this.translate.instant('ORDER.NOT_AVAILABLE');
  }

  orderPriceBreakdownRows(order: PublicOrder | null): PriceBreakdownRow[] {
    return [
      {
        labelKey: 'PAYMENT.SUBTOTAL',
        amount: order?.subtotalChf ?? 0,
      },
      {
        label: this.translate.instant('ORDER.CAD_SERVICE', {
          hours: order?.cadHours || 0,
        }),
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

    return this.localizedColorLabel(item, 'shop');
  }

  itemColorLabel(item: PublicOrderItem): string {
    return (
      this.localizedColorLabel(item, 'shop') ||
      this.localizedColorLabel(item, 'filament') ||
      String(item?.colorCode ?? '').trim() ||
      this.translate.instant('ORDER.NOT_AVAILABLE')
    );
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

    return findColorHex(rawColor);
  }

  showItemMaterial(item: PublicOrderItem): boolean {
    return !this.isShopItem(item);
  }

  showItemPrintMetrics(item: PublicOrderItem): boolean {
    return !this.isShopItem(item);
  }

  private localizedColorLabel(
    item: PublicOrderItem,
    source: 'shop' | 'filament',
  ): string | null {
    if (source === 'shop') {
      return resolveLocalizedColorLabel(this.translate.currentLang, {
        fallback: item.shopVariantColorName,
        it: item.shopVariantColorLabelIt,
        en: item.shopVariantColorLabelEn,
        de: item.shopVariantColorLabelDe,
        fr: item.shopVariantColorLabelFr,
      });
    }

    return resolveLocalizedColorLabel(this.translate.currentLang, {
      fallback: item.filamentColorName ?? item.colorCode,
      it: item.filamentColorLabelIt,
      en: item.filamentColorLabelEn,
      de: item.filamentColorLabelDe,
      fr: item.filamentColorLabelFr,
    });
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

  private filenameFromContentDisposition(header: string | null): string | null {
    if (!header) {
      return null;
    }

    const encodedMatch = /filename\*=UTF-8''([^;]+)/i.exec(header);
    if (encodedMatch?.[1]) {
      try {
        return decodeURIComponent(encodedMatch[1].trim());
      } catch {
        return encodedMatch[1].trim();
      }
    }

    const quotedMatch = /filename="([^"]+)"/i.exec(header);
    if (quotedMatch?.[1]) {
      return quotedMatch[1].trim();
    }

    const plainMatch = /filename=([^;]+)/i.exec(header);
    return plainMatch?.[1]?.trim() || null;
  }

  private fallbackCadDownloadName(
    order: PublicOrder | null,
    orderId: string,
  ): string {
    const orderNumber = order?.orderNumber ?? this.extractOrderNumber(orderId);
    const extension = (order?.cadFileCount ?? 0) > 1 ? 'zip' : 'stl';
    return `cad-files-${orderNumber}.${extension}`;
  }
}
