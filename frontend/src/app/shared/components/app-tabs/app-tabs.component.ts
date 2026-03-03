import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-tabs',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './app-tabs.component.html',
  styleUrl: './app-tabs.component.scss',
})
export class AppTabsComponent {
  tabs = input<{ label: string; value: string }[]>([]);
  activeTab = input<string>('');
  tabChange = output<string>();

  selectTab(val: string) {
    this.tabChange.emit(val);
  }
}
