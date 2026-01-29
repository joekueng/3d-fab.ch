import { Routes } from '@angular/router';
import { HomeComponent } from './home/home.component';
import { BasicQuoteComponent } from './quote/basic-quote/basic-quote.component';
import { AdvancedQuoteComponent } from './quote/advanced-quote/advanced-quote.component';
import { ContactComponent } from './contact/contact.component';

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'quote/basic', component: BasicQuoteComponent },
  { path: 'quote/advanced', component: AdvancedQuoteComponent },
  { path: 'contact', component: ContactComponent },
  { path: '**', redirectTo: '' }
];
