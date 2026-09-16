import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface InformationFile { id: string; name: string; mime: string; size: number; }
export interface InformationEntry { id: string; text: string; model: string; modelKey: string; createdAt: string; readAt: string | null; attachments: InformationFile[]; }
export interface Information { id: string; entries: InformationEntry[]; customerToken?: string; }
export interface InformationCredential { id: string; token: string; }
export interface InformationModel { label: string; value: string; }

@Injectable({ providedIn: 'root' })
export class OrderInformationService {
  // Optional keeps the presentational card constructible in isolated admin layout tests;
  // routed application providers always register HttpClient.
  private readonly http = inject(HttpClient, { optional: true })!;
  private readonly api = environment.apiUrl + '/api';
  readonly text = signal('');
  readonly model = signal('');
  readonly modelName = signal('');
  readonly files = signal<File[]>([]);
  readonly attachments = signal<InformationFile[]>([]);
  readonly busy = signal(false);
  readonly error = signal(false);
  private credential: InformationCredential | null = null;
  private inaccessible = false;
  private quoteId: string | null = null;
  private generation = 0;
  private orderTokens = new Map<string, string>();
  private pending: Promise<InformationCredential> | null = null;
  private loading: Promise<void> | null = null;

  private read(key: string): string | null {
    try { return typeof localStorage === 'undefined' ? null : localStorage.getItem(key); } catch { return null; }
  }
  private write(key: string, value: string): void {
    try { if (typeof localStorage !== 'undefined') localStorage.setItem(key, value); } catch { /* Current tab keeps its credential. */ }
  }
  rememberOrder(id: string, token: string): void { this.orderTokens.set(id, token); this.write('order-information:' + id, token); }
  orderToken(id: string): string { return this.orderTokens.get(id) || this.read('order-information:' + id) || ''; }
  draftCredential(): InformationCredential | null { return this.credential; }
  resetDraft(): void {
    this.generation++; this.pending = null; this.busy.set(false);
    this.credential = null; this.quoteId = null; this.inaccessible = false;
    this.text.set(''); this.model.set(''); this.modelName.set(''); this.files.set([]); this.attachments.set([]); this.error.set(false);
  }
  async useDraft(id: string | null | undefined, legacyNotes = '', quoteId?: string, serverToken?: string): Promise<void> {
    if (quoteId && quoteId !== this.quoteId && !id) this.resetDraft();
    if (quoteId) this.quoteId = quoteId;
    if (id && this.credential?.id === id && !this.inaccessible) { if (this.loading) await this.loading; this.publishLink(); return; }
    if (!id) {
      if (!this.credential && legacyNotes) this.text.set(legacyNotes);
      return;
    }
    const token = serverToken || this.read('information-draft:' + id);
    this.resetDraft();
    this.quoteId = quoteId || null;
    this.credential = token ? { id, token } : null;
    this.inaccessible = !token;
    if (!token) { this.text.set(''); this.model.set(''); this.files.set([]); this.attachments.set([]); this.error.set(true); return; }
    const generation = this.generation;
    const loading = (async () => {
      this.busy.set(true);
      try {
        const value = await firstValueFrom(this.http.get<Information>(`${this.api}/information-drafts/${id}`, { headers: this.headers(token) }));
        if (generation === this.generation) { this.applyDraft(value); this.write('information-draft:' + id, token); this.publishLink(); this.error.set(false); }
      } catch { if (generation === this.generation) { this.inaccessible = true; this.error.set(true); } }
      finally { if (generation === this.generation) this.busy.set(false); }
    })();
    this.loading = loading;
    await loading;
    if (this.loading === loading) this.loading = null;
  }
  private publishLink(): void {
    if (!this.quoteId || typeof window === 'undefined') return;
    const url = new URL(window.location.href);
    if (url.searchParams.get('session') !== this.quoteId) return;
    const fragment = new URLSearchParams(url.hash.slice(1));
    if (!fragment.has('informationKey')) return;
    fragment.delete('informationKey');
    url.hash = fragment.toString();
    window.history.replaceState(window.history.state, '', url);
  }

