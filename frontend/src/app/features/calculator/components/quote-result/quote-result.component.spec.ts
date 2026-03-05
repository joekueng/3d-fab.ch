import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { TranslateModule } from '@ngx-translate/core';
import { QuoteResultComponent } from './quote-result.component';
import { QuoteResult } from '../../services/quote-estimator.service';

describe('QuoteResultComponent', () => {
  let fixture: ComponentFixture<QuoteResultComponent>;
  let component: QuoteResultComponent;

  const createResult = (): QuoteResult => ({
    sessionId: 'session-1',
    items: [
      {
        id: 'line-1',
        fileName: 'part-a.stl',
        unitPrice: 2,
        unitTime: 120,
        unitWeight: 1.2,
        quantity: 2,
      },
      {
        id: 'line-2',
        fileName: 'part-b.stl',
        unitPrice: 1.5,
        unitTime: 60,
        unitWeight: 0.5,
        quantity: 1,
      },
    ],
    setupCost: 5,
    globalMachineCost: 0,
    currency: 'CHF',
    totalPrice: 0,
    totalTimeHours: 0,
    totalTimeMinutes: 0,
    totalWeight: 0,
  });

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        QuoteResultComponent,
        TranslateModule.forRoot(),
        HttpClientTestingModule,
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(QuoteResultComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('result', createResult());
    fixture.detectChanges();
  });

  it('emits quantity changes with clamped max quantity', () => {
    spyOn(component.itemChange, 'emit');

    component.updateQuantity(0, 999);
    component.flushQuantityUpdate(0);

    expect(component.items()[0].quantity).toBe(component.maxInputQuantity);
    expect(component.itemChange.emit).toHaveBeenCalledWith({
      id: 'line-1',
      index: 0,
      fileName: 'part-a.stl',
      quantity: component.maxInputQuantity,
    });
  });

  it('computes totals from local item quantities', () => {
    component.updateQuantity(1, 3);

    const totals = component.totals();
    expect(totals.price).toBe(13.5);
    expect(totals.hours).toBe(0);
    expect(totals.minutes).toBe(7);
    expect(totals.weight).toBe(4);
  });

  it('flags over-limit quantities for direct order', () => {
    component.updateQuantity(0, 101);
    expect(component.hasQuantityOverLimit()).toBeTrue();
  });
});
