import { PLATFORM_ID, inject, Injectable, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

export interface ContactRequestDraftContext {
  source: 'shop_catalog' | 'shop_product';
  productName?: string;
  productSlug?: string;
}

export interface ContactRequestDraft {
  email: string;
  message: string;
  acceptLegal: boolean;
  files: File[];
  context: ContactRequestDraftContext | null;
}

interface PersistedContactRequestDraft {
  email: string;
  message: string;
  acceptLegal: boolean;
  context: ContactRequestDraftContext | null;
}

@Injectable({
  providedIn: 'root',
})
export class ContactRequestDraftService {
  private static readonly STORAGE_KEY = 'contact-request-draft';

  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly memoryFiles: File[] = [];
  private readonly draftState = signal<ContactRequestDraft | null>(
    this.readDraft(),
  );

  getDraft(): ContactRequestDraft | null {
    const draft = this.draftState();
    if (!draft) {
      return null;
    }

    return {
      ...draft,
      files: [...this.memoryFiles],
    };
  }

  setDraft(draft: ContactRequestDraft): void {
    this.memoryFiles.splice(0, this.memoryFiles.length, ...draft.files);

    const normalizedDraft: ContactRequestDraft = {
      email: draft.email || '',
      message: draft.message || '',
      acceptLegal: Boolean(draft.acceptLegal),
      context: draft.context ?? null,
      files: [...this.memoryFiles],
    };

    this.draftState.set(normalizedDraft);
    this.persistDraft(normalizedDraft);
  }

  clearDraft(): void {
    this.memoryFiles.splice(0, this.memoryFiles.length);
    this.draftState.set(null);
    if (!this.isBrowser) {
      return;
    }

    window.sessionStorage.removeItem(ContactRequestDraftService.STORAGE_KEY);
  }

  buildSubmittedMessage(
    message: string,
    context?: ContactRequestDraftContext | null,
  ): string {
    const trimmedMessage = (message || '').trim();
    const normalizedContext = context ?? null;

    if (!normalizedContext) {
      return trimmedMessage;
    }

    const header =
      normalizedContext.source === 'shop_product'
        ? '[SHOP PRODUCT QUICK REQUEST]'
        : '[SHOP QUICK REQUEST]';

    const lines = [header];

    if (normalizedContext.productName) {
      lines.push(`Product reference: ${normalizedContext.productName}`);
    }

    if (normalizedContext.productSlug) {
      lines.push(`Product slug: ${normalizedContext.productSlug}`);
    }

    if (trimmedMessage) {
      lines.push('', trimmedMessage);
    }

    return lines.join('\n');
  }

  private readDraft(): ContactRequestDraft | null {
    if (!this.isBrowser) {
      return null;
    }

    const raw = window.sessionStorage.getItem(
      ContactRequestDraftService.STORAGE_KEY,
    );
    if (!raw) {
      return null;
    }

    try {
      const parsed = JSON.parse(raw) as PersistedContactRequestDraft;
      return {
        email: parsed.email || '',
        message: parsed.message || '',
        acceptLegal: Boolean(parsed.acceptLegal),
        context: parsed.context ?? null,
        files: [],
      };
    } catch {
      window.sessionStorage.removeItem(ContactRequestDraftService.STORAGE_KEY);
      return null;
    }
  }

  private persistDraft(draft: ContactRequestDraft): void {
    if (!this.isBrowser) {
      return;
    }

    const payload: PersistedContactRequestDraft = {
      email: draft.email,
      message: draft.message,
      acceptLegal: draft.acceptLegal,
      context: draft.context,
    };

    window.sessionStorage.setItem(
      ContactRequestDraftService.STORAGE_KEY,
      JSON.stringify(payload),
    );
  }
}
