import { Component, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ShopService, Product } from './services/shop.service';
import { AppButtonComponent } from '../../shared/components/app-button/app-button.component';

@Component({
  selector: 'app-product-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, TranslateModule, AppButtonComponent],
  templateUrl: './product-detail.component.html',
  styleUrl: './product-detail.component.scss'
})
export class ProductDetailComponent {
  // Input binding from router
  id = input<string>();
  
  product = signal<Product | undefined>(undefined);

  constructor(
    private shopService: ShopService,
    private translate: TranslateService
  ) {}

  ngOnInit() {
    const productId = this.id();
    if (productId) {
      this.shopService.getProductById(productId).subscribe(p => this.product.set(p));
    }
  }

  addToCart() {
    alert(this.translate.instant('SHOP.MOCK_ADD_CART'));
  }
}
