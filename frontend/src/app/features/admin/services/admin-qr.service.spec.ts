import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { AdminQrService } from './admin-qr.service';

describe('AdminQrService', () => {
  let service: AdminQrService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [AdminQrService],
    });

    service = TestBed.inject(AdminQrService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('loads stats with credentials and date query params', () => {
    service
      .getQrLinkStats('qr-1', '2026-04-01', '2026-04-20')
      .subscribe((response) => {
        expect(response.rawScans).toBe(12);
      });

    const request = httpMock.expectOne(
      'http://localhost:8000/api/admin/qr-links/qr-1/stats?from=2026-04-01&to=2026-04-20',
    );
    expect(request.request.method).toBe('GET');
    expect(request.request.withCredentials).toBeTrue();

    request.flush({
      qrLinkId: 'qr-1',
      fromDate: '2026-04-01',
      toDate: '2026-04-20',
      rawScans: 12,
      uniqueVisitors: 10,
      excludedBotScans: 1,
      daily: [],
      languages: [],
      recentScans: [],
    });
  });

  it('downloads SVG as blob with credentials', () => {
    service.downloadQrSvg('qr-1').subscribe();

    const request = httpMock.expectOne(
      'http://localhost:8000/api/admin/qr-links/qr-1/svg',
    );
    expect(request.request.method).toBe('GET');
    expect(request.request.withCredentials).toBeTrue();
    expect(request.request.responseType).toBe('blob');

    request.flush(new Blob(['<svg/>'], { type: 'image/svg+xml' }));
  });

  it('loads overview stats with credentials', () => {
    service.getOverviewStats('2026-04-01', '2026-04-20').subscribe((response) => {
      expect(response.totalQrLinks).toBe(3);
    });

    const request = httpMock.expectOne(
      'http://localhost:8000/api/admin/qr-links/overview?from=2026-04-01&to=2026-04-20',
    );
    expect(request.request.method).toBe('GET');
    expect(request.request.withCredentials).toBeTrue();

    request.flush({
      fromDate: '2026-04-01',
      toDate: '2026-04-20',
      totalQrLinks: 3,
      activeQrLinks: 2,
      rawScans: 20,
      uniqueVisitors: 16,
      daily: [],
      qrLinks: [],
    });
  });
});
