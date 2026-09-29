import { PLATFORM_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import {
  AdminCadInvoicesComponent,
  hoursMinutesToSeconds,
  parseDecimalInput,
  secondsToHoursMinutes,
} from './admin-cad-invoices.component';
import {
  AdminCadInvoice,
  AdminOperationsService,
  AdminQuoteItemStats,
  AdminQuoteItemsResponse,
  QuoteSessionAttachment,
} from '../services/admin-operations.service';
import { AdminOrdersService } from '../services/admin-orders.service';

const SESSION_ID = '11111111-1111-1111-1111-111111111111';

describe('parseDecimalInput', () => {
  it('accepts both decimal points and decimal commas', () => {
    expect(parseDecimalInput('1.5')).toBe(1.5);
    expect(parseDecimalInput('1,5')).toBe(1.5);
  });

  it('rejects malformed decimal values', () => {
    expect(parseDecimalInput('1.2.3')).toBeNaN();
    expect(parseDecimalInput('')).toBeNaN();
  });
});

describe('secondsToHoursMinutes', () => {
  it('splits seconds into hours and minutes', () => {
    expect(secondsToHoursMinutes(3661)).toEqual({ hours: 1, minutes: 1 });
    expect(secondsToHoursMinutes(3600)).toEqual({ hours: 1, minutes: 0 });
    expect(secondsToHoursMinutes(59)).toEqual({ hours: 0, minutes: 0 });
    expect(secondsToHoursMinutes(0)).toEqual({ hours: 0, minutes: 0 });
  });
});

describe('hoursMinutesToSeconds', () => {
  it('converts hours and minutes to seconds', () => {
    expect(hoursMinutesToSeconds('1', '1')).toBe(3660);
    expect(hoursMinutesToSeconds(2, 30)).toBe(9000);
    expect(hoursMinutesToSeconds('0', '0')).toBe(0);
  });

  it('rejects invalid values', () => {
    expect(hoursMinutesToSeconds('1', '60')).toBeNaN();
    expect(hoursMinutesToSeconds('-1', '0')).toBeNaN();
    expect(hoursMinutesToSeconds('1', '')).toBeNaN();
    expect(hoursMinutesToSeconds('1,5', '0')).toBeNaN();
  });
});

describe('AdminCadInvoicesComponent session files', () => {
  it('loads print files when a complete session id is entered', () => {
    const { component, adminOperationsService } = createComponent();
    adminOperationsService.getAdminQuoteItems.and.returnValue(of(response()));

    component.form.sessionId = SESSION_ID;
    component.onSessionIdChange();

    expect(adminOperationsService.getAdminQuoteItems).toHaveBeenCalledWith(
      SESSION_ID,
    );
    expect(component.managedSessionStatus).toBe('CAD_ACTIVE');
    expect(component.managedItemsTotalChf).toBe(20);
    expect(component.managedRows.length).toBe(1);
    expect(component.managedRows[0].hours).toBe('1');
    expect(component.managedRows[0].minutes).toBe('1');
    expect(component.managedRows[0].grams).toBe('100');
  });

  it('clears the session files when the session id is not complete', () => {
    const { component, adminOperationsService } = createComponent();
    adminOperationsService.getAdminQuoteItems.and.returnValue(of(response()));
    component.form.sessionId = SESSION_ID;
    component.onSessionIdChange();

    component.form.sessionId = 'not-a-uuid';
    component.onSessionIdChange();

    expect(component.managedRows.length).toBe(0);
    expect(component.managedSessionId).toBeNull();
    expect(adminOperationsService.getAdminQuoteItems).toHaveBeenCalledTimes(1);
  });

  it('previews prices with persist=false and shows proposed totals', () => {
    const { component, adminOperationsService } = createComponent();
    adminOperationsService.getAdminQuoteItems.and.returnValue(of(response()));
    loadSession(component);

    adminOperationsService.updateAdminQuoteItemStats.and.returnValue(
      of(
        response({
          items: [item({ newUnitPriceChf: 15 })],
          grandTotalChf: 35,
        }),
      ),
    );

    component.previewManagedChanges();

    expect(
      adminOperationsService.updateAdminQuoteItemStats,
    ).toHaveBeenCalledWith(SESSION_ID, {
      persist: false,
      items: [{ itemId: 'item-1', printTimeSeconds: 3660, materialGrams: 100 }],
    });
    expect(component.managedPreviewActive).toBeTrue();
    expect(component.managedPreviewTotalChf).toBe(35);
    expect(component.managedRows[0].item.newUnitPriceChf).toBe(15);
  });

  it('invalidates a preview when a draft changes', () => {
    const { component, adminOperationsService } = createComponent();
    adminOperationsService.getAdminQuoteItems.and.returnValue(of(response()));
    loadSession(component);
    adminOperationsService.updateAdminQuoteItemStats.and.returnValue(
      of(response({ items: [item({ newUnitPriceChf: 15 })], grandTotalChf: 35 })),
    );
    component.previewManagedChanges();
    expect(component.managedPreviewActive).toBeTrue();
    const callsBeforeEdit =
      adminOperationsService.updateAdminQuoteItemStats.calls.count();

    component.onManagedDraftChange();

    expect(component.managedPreviewActive).toBeFalse();
    expect(component.managedPreviewTotalChf).toBeNull();
    expect(component.managedRows[0].item.newUnitPriceChf).toBeNull();

    component.saveManagedChanges();
    expect(adminOperationsService.updateAdminQuoteItemStats.calls.count()).toBe(
      callsBeforeEdit,
    );
  });

  it('blocks the preview when draft values are invalid', () => {
    const { component, adminOperationsService } = createComponent();
    adminOperationsService.getAdminQuoteItems.and.returnValue(of(response()));
    loadSession(component);
    component.managedRows[0].grams = '';

    component.previewManagedChanges();

    expect(
      adminOperationsService.updateAdminQuoteItemStats,
    ).not.toHaveBeenCalled();
    expect(component.managedError).toBe('CAD_ITEM_PRICING.INVALID_VALUES');
  });

  it('persists changes only after a preview and reloads the invoice list', () => {
    const { component, adminOperationsService } = createComponent();
    adminOperationsService.getAdminQuoteItems.and.returnValue(of(response()));
    loadSession(component);
    adminOperationsService.updateAdminQuoteItemStats.and.returnValue(
      of(
        response({
          items: [item({ newUnitPriceChf: 15, unitPriceChf: 15 })],
          grandTotalChf: 35,
        }),
      ),
    );
    component.previewManagedChanges();
    adminOperationsService.listCadInvoices.calls.reset();

    component.saveManagedChanges();

    expect(
      adminOperationsService.updateAdminQuoteItemStats,
    ).toHaveBeenCalledWith(SESSION_ID, {
      persist: true,
      items: [{ itemId: 'item-1', printTimeSeconds: 3660, materialGrams: 100 }],
    });
    expect(component.successMessage).toBe('CAD_ITEM_PRICING.SAVED');
    expect(component.managedPreviewActive).toBeFalse();
    expect(adminOperationsService.listCadInvoices).toHaveBeenCalled();
  });

  it('lists attachments and deletes them', () => {
    const { component, adminOperationsService } = createComponent();
    const attachment = imageAttachment();
    adminOperationsService.getAdminQuoteItems.and.returnValue(of(response()));
    adminOperationsService.listQuoteSessionAttachments.and.returnValue(
      of([attachment]),
    );
    adminOperationsService.deleteQuoteSessionAttachment.and.returnValue(
      of(void 0),
    );
    loadSession(component);

    expect(adminOperationsService.listQuoteSessionAttachments).toHaveBeenCalledWith(
      SESSION_ID,
    );
    expect(component.sessionAttachments.length).toBe(1);

    component.deleteAttachment(attachment);

    expect(
      adminOperationsService.deleteQuoteSessionAttachment,
    ).toHaveBeenCalledWith(SESSION_ID, 'attachment-1');
    expect(component.sessionAttachments.length).toBe(0);
  });

  it('queues attachments without a session and uploads them after creation', () => {
    const { component, adminOperationsService } = createComponent();
    adminOperationsService.getAdminQuoteItems.and.returnValue(of(response()));
    adminOperationsService.createCadInvoice.and.returnValue(
      of({ sessionId: SESSION_ID } as AdminCadInvoice),
    );
    adminOperationsService.uploadQuoteSessionAttachments.and.returnValue(
      of([imageAttachment()]),
    );
    component.serviceLines = [
      {
        description: 'Servizio',
        billingType: 'FIXED',
        quantity: '1',
        unitPriceChf: '10',
      },
    ];

    component.onAttachmentFilesSelected({
      target: { files: [new File(['x'], 'photo.png', { type: 'image/png' })] },
    } as unknown as Event);

    expect(component.pendingAttachmentFiles.length).toBe(1);
    expect(
      adminOperationsService.uploadQuoteSessionAttachments,
    ).not.toHaveBeenCalled();

    component.createCadInvoice();

    expect(
      adminOperationsService.uploadQuoteSessionAttachments,
    ).toHaveBeenCalledWith(SESSION_ID, jasmine.any(Array));
    expect(component.pendingAttachmentFiles.length).toBe(0);
  });

  it('uploads selected attachments and reloads the list', () => {
    const { component, adminOperationsService } = createComponent();
    adminOperationsService.getAdminQuoteItems.and.returnValue(of(response()));
    adminOperationsService.uploadQuoteSessionAttachments.and.returnValue(
      of([imageAttachment()]),
    );
    loadSession(component);

    component.onAttachmentFilesSelected({
      target: { files: [new File(['x'], 'photo.png', { type: 'image/png' })] },
    } as unknown as Event);

    expect(
      adminOperationsService.uploadQuoteSessionAttachments,
    ).toHaveBeenCalledWith(SESSION_ID, [
      jasmine.any(File) as unknown as File,
    ]);
    expect(adminOperationsService.listQuoteSessionAttachments).toHaveBeenCalledTimes(
      2,
    );
  });
});

function loadSession(component: AdminCadInvoicesComponent): void {
  component.form.sessionId = SESSION_ID;
  component.onSessionIdChange();
}

function createComponent() {
  TestBed.resetTestingModule();

  const adminOperationsService = jasmine.createSpyObj<AdminOperationsService>(
    'AdminOperationsService',
    [
      'listCadInvoices',
      'createCadInvoice',
      'getAdminQuoteItems',
      'updateAdminQuoteItemStats',
      'listQuoteSessionAttachments',
      'uploadQuoteSessionAttachments',
      'deleteQuoteSessionAttachment',
      'getQuoteSessionAttachmentPreview',
    ],
  );
  adminOperationsService.listCadInvoices.and.returnValue(of([]));
  adminOperationsService.listQuoteSessionAttachments.and.returnValue(of([]));
  adminOperationsService.getQuoteSessionAttachmentPreview.and.returnValue(
    of(new Blob()),
  );
  const adminOrdersService = jasmine.createSpyObj<AdminOrdersService>(
    'AdminOrdersService',
    ['downloadOrderInvoice'],
  );
  const translateService = jasmine.createSpyObj<TranslateService>(
    'TranslateService',
    ['instant'],
  );
  translateService.instant.and.callFake((key: unknown) => String(key));

  TestBed.configureTestingModule({
    providers: [
      { provide: AdminOperationsService, useValue: adminOperationsService },
      { provide: AdminOrdersService, useValue: adminOrdersService },
      { provide: TranslateService, useValue: translateService },
      { provide: PLATFORM_ID, useValue: 'browser' },
    ],
  });

  const component = TestBed.runInInjectionContext(
    () => new AdminCadInvoicesComponent(),
  );

  return { component, adminOperationsService, adminOrdersService };
}

function item(
  overrides: Partial<AdminQuoteItemStats> = {},
): AdminQuoteItemStats {
  return {
    id: 'item-1',
    displayName: 'fixture.stl',
    quantity: 2,
    printTimeSeconds: 3660,
    materialGrams: 100,
    unitPriceChf: 10,
    newUnitPriceChf: null,
    status: 'READY',
    lineItemType: 'PRINT_FILE',
    editable: true,
    ...overrides,
  };
}

function response(
  overrides: Partial<AdminQuoteItemsResponse> = {},
): AdminQuoteItemsResponse {
  return {
    sessionId: SESSION_ID,
    sessionStatus: 'CAD_ACTIVE',
    items: [item()],
    printItemsTotalChf: 20,
    globalMachineCostChf: 0,
    cadTotalChf: 0,
    itemsTotalChf: 20,
    setupCostChf: 0,
    shippingCostChf: 0,
    grandTotalChf: 20,
    ...overrides,
  };
}

function imageAttachment(): QuoteSessionAttachment {
  return {
    id: 'attachment-1',
    originalFilename: 'photo.png',
    mimeType: 'image/png',
    fileSizeBytes: 10,
    image: true,
    createdAt: '2026-09-29T12:00:00Z',
  };
}
