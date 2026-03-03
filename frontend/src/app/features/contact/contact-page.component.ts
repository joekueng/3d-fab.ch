import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { ContactFormComponent } from './components/contact-form/contact-form.component';
import { AppCardComponent } from '../../shared/components/app-card/app-card.component';

@Component({
  selector: 'app-contact-page',
  standalone: true,
  imports: [CommonModule, TranslateModule, ContactFormComponent, AppCardComponent],
  templateUrl: './contact-page.component.html',
  styleUrl: './contact-page.component.scss'
})
export class ContactPageComponent {}
