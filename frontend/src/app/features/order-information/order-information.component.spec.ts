import { TestBed, ComponentFixture, fakeAsync, flushMicrotasks, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { OrderInformationComponent } from './order-information.component';
import { OrderInformationService } from './order-information.service';
import { environment } from '../../../environments/environment';
import itTranslations from '../../../assets/i18n/it.json';

describe('OrderInformationComponent', () => {
  let fixture: ComponentFixture<OrderInformationComponent>;
  let http: HttpTestingController;
  let service: OrderInformationService;
  const api = environment.apiUrl + '/api';
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [OrderInformationComponent, TranslateModule.forRoot()], providers: [provideHttpClient(), provideHttpClientTesting()] }).compileComponents();
    const translate = TestBed.inject(TranslateService); translate.setTranslation('it', itTranslations); translate.use('it');
    http = TestBed.inject(HttpTestingController); service = TestBed.inject(OrderInformationService);
    fixture = TestBed.createComponent(OrderInformationComponent);
  });
  afterEach(() => { fixture.destroy(); http.verify(); localStorage.removeItem('order-information:test-order'); });
  function button(label: string): HTMLButtonElement {
    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    const button = buttons.find(button => button.textContent?.trim() === label);
    if (!button) throw new Error('Missing button ' + label);
    return button;
  }
  it('edits notes, previews photos, removes selected files and keeps the collapsed summary', fakeAsync(() => {
    fixture.detectChanges(); button('Aggiungi istruzioni o allegati').click(); fixture.detectChanges(); tick();
    const textarea: HTMLTextAreaElement = fixture.nativeElement.querySelector('textarea');
    textarea.value = 'Stampare in verticale'; textarea.dispatchEvent(new Event('input')); fixture.detectChanges(); tick();
    expect(service.text()).toBe('Stampare in verticale');
    const picker: HTMLInputElement = fixture.nativeElement.querySelector('input[type=file]');
    const transfer = new DataTransfer(); transfer.items.add(new File(['photo'], 'photo.png', { type: 'image/png' }));
    Object.defineProperty(picker, 'files', { configurable: true, value: transfer.files }); picker.dispatchEvent(new Event('change')); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('img')?.getAttribute('alt')).toBe('photo.png');
    button('Rimuovi').click(); fixture.detectChanges(); expect(service.files()).toEqual([]);
    button('Chiudi').click(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('1 nota · 0 allegati');
  }));
  it('keeps instructions and file controls editable while copying a link saves the draft', fakeAsync(() => {
    fixture.detectChanges();
    button('Aggiungi istruzioni o allegati').click(); fixture.detectChanges(); tick();
    service.busy.set(true); fixture.detectChanges();
    expect((fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement).disabled).toBeFalse();
    expect((fixture.nativeElement.querySelector('input[type=file]') as HTMLInputElement).disabled).toBeFalse();
    expect(button('Chiudi').disabled).toBeFalse();
  }));

  it('shows draft instructions in checkout and allows editing without losing them', fakeAsync(() => {
    service.text.set('Conservare la superficie esterna'); fixture.componentRef.setInput('review', true);
    fixture.detectChanges(); tick();
    expect(fixture.nativeElement.textContent).toContain('Conservare la superficie esterna');
    button('Modifica').click(); fixture.detectChanges(); tick(); fixture.detectChanges();
    expect((fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement).value).toBe('Conservare la superficie esterna');
  }));
  it('adds information after purchase and retains the earlier entry in the rendered history', fakeAsync(() => {
    service.rememberOrder('test-order', 'secret'); fixture.componentRef.setInput('orderId', 'test-order'); fixture.detectChanges();
    const first = { id: 'first', text: 'Originale', model: '', modelKey: '', attachments: [], readAt: null, createdAt: '2026-09-11T12:00:00Z' };
    http.expectOne(api + '/orders/test-order/information').flush({ id: 'info', entries: [first] }); flushMicrotasks(); fixture.detectChanges();
    button('Aggiungi informazioni').click(); fixture.detectChanges(); tick();
    const textarea: HTMLTextAreaElement = fixture.nativeElement.querySelector('textarea'); textarea.value = 'Seconda indicazione'; textarea.dispatchEvent(new Event('input')); fixture.detectChanges(); tick();
    expect(fixture.nativeElement.textContent).toContain('se la stampa è già iniziata');
    button('Salva').click();
    const save = http.expectOne(api + '/orders/test-order/information'); expect(save.request.method).toBe('POST');
    save.flush({ id: 'info', entries: [first, { ...first, id: 'second', text: 'Seconda indicazione' }] }); flushMicrotasks(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Originale'); expect(fixture.nativeElement.textContent).toContain('Seconda indicazione');
    expect(fixture.nativeElement.textContent).toContain('Informazioni aggiunte all’ordine');
  }));
  it('acknowledges only the entries currently shown to the administrator', fakeAsync(() => {
    fixture.componentRef.setInput('orderId', 'test-order'); fixture.componentRef.setInput('admin', true); fixture.detectChanges();
    const entry = { id: 'entry', text: 'Da leggere', model: '', modelKey: '', attachments: [], readAt: null, createdAt: '2026-09-11T12:00:00Z' };
    http.expectOne(api + '/admin/orders/test-order/information').flush({ id: 'info', entries: [entry], customerToken: 'secret' }); flushMicrotasks(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nuove informazioni');
    button('Segna come lette').click(); const read = http.expectOne(api + '/admin/orders/test-order/information/read');
    expect(read.request.body).toEqual({ entries: ['entry'] }); read.flush({ id: 'info', entries: [{ ...entry, readAt: '2026-09-11T13:00:00Z' }] }); flushMicrotasks(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Nuove informazioni');
    expect(fixture.componentInstance.customerLink()).toContain('#informationKey=secret');
  }));
  it('keeps long filenames within the card at narrow widths', fakeAsync(() => {
    fixture.nativeElement.style.display = 'block'; fixture.nativeElement.style.width = '320px';
    service.files.set([new File(['pdf'], 'a'.repeat(180) + '.pdf', { type: 'application/pdf' })]);
    fixture.detectChanges(); button('Modifica').click(); fixture.detectChanges(); tick();
    const attachments: HTMLElement = fixture.nativeElement.querySelector('.attachments');
    expect(attachments.scrollWidth).toBeLessThanOrEqual(attachments.clientWidth + 1);
    expect((fixture.nativeElement as HTMLElement).scrollWidth).toBeLessThanOrEqual(321);
  }));
});
