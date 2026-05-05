import { CommonModule, DOCUMENT, isPlatformBrowser } from '@angular/common';
import {
  AfterViewInit,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  PLATFORM_ID,
  SimpleChanges,
  ViewChild,
  inject,
} from '@angular/core';
import {
  normalizeRichTextStorageValue,
  replaceElementContentFromHtml,
  serializeNodeChildren,
} from './admin-shop-rich-text.util';

@Component({
  selector: 'app-admin-shop-rich-text-editor',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './admin-shop-rich-text-editor.component.html',
  styleUrl: './admin-shop-rich-text-editor.component.scss',
})
export class AdminShopRichTextEditorComponent
  implements AfterViewInit, OnChanges
{
  private readonly documentRef = inject(DOCUMENT);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private editorElement: HTMLDivElement | null = null;

  @Input() html = '';
  @Input() languageLabel = '';
  @Output() htmlChange = new EventEmitter<string>();

  @ViewChild('editorRef')
  set editorRef(value: ElementRef<HTMLDivElement> | undefined) {
    this.editorElement = value?.nativeElement ?? null;
    this.renderHtml();
  }

  ngAfterViewInit(): void {
    this.renderHtml();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['html']) {
      this.renderHtml();
    }
  }

  preventToolbarMouseDown(event: MouseEvent): void {
    event.preventDefault();
  }

  onEditorInput(event: Event): void {
    this.syncEditor(event.target as HTMLDivElement | null, false);
  }

  onEditorBlur(event: Event): void {
    this.syncEditor(event.target as HTMLDivElement | null, true);
  }

  format(command: 'bold' | 'italic' | 'underline'): void {
    this.applyExecCommand(command);
  }

  formatList(type: 'unordered' | 'ordered'): void {
    this.applyExecCommand(
      type === 'unordered' ? 'insertUnorderedList' : 'insertOrderedList',
    );
  }

  clearFormatting(): void {
    this.applyExecCommand('removeFormat');
  }

  private syncEditor(editor: HTMLDivElement | null, sanitize: boolean): void {
    if (!editor) {
      return;
    }

    const currentHtml = serializeNodeChildren(editor, this.isBrowser);
    const nextHtml = sanitize
      ? (normalizeRichTextStorageValue(currentHtml, this.isBrowser) ?? '')
      : currentHtml;

    this.html = nextHtml;
    this.htmlChange.emit(nextHtml);

    if (sanitize && currentHtml !== nextHtml) {
      replaceElementContentFromHtml(
        editor,
        nextHtml,
        this.documentRef,
        this.isBrowser,
      );
    }
  }

  private renderHtml(): void {
    const editor = this.editorElement;
    if (!editor) {
      return;
    }
    const safeHtml = this.html ?? '';
    if (serializeNodeChildren(editor, this.isBrowser) !== safeHtml) {
      replaceElementContentFromHtml(
        editor,
        safeHtml,
        this.documentRef,
        this.isBrowser,
      );
    }
  }

  private applyExecCommand(command: string): void {
    if (!this.isBrowser) {
      return;
    }
    const editor = this.editorElement;
    if (!editor) {
      return;
    }
    editor.focus();
    this.documentRef.execCommand(command, false);
    this.syncEditor(editor, false);
  }
}
