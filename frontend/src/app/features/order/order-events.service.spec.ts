import { OrderEventsService } from './order-events.service';

describe('OrderEventsService', () => {
  it('uses native reconnection and closes EventSource on unsubscribe', () => {
    const source = jasmine.createSpyObj<EventSource>('source', ['addEventListener', 'close']);
    const constructor = spyOn(window, 'EventSource').and.returnValue(source);
    const received: string[] = [];
    const subscription = new OrderEventsService().watch('order-id').subscribe(value => received.push(value));
    expect(constructor.calls.mostRecent().args[0]).toContain('/api/orders/order-id/events');
    source.onopen?.call(source, new Event('open'));
    source.onerror?.call(source, new Event('error'));
    expect(received).toEqual(['connected', 'disconnected']);
    expect(source.close).not.toHaveBeenCalled();
    subscription.unsubscribe();
    expect(source.close).toHaveBeenCalledTimes(1);
  });
});
