import { CalculatorOptionsService } from '../../calculator/services/calculator-options.service';
import { provideZonelessChangeDetection, Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { of, Subject } from 'rxjs';
import { AdminOperationsService } from '../services/admin-operations.service';
import { AdminMediaService } from '../services/admin-media.service';
import { AdminTranslationService } from '../services/admin-translation.service';
import { AdminContactRequestsComponent } from './admin-contact-requests.component';
import { AdminHomeMediaComponent } from './admin-home-media.component';
import { AdminSessionsComponent } from './admin-sessions.component';

// Delayed responses must refresh an OnPush view without clicks or detectChanges().
describe('Admin asynchronous loading', () => {
  function setup<T>(component: Type<T>, providers: object[]) {
    TestBed.configureTestingModule({
      imports: [component],
      providers: [
        provideZonelessChangeDetection(),
        provideTranslateService(),
        ...providers,
      ],
    });
    const fixture = TestBed.createComponent(component);
    fixture.autoDetectChanges();
    return fixture;
  }

  it('renders media after the HTTP response and clears loading', async () => {
    const assets = new Subject<never[]>();
    const fixture = setup(AdminHomeMediaComponent, [
      {
        provide: CalculatorOptionsService,
        useValue: { getOptions: () => of({ materials: [] }) },
      },
      { provide: AdminMediaService, useValue: { listAssets: () => assets } },
      { provide: AdminTranslationService, useValue: {} },
    ]);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Caricamento media');
    assets.next([]);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).not.toContain(
      'Caricamento media',
    );
    expect(fixture.nativeElement.textContent).toContain('Seleziona area');
  });

  it('renders contact errors after an asynchronous failure', async () => {
    const requests = new Subject<never[]>();
    const fixture = setup(AdminContactRequestsComponent, [
      {
        provide: AdminOperationsService,
        useValue: { getContactRequests: () => requests },
      },
    ]);
    await fixture.whenStable();
    requests.error(new Error('unavailable'));
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain(
      'Impossibile caricare le richieste di contatto.',
    );
    expect(fixture.componentInstance.loading).toBeFalse();
  });

  it('renders sessions after a delayed response', async () => {
    const sessions = new Subject<never[]>();
    const fixture = setup(AdminSessionsComponent, [
      {
        provide: AdminOperationsService,
        useValue: {
          getSessions: () => sessions,
          getSessionStatistics: () => of(null),
        },
      },
    ]);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
    sessions.next([]);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('table')).not.toBeNull();
    expect(fixture.componentInstance.loading).toBeFalse();
  });
});
