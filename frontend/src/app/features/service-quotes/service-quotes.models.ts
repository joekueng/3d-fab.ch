import { AdminEmailLog } from '../admin/services/admin-email-log.model';

export type QuotePricingMode = 'HOURLY_ESTIMATE' | 'LUMP_SUM_ESTIMATE';
export type QuoteVariationType = 'PERCENTAGE' | 'AMOUNT_CHF';
export interface ServiceQuoteContent {
  title: string;
  description: string;
  pricingMode: QuotePricingMode;
  estimatedHours: number | null;
  hourlyRateChf: number | null;
  lumpSumChf: number | null;
  variationType: QuoteVariationType;
  variationValue: number;
  validUntil: string;
  language: string;
}
export interface ServiceQuoteReference {
  quoteId: string;
  revisionId: string;
  acceptanceId: string;
  revisionNumber: number;
  title: string;
  estimatedAmountChf: number;
  maximumAmountChf: number;
  variationType: QuoteVariationType;
  variationValue: number;
}
export interface QuoteCustomer {
  email: string;
  customerType: 'PRIVATE' | 'BUSINESS';
  firstName: string | null;
  lastName: string | null;
  companyName: string | null;
  contactPerson: string | null;
  addressLine1: string;
  zip: string;
  city: string;
  countryCode: string;
  phone: string | null;
}
export interface ServiceQuoteOffer {
  quoteId: string;
  revisionId: string;
  revisionNumber: number;
  status: string;
  content: ServiceQuoteContent;
  estimatedAmountChf: number;
  maximumAmountChf: number;
  acceptanceText: string;
  contentVersion: string;
  termsVersion: string;
  privacyVersion: string;
}
export interface QuoteConfirmation {
  acceptanceId: string;
  documentStatus: string;
  emailStatus: string;
  downloadExpiresAt: string;
}
export interface QuoteAcceptance {
  id: string;
  customer: QuoteCustomer;
  acceptedAt: string;
  clientIp: string | null;
  documentStatus: string;
  emailStatus: string;
  adminEmailStatus: string;
  documentHash: string | null;
}
export interface QuoteRevision {
  offer: ServiceQuoteOffer;
  publicLinkId: string;
  publicUrl: string;
  acceptance: QuoteAcceptance | null;
}
export interface AdminServiceQuote {
  id: string;
  version: number;
  clientName: string | null;
  collaborationName: string | null;
  sourceRequestId: string | null;
  draft: ServiceQuoteContent | null;
  createdAt: string;
  revisions: QuoteRevision[];
  linkedJobs: { sessionId: string; name: string | null; convertedOrderId: string | null }[];
  emailLogs: AdminEmailLog[];
}
export interface QuoteDraftRequest {
  content: ServiceQuoteContent;
  clientName: string;
  collaborationName: string;
  sourceRequestId: string | null;
  version: number;
}
export interface QuoteAcceptRequest {
  customer: QuoteCustomer;
  requestId: string;
  downloadToken: string;
  contentVersion: string;
  acceptQuote: boolean;
  acceptTerms: boolean;
  acceptPrivacy: boolean;
}
export interface QuoteResume {
  status: string;
  quoteId: string | null;
  revisionId: string | null;
  token: string | null;
}
