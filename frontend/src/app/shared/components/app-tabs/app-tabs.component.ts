import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-tabs',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="tabs">
      @for (tab of tabs(); track tab.value) {
        <button 
          class="tab" 
          [class.active]="activeTab() === tab.value"
          (click)="selectTab(tab.value)">
          {{ tab.label }}
        </button>
      }
    </div>
  `,
  styles: [`
    .tabs {
      display: flex;
      border-bottom: 1px solid var(--color-border);
      gap: var(--space-4);
    }
    .tab {
      background: none;
      border: none;
      padding: var(--space-3) var(--space-4);
      cursor: pointer;
      font-weight: 500;
      color: var(--color-text-muted);
      border-bottom: 2px solid transparent;
      transition: all 0.2s;
      
      &:hover { color: var(--color-text); }
      &.active {
        color: var(--color-brand);
        border-bottom-color: var(--color-brand);
      }
    }
  `]
})
export class AppTabsComponent {
  tabs = input<{label: string, value: string}[]>([]);
  activeTab = input<string>('');
  tabChange = output<string>();

  selectTab(val: string) {
    this.tabChange.emit(val);
  }
}
