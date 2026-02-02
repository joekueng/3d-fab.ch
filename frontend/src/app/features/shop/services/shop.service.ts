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
  providedIn: 'root'
})
export class ShopService {
  // Dati statici per ora
  private staticProducts: Product[] = [
    {
      id: '1',
      name: 'Filamento PLA Standard',
      description: 'Il classico per ogni stampa, facile e affidabile.',
      price: 24.90,
      category: 'Filamenti'
    },
    {
      id: '2',
      name: 'Filamento PETG Tough',
      description: 'Resistente agli urti e alle temperature.',
      price: 29.90,
      category: 'Filamenti'
    },
    {
      id: '3',
      name: 'Kit Ugelli (0.4mm)',
      description: 'Set di ricambio per estrusore FDM.',
      price: 15.00,
      category: 'Accessori'
    }
  ];

  getProducts(): Observable<Product[]> {
    return of(this.staticProducts);
  }

  getProductById(id: string): Observable<Product | undefined> {
    return of(this.staticProducts.find(p => p.id === id));
  }
}