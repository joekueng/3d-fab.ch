import { PRINT_GUIDE_LINKS } from './print-guide-links';
import { Component, inject } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { LanguageService } from '../services/language.service';

@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [TranslatePipe, RouterLink, RouterLinkActive],
  templateUrl: './footer.component.html',
  styleUrls: ['./footer.component.scss'],
})
export class FooterComponent {
  readonly guideLinks = PRINT_GUIDE_LINKS;
  readonly languageService = inject(LanguageService);
}
