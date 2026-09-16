import { TestBed, fakeAsync, flushMicrotasks } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { of, Subject } from 'rxjs';
import { signal } from '@angular/core';
import { SessionEmailComponent } from './session-email.component';
import { QuoteEstimatorService } from '../../services/quote-estimator.service';
import { OrderInformationService } from '../../../order-information/order-information.service';

describe('SessionEmailComponent', () => {
  it('copies the saved server link without needing an email address', fakeAsync(() => {
    const estimator = jasmine.createSpyObj<QuoteEstimatorService>('estimator', [
      'sessionLink',
    ]);
    const link = 'https://example.test/it/calculator/basic?session=test';
    estimator.sessionLink.and.returnValue(of({ url: link }));
    const clipboard = spyOn(navigator.clipboard, 'writeText').and.returnValue(
      Promise.resolve(),
    );
    TestBed.configureTestingModule({
      imports: [SessionEmailComponent, TranslateModule.forRoot()],
      providers: [
        { provide: QuoteEstimatorService, useValue: estimator },
        {
          provide: OrderInformationService,
          useValue: { busy: signal(false), model: signal('') },
        },
      ],
    });
    const fixture = TestBed.createComponent(SessionEmailComponent);
    fixture.componentRef.setInput('sessionId', 'session');
    fixture.componentRef.setInput('expiresAt', '2026-12-16T12:00:00+01:00');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain(
      'SESSION_EMAIL.ACCESS_NOTICE',
    );
    expect(fixture.nativeElement.textContent).toContain(
      'SESSION_EMAIL.EXPIRY_LABEL',
    );
    const buttons: HTMLButtonElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('button'),
    );
    buttons
      .find((button) => button.textContent?.trim() === 'SESSION_EMAIL.COPY')!
      .click();
    flushMicrotasks();
    fixture.detectChanges();
    expect(clipboard).toHaveBeenCalledWith(link);
    expect(fixture.componentInstance.form.invalid).toBeTrue();
    expect(
      fixture.nativeElement.querySelector('[role=status]').textContent,
    ).toContain('SESSION_EMAIL.COPIED');
    fixture.destroy();
  }));

  it('requires an updated quote and prevents duplicate sends until completion', () => {
    const response = new Subject<void>();
    const estimator = jasmine.createSpyObj<QuoteEstimatorService>('estimator', [
      'emailSession',
    ]);
    estimator.emailSession.and.returnValue(response);
    TestBed.configureTestingModule({
      imports: [SessionEmailComponent, TranslateModule.forRoot()],
      providers: [
        { provide: QuoteEstimatorService, useValue: estimator },
        {
          provide: OrderInformationService,
          useValue: { busy: signal(false), model: signal('') },
        },
      ],
    });
    const fixture = TestBed.createComponent(SessionEmailComponent);
    fixture.componentRef.setInput('sessionId', 'session');
    fixture.componentRef.setInput('expiresAt', '2026-12-16T12:00:00+01:00');
    fixture.componentInstance.expanded.set(true);
    fixture.componentInstance.form.controls.email.setValue(
      'customer@example.test',
    );
    fixture.componentRef.setInput('recalculationRequired', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('form').textContent).toContain(
      'SESSION_EMAIL.EMAIL_PRIVACY',
    );
    expect(
      fixture.nativeElement.querySelector('button[type=submit]').disabled,
    ).toBeTrue();
    fixture.componentInstance.send();
    expect(estimator.emailSession).not.toHaveBeenCalled();
    fixture.componentRef.setInput('recalculationRequired', false);
    fixture.detectChanges();
    fixture.nativeElement
      .querySelector('form')
      .dispatchEvent(new Event('submit'));
    fixture.componentInstance.send();
    fixture.detectChanges();
    expect(estimator.emailSession).toHaveBeenCalledTimes(1);
    expect(
      fixture.nativeElement.querySelector('button[type=submit]').disabled,
    ).toBeTrue();
    response.next();
    response.complete();
    fixture.detectChanges();
    expect(
      fixture.nativeElement.querySelector('[role=status]').textContent,
    ).toContain('SESSION_EMAIL.SENT');
    expect(fixture.componentInstance.sending()).toBeFalse();
    fixture.destroy();
  });
});
