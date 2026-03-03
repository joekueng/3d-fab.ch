import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';

export interface Product {
  id: string;
  name: string;
  description: string;
  price: number;
  category: string;
}

@Injectable({
  providedIn: 'root',
})
export class ShopService {
  // Dati statici per ora
  private staticProducts: Product[] = [
    {
      id: '1',
      name: 'SHOP.PRODUCTS.P1.NAME',
      description: 'SHOP.PRODUCTS.P1.DESC',
      price: 24.9,
      category: 'SHOP.CATEGORIES.FILAMENTS',
    },
    {
      id: '2',
      name: 'SHOP.PRODUCTS.P2.NAME',
      description: 'SHOP.PRODUCTS.P2.DESC',
      price: 29.9,
      category: 'SHOP.CATEGORIES.FILAMENTS',
    },
    {
      id: '3',
      name: 'SHOP.PRODUCTS.P3.NAME',
      description: 'SHOP.PRODUCTS.P3.DESC',
      price: 15.0,
      category: 'SHOP.CATEGORIES.ACCESSORIES',
    },
  ];

  getProducts(): Observable<Product[]> {
    return of(this.staticProducts);
  }

  getProductById(id: string): Observable<Product | undefined> {
    return of(this.staticProducts.find((p) => p.id === id));
  }
}
