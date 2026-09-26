import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type OrderStreamEvent = 'connected' | 'changed' | 'disconnected';

@Injectable({ providedIn: 'root' })
export class OrderEventsService {
  watch(orderId: string): Observable<OrderStreamEvent> {
    return new Observable((subscriber) => {
      if (typeof EventSource === 'undefined') {
        subscriber.next('disconnected');
        return;
      }
      const source = new EventSource(
        `${environment.apiUrl}/api/orders/${encodeURIComponent(orderId)}/events`,
      );
      source.onopen = () => subscriber.next('connected');
      source.addEventListener('order-changed', () => subscriber.next('changed'));
      source.onerror = () => subscriber.next('disconnected');
      return () => source.close();
    });
  }
}
