import { TestBed, fakeAsync, flushMicrotasks } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { QuoteEstimatorService } from './quote-estimator.service';
import { OrderInformationService } from '../../order-information/order-information.service';
import { environment } from '../../../../environments/environment';

describe('Session email and cross-device restore', () => {
  let estimator: QuoteEstimatorService;
  let information: OrderInformationService;
  let http: HttpTestingController;
  let originalUrl: string;
  const api = environment.apiUrl + '/api';
  const saved = {
    id: 'email-draft',
    entries: [
      {
        id: 'entry',
        text: 'Print upright',
        model: '',
        modelKey: '',
        createdAt: '',
        readAt: null,
        attachments: [
          {
            id: 'file',
            name: 'drawing.pdf',
            mime: 'application/pdf',
            size: 12,
          },
        ],
      },
    ],
  };

  beforeEach(() => {
    originalUrl = window.location.href;
    localStorage.removeItem('information-draft:email-draft');
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    estimator = TestBed.inject(QuoteEstimatorService);
    information = TestBed.inject(OrderInformationService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => {
    http.verify();
    window.history.replaceState(window.history.state, '', originalUrl);
    localStorage.removeItem('information-draft:email-draft');
  });

  it('restores notes and server attachments using only the emailed URL', fakeAsync(() => {
    window.history.replaceState(null, '', '?session=email-session');
    let restored = false;
    estimator
      .getQuoteSession('email-session')
      .subscribe(() => (restored = true));
    const access = http.expectOne(api + '/quote-sessions/email-session/resume');
    expect(access.request.body).toEqual({});
    access.flush({ id: 'email-draft', token: 'link-secret' });
    http.expectOne(api + '/quote-sessions/email-session').flush({
      session: { informationDraftId: 'email-draft' },
      items: [],
    });
    const draft = http.expectOne(api + '/information-drafts/email-draft');
    expect(draft.request.headers.get('X-Information-Token')).toBe(
      'link-secret',
    );
    draft.flush(saved);
    flushMicrotasks();
    expect(restored).toBeTrue();
    expect(information.text()).toBe('Print upright');
    expect(information.attachments()[0].name).toBe('drawing.pdf');
    void information.blob(information.attachments()[0], null, false);
    const download = http.expectOne(
      api + '/information-drafts/email-draft/files/file',
    );
    expect(download.request.headers.get('X-Information-Token')).toBe(
      'link-secret',
    );
    download.flush(new Blob(['%PDF-drawing']));
    flushMicrotasks();
  }));

  it('saves and scans pending files before requesting the email', fakeAsync(() => {
    information.text.set('Print upright');
    information.files.set([
      new File(['%PDF-drawing'], 'drawing.pdf', { type: 'application/pdf' }),
    ]);
    let sent = false;
    estimator
      .emailSession('email-session', 'customer@example.test', 'it', 'easy')
      .subscribe(() => (sent = true));
    http
      .expectOne(api + '/information-drafts')
      .flush({ id: 'email-draft', token: 'link-secret' });
    flushMicrotasks();
    const upload = http.expectOne(api + '/information-drafts/email-draft');
    expect((upload.request.body as FormData).getAll('files').length).toBe(1);
    http.expectNone(api + '/quote-sessions/email-session/email');
    upload.flush(saved);
    flushMicrotasks();
    const email = http.expectOne(api + '/quote-sessions/email-session/email');
    expect(email.request.body.information).toEqual({
      id: 'email-draft',
      token: 'link-secret',
    });
    expect(sent).toBeFalse();
    email.flush(null);
    flushMicrotasks();
    expect(sent).toBeTrue();
    expect(information.files().length).toBe(0);
    expect(information.attachments().length).toBe(1);
  }));

  it('does not email when the scan fails and retains pending work', fakeAsync(() => {
    information.text.set('Keep this');
    information.files.set([
      new File(['%PDF-drawing'], 'drawing.pdf', { type: 'application/pdf' }),
    ]);
    let failed = false;
    estimator
      .emailSession('email-session', 'customer@example.test', 'it', 'easy')
      .subscribe({ error: () => (failed = true) });
    http
      .expectOne(api + '/information-drafts')
      .flush({ id: 'email-draft', token: 'link-secret' });
    flushMicrotasks();
    http
      .expectOne(api + '/information-drafts/email-draft')
      .flush({}, { status: 503, statusText: 'Unavailable' });
    flushMicrotasks();
    http.expectNone(api + '/quote-sessions/email-session/email');
    expect(failed).toBeTrue();
    expect(information.text()).toBe('Keep this');
    expect(information.files().length).toBe(1);
  }));

  it('does not load session data when the emailed link has expired', () => {
    window.history.replaceState(null, '', '?session=email-session');
    let failed = false;
    estimator
      .getQuoteSession('email-session')
      .subscribe({ error: () => (failed = true) });
    http
      .expectOne(api + '/quote-sessions/email-session/resume')
      .flush({}, { status: 410, statusText: 'Gone' });
    http.expectNone(api + '/quote-sessions/email-session');
    expect(failed).toBeTrue();
  });

  it('links newly saved information to a legacy session before publishing its complete URL', fakeAsync(() => {
    window.history.replaceState(null, '', '?session=email-session');
    void information.useDraft(null, '', 'email-session');
    information.text.set('Print upright');
    information.files.set([
      new File(['%PDF-drawing'], 'drawing.pdf', { type: 'application/pdf' }),
    ]);
    void information.saveDraft();
    http
      .expectOne(api + '/information-drafts')
      .flush({ id: 'email-draft', token: 'link-secret' });
    flushMicrotasks();
    http.expectOne(api + '/information-drafts/email-draft').flush(saved);
    flushMicrotasks();
    expect(window.location.hash).toBe('');
    const association = http.expectOne(
      api + '/quote-sessions/email-session/information',
    );
    expect(association.request.body).toEqual({
      id: 'email-draft',
      token: 'link-secret',
    });
    association.flush(null);
    flushMicrotasks();
    expect(window.location.hash).toBe('');
    expect(information.attachments().length).toBe(1);
  }));
});
