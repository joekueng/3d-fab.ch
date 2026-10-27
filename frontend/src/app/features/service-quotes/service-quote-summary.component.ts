import { CommonModule } from '@angular/common';
import { Component, input } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { ServiceQuoteOffer } from './service-quotes.models';

@Component({
  selector: 'app-service-quote-summary',
  imports: [CommonModule, TranslatePipe],
  templateUrl: './service-quote-summary.component.html',
  styleUrl: './service-quote-summary.component.scss',
})
export class ServiceQuoteSummaryComponent {
  readonly offer = input.required<ServiceQuoteOffer>();
  validityDate(): string {
    return new Intl.DateTimeFormat(`${this.offer().content.language}-CH`, {
      year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
      timeZone: 'Europe/Zurich', timeZoneName: 'short',
    }).format(new Date(this.offer().content.validUntil));
  }
}
