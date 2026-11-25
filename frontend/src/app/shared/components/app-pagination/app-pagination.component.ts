import { Component, computed, input, output } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { TranslatePipe } from '@ngx-translate/core';
import { AppButtonComponent } from '../app-button/app-button.component';
import { PageInfo } from '../../models/page-info';

@Component({
  selector: 'app-pagination',
  imports: [AppButtonComponent, DecimalPipe, TranslatePipe],
  templateUrl: './app-pagination.component.html',
  styleUrl: './app-pagination.component.scss',
})
export class AppPaginationComponent {
  readonly pageInfo = input.required<PageInfo>();
  readonly loading = input(false);
  readonly pageChange = output<number>();

  readonly first = computed(
    () => this.pageInfo().page * this.pageInfo().size + 1,
  );
  readonly last = computed(() =>
    Math.min(
      this.first() + this.pageInfo().size - 1,
      this.pageInfo().totalElements,
    ),
  );
}
