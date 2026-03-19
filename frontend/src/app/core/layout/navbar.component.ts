import {CommonModule, NgOptimizedImage} from '@angular/common';
import {
  afterNextRender,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  NavigationStart,
  Router,
  RouterLink,
  RouterLinkActive,
} from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { LanguageService } from '../services/language.service';
import { routes } from '../../app.routes';
import {
  ShopCartItem,
  ShopService,
} from '../../features/shop/services/shop.service';
import { finalize } from 'rxjs';
import {
  findColorHex,
  resolveLocalizedColorLabel,
} from '../constants/colors.const';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, TranslateModule, NgOptimizedImage],
  templateUrl: './navbar.component.html',
  styleUrls: ['./navbar.component.scss'],
})
export class NavbarComponent {
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);
  readonly shopService = inject(ShopService);

  isMenuOpen = false;
  readonly isCartOpen = signal(false);
  readonly cartMutating = signal(false);
  readonly busyLineItemId = signal<string | null>(null);

  readonly cart = this.shopService.cart;
  readonly cartLoading = this.shopService.cartLoading;
  readonly cartItems = computed(() =>
    (this.cart()?.items ?? []).filter(
      (item) => item.lineItemType === 'SHOP_PRODUCT',
    ),
  );
  readonly cartHasItems = computed(() => this.cartItems().length > 0);
  readonly cartItemCount = this.shopService.cartItemCount;

  readonly languageOptions: Array<{
    value: 'it' | 'en' | 'de' | 'fr';
    label: string;
  }> = [
    { value: 'it', label: 'IT' },
    { value: 'en', label: 'EN' },
    { value: 'de', label: 'DE' },
    { value: 'fr', label: 'FR' },
  ];

  constructor(public langService: LanguageService) {
    afterNextRender(() => {
      this.scheduleCartWarmup();
    });

    this.router.events
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((event) => {
        if (event instanceof NavigationStart) {
          this.closeMenu();
          this.closeCart();
        }
      });
  }

  onLanguageChange(event: Event): void {
    const select = event.target as HTMLSelectElement;
    const lang = select.value as 'it' | 'en' | 'de' | 'fr';
    this.langService.switchLang(lang);
  }

  toggleMenu() {
    this.isMenuOpen = !this.isMenuOpen;
  }

  closeMenu() {
    this.isMenuOpen = false;
  }

  toggleCart(): void {
    this.closeMenu();
    this.isCartOpen.update((open) => !open);
    if (this.isCartOpen()) {
      this.loadCartIfNeeded();
    }
  }

  closeCart(): void {
    this.isCartOpen.set(false);
  }

  increaseQuantity(item: ShopCartItem): void {
    this.updateItemQuantity(item, (item.quantity ?? 0) + 1);
  }

  decreaseQuantity(item: ShopCartItem): void {
    const nextQuantity = Math.max(1, (item.quantity ?? 1) - 1);
    this.updateItemQuantity(item, nextQuantity);
  }

  removeItem(item: ShopCartItem): void {
    this.cartMutating.set(true);
    this.busyLineItemId.set(item.id);
    this.shopService
      .removeCartItem(item.id)
      .pipe(
        finalize(() => {
          this.cartMutating.set(false);
          this.busyLineItemId.set(null);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  goToCheckout(): void {
    const sessionId = this.shopService.cartSessionId();
    if (!sessionId) {
      return;
    }

    this.closeCart();
    this.router.navigate(['/', this.langService.selectedLang(), 'checkout'], {
      queryParams: {
        session: sessionId,
      },
    });
  }

  cartItemName(item: ShopCartItem): string {
    return (
      item.displayName || item.shopProductName || item.originalFilename || '-'
    );
  }

  cartItemVariant(item: ShopCartItem): string | null {
    return item.shopVariantLabel || this.cartItemColor(item);
  }

  cartItemColor(item: ShopCartItem): string | null {
    return (
      resolveLocalizedColorLabel(this.langService.selectedLang(), {
        fallback: item.shopVariantColorName ?? item.colorCode,
        it: item.shopVariantColorLabelIt,
        en: item.shopVariantColorLabelEn,
        de: item.shopVariantColorLabelDe,
        fr: item.shopVariantColorLabelFr,
      }) ??
      item.shopVariantColorName ??
      item.colorCode
    );
  }

  cartItemColorHex(item: ShopCartItem): string {
    return (
      item.shopVariantColorHex ||
      findColorHex(item.shopVariantColorName) ||
      findColorHex(item.colorCode) ||
      '#c9ced6'
    );
  }

  trackByCartItem(_index: number, item: ShopCartItem): string {
    return item.id;
  }

  private updateItemQuantity(item: ShopCartItem, quantity: number): void {
    this.cartMutating.set(true);
    this.busyLineItemId.set(item.id);
    this.shopService
      .updateCartItem(item.id, quantity)
      .pipe(
        finalize(() => {
          this.cartMutating.set(false);
          this.busyLineItemId.set(null);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  private scheduleCartWarmup(): void {
    if (typeof window === 'undefined') {
      this.loadCartIfNeeded();
      return;
    }

    const warmup = () => this.loadCartIfNeeded();
    const idleCallback = (
      window as Window & {
        requestIdleCallback?: (
          callback: IdleRequestCallback,
          options?: IdleRequestOptions,
        ) => number;
      }
    ).requestIdleCallback;

    if (typeof idleCallback === 'function') {
      idleCallback(() => warmup(), { timeout: 1500 });
      return;
    }

    window.setTimeout(warmup, 300);
  }

  private loadCartIfNeeded(): void {
    if (this.shopService.cartLoaded() || this.shopService.cartLoading()) {
      return;
    }

    this.shopService
      .loadCart()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        error: () => {
          this.shopService.cart.set(null);
        },
      });
  }

  protected readonly routes = routes;
}