  private applyDraft(value: Information): void {
    this.text.set(value.entries[0]?.text || ''); this.model.set(value.entries[0]?.modelKey || ''); this.modelName.set(value.entries[0]?.model || '');
    this.attachments.set(value.entries.flatMap(e => e.attachments)); this.files.set([]);
  }
  async saveDraft(): Promise<InformationCredential> {
    if (this.pending) {
      await this.pending;
      // A second explicit save must include edits made while the first save was in flight.
      return this.saveDraft();
    }
    const pending = this.persistDraft();
    this.pending = pending;
    try { return await pending; } finally { if (this.pending === pending) this.pending = null; }
  }
  private async persistDraft(): Promise<InformationCredential> {
    if (this.loading) await this.loading;
    const generation = this.generation;
    const snapshot = {
      text: this.text(), model: this.model(), modelName: this.modelName(),
      files: [...this.files()], attachments: [...this.attachments()],
    };
    const body = this.form(snapshot.text, snapshot.model, snapshot.modelName, snapshot.files, snapshot.attachments.map(a => a.id));
    this.busy.set(true);
    try {
      if (this.inaccessible) throw new Error('Information access required');
      let credential = this.credential;
      if (!credential) {
        credential = await firstValueFrom(this.http.post<InformationCredential>(`${this.api}/information-drafts`, {}));
        this.write('information-draft:' + credential.id, credential.token);
        if (generation !== this.generation) throw new Error('Draft changed');
        this.credential = credential;
      }
      const value = await firstValueFrom(this.http.put<Information>(`${this.api}/information-drafts/${credential.id}`, body, { headers: this.headers(credential.token) }));
      if (generation !== this.generation) throw new Error('Draft changed');
      // Keep the scanned upload result even if linking fails, so retrying does not upload duplicates.
      // Saving a link runs in the background: never overwrite edits made during the request.
      const entry = value.entries[0];
      if (this.text() === snapshot.text) this.text.set(entry?.text || '');
      if (this.model() === snapshot.model) this.model.set(entry?.modelKey || '');
      if (this.modelName() === snapshot.modelName) this.modelName.set(entry?.model || '');
      const existingIds = new Set(snapshot.attachments.map(file => file.id));
      const remainingIds = new Set(this.attachments().map(file => file.id));
      const pendingFiles = new Set(this.files());
      let uploadedIndex = 0;
      this.attachments.set(value.entries.flatMap(entry => entry.attachments).filter(file =>
        existingIds.has(file.id) ? remainingIds.has(file.id) : pendingFiles.has(snapshot.files[uploadedIndex++]),
      ));
      this.files.update(files => files.filter(file => !snapshot.files.includes(file)));
      if (this.quoteId) {
        await firstValueFrom(this.http.put<void>(`${this.api}/quote-sessions/${this.quoteId}/information`, credential));
        if (generation !== this.generation) throw new Error('Draft changed');
      }
      this.publishLink(); this.error.set(false);
      return credential;
    } catch (error) { if (generation === this.generation) this.error.set(true); throw error; }
    finally { if (generation === this.generation) this.busy.set(false); }
  }
  getOrder(id: string, admin: boolean): Promise<Information> {
    return firstValueFrom(this.http.get<Information>(this.orderUrl(id, admin), this.options(id, admin)));
  }
  append(id: string, text: string, model: string, modelName: string, files: File[]): Promise<Information> {
    return firstValueFrom(this.http.post<Information>(this.orderUrl(id, false), this.form(text, model, modelName, files, []), this.options(id, false)));
  }
  markRead(id: string, entries: string[]): Promise<Information> {
    return firstValueFrom(this.http.post<Information>(this.orderUrl(id, true) + '/read', { entries }, { withCredentials: true }));
  }
  unread(): Promise<string[]> {
    return firstValueFrom(this.http.get<string[]>(`${this.api}/admin/orders/information-unread`, { withCredentials: true }));
  }
  async blob(file: InformationFile, orderId: string | null, admin: boolean): Promise<Blob> {
    const base = orderId ? this.orderUrl(orderId, admin) : `${this.api}/information-drafts/${this.credential?.id}`;
    const options = orderId ? this.options(orderId, admin) : { headers: this.headers(this.credential?.token || '') };
    return firstValueFrom(this.http.get(`${base}/files/${file.id}`, { ...options, responseType: 'blob' }));
  }
  private orderUrl(id: string, admin: boolean): string { return `${this.api}/${admin ? 'admin/' : ''}orders/${id}/information`; }
  private options(id: string, admin: boolean): { headers: Record<string, string>; withCredentials: boolean } {
    return { headers: admin ? {} : this.headers(this.orderToken(id)), withCredentials: admin };
  }
  private headers(token: string): Record<string, string> { return { 'X-Information-Token': token }; }
  private form(text: string, model: string, modelName: string, files: File[], attachments: string[]): FormData {
    const data = new FormData();
    data.append('entry', new Blob([JSON.stringify({ text, model: modelName, modelKey: model, attachments })], { type: 'application/json' }));
    files.forEach(file => data.append('files', file)); return data;
  }
}
