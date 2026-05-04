import { AdminMediaLanguage } from '../services/admin-media.service';
import { ProductStatusFilter, ShopLanguage } from './admin-shop.types';

export const SHOP_LANGUAGES: readonly ShopLanguage[] = [
  'it',
  'en',
  'de',
  'fr',
];

export const MEDIA_LANGUAGES: readonly AdminMediaLanguage[] = [
  'it',
  'en',
  'de',
  'fr',
];

export const LANGUAGE_LABELS: Readonly<Record<ShopLanguage, string>> = {
  it: 'IT',
  en: 'EN',
  de: 'DE',
  fr: 'FR',
};

export const PRODUCT_STATUS_FILTERS: readonly ProductStatusFilter[] = [
  'ALL',
  'ACTIVE',
  'INACTIVE',
  'FEATURED',
];

export const MAX_MODEL_FILE_SIZE_BYTES = 100 * 1024 * 1024;
export const SHOP_LIST_PANEL_WIDTH_STORAGE_KEY =
  'admin-shop-list-panel-width';
export const MIN_LIST_PANEL_WIDTH_PERCENT = 32;
export const MAX_LIST_PANEL_WIDTH_PERCENT = 68;
