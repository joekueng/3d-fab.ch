import { TestBed, fakeAsync, flushMicrotasks } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { OrderInformationService } from './order-information.service';
import { environment } from '../../../environments/environment';

describe('OrderInformationService', () => {
  let service: OrderInformationService;
  let http: HttpTestingController;
  const api = environment.apiUrl + '/api';
  const attachment = {
    id: 'file',
    name: 'photo.png',
    size: 20,
    mime: 'image/png',
  };
  const stored = {
    id: 'draft',
    entries: [
      {
        id: 'entry',
        text: 'Vertical',
        model: 'part.stl',
        modelKey: 'part-key',
        createdAt: '2026-09-11T10:00:00Z',
        readAt: null,
        attachments: [attachment],
      },
    ],
  };
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(OrderInformationService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => {
    http.verify();
    localStorage.removeItem('information-draft:draft');
    localStorage.removeItem('order-information:order');
  });

  it('keeps the same draft credential while saving and recalculating', fakeAsync(() => {
    service.text.set('Vertical');
    service.model.set('part-key');
    service.modelName.set('part.stl');
    service.files.set([new File(['png'], 'photo.png', { type: 'image/png' })]);
    void service.saveDraft();
    http
      .expectOne(api + '/information-drafts')
      .flush({ id: 'draft', token: 'secret' });
    flushMicrotasks();
    const upload = http.expectOne(api + '/information-drafts/draft');
    expect(upload.request.headers.get('X-Information-Token')).toBe('secret');
    expect((upload.request.body as FormData).getAll('files').length).toBe(1);
    upload.flush(stored);
    flushMicrotasks();
    expect(service.files()).toEqual([]);
    expect(service.attachments()).toEqual([attachment]);
    expect(service.model()).toBe('part-key');
    void service.saveDraft();
    const save = http.expectOne(api + '/information-drafts/draft');
    expect(save.request.method).toBe('PUT');
    expect((save.request.body as FormData).getAll('files')).toEqual([]);
    save.flush(stored);
    flushMicrotasks();
    expect(service.draftCredential()).toEqual({ id: 'draft', token: 'secret' });
  }));

  it('preserves edits and newly selected files while a background save finishes', fakeAsync(() => {
    const uploaded = new File(['png'], 'photo.png', { type: 'image/png' });
    const nextFile = new File(['pdf'], 'next.pdf', { type: 'application/pdf' });
    service.text.set('Vertical');
    service.files.set([uploaded]);
    void service.saveDraft();
    http
      .expectOne(api + '/information-drafts')
      .flush({ id: 'draft', token: 'secret' });
    flushMicrotasks();
    const upload = http.expectOne(api + '/information-drafts/draft');
    service.text.set('Edited while copying');
    service.files.set([uploaded, nextFile]);
    upload.flush(stored);
    flushMicrotasks();
    expect(service.text()).toBe('Edited while copying');
    expect(service.files()).toEqual([nextFile]);
    expect(service.attachments()).toEqual([attachment]);
  }));

  it('restores saved information when opening a quote in another page instance', fakeAsync(() => {
    localStorage.setItem('information-draft:draft', 'secret');
    void service.useDraft('draft');
    http.expectOne(api + '/information-drafts/draft').flush(stored);
    flushMicrotasks();
    expect(service.text()).toBe('Vertical');
    expect(service.attachments()).toEqual([attachment]);
    expect(service.model()).toBe('part-key');
  }));

  it('queues an explicit save of newer edits behind the copy operation', fakeAsync(() => {
    service.text.set('First version');
    void service.saveDraft();
    http
      .expectOne(api + '/information-drafts')
      .flush({ id: 'draft', token: 'secret' });
    flushMicrotasks();
    const first = http.expectOne(api + '/information-drafts/draft');
    service.text.set('New version');
    let secondSaved = false;
    void service.saveDraft().then(() => (secondSaved = true));
    first.flush({
      id: 'draft',
      entries: [
        { ...stored.entries[0], text: 'First version', attachments: [] },
      ],
    });
    flushMicrotasks();
    expect(service.text()).toBe('New version');
    expect(secondSaved).toBeFalse();
    const second = http.expectOne(api + '/information-drafts/draft');
    second.flush({
      id: 'draft',
      entries: [{ ...stored.entries[0], text: 'New version', attachments: [] }],
    });
    flushMicrotasks();
    expect(service.text()).toBe('New version');
    expect(secondSaved).toBeTrue();
  }));

  it('retains input after failed upload and never silently replaces an inaccessible draft', fakeAsync(() => {
    service.text.set('Keep me');
    const file = new File(['pdf'], 'note.pdf', { type: 'application/pdf' });
    service.files.set([file]);
    void service.saveDraft().catch(() => undefined);
    http
      .expectOne(api + '/information-drafts')
      .flush({ id: 'draft', token: 'secret' });
    flushMicrotasks();
    http
      .expectOne(api + '/information-drafts/draft')
      .flush({}, { status: 400, statusText: 'Bad request' });
    flushMicrotasks();
    expect(service.text()).toBe('Keep me');
    expect(service.files()).toEqual([file]);
    expect(service.error()).toBeTrue();
    void service.useDraft('foreign-draft');
    flushMicrotasks();
    void service.saveDraft().catch(() => undefined);
    flushMicrotasks();
    http.expectNone(api + '/information-drafts');
    expect(service.error()).toBeTrue();
  }));

  it('sends separate order credentials and admin session cookies to their respective endpoints', fakeAsync(() => {
    service.rememberOrder('order', 'order-secret');
    void service.getOrder('order', false);
    const customer = http.expectOne(api + '/orders/order/information');
    expect(customer.request.headers.get('X-Information-Token')).toBe(
      'order-secret',
    );
    customer.flush({ id: 'order', entries: [] });
    void service.markRead('order', ['known-entry']);
    const admin = http.expectOne(api + '/admin/orders/order/information/read');
    expect(admin.request.withCredentials).toBeTrue();
    expect(admin.request.body).toEqual({ entries: ['known-entry'] });
    expect(admin.request.headers.has('X-Information-Token')).toBeFalse();
    admin.flush({ id: 'order', entries: [] });
    flushMicrotasks();
  }));
});
