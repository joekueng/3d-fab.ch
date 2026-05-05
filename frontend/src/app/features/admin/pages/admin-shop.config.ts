import { AdminMediaLanguage } from '../services/admin-media.service';
import { ProductStatusFilter, ShopLanguage } from './admin-shop.types';
import {
  ADMIN_LANGUAGE_LABELS,
  ADMIN_LOCALIZED_LANGUAGES,
} from '../../../shared/utils/admin-localization.util';

export const SHOP_LANGUAGES =
  ADMIN_LOCALIZED_LANGUAGES satisfies readonly ShopLanguage[];

export const MEDIA_LANGUAGES =
  ADMIN_LOCALIZED_LANGUAGES satisfies readonly AdminMediaLanguage[];

export const LANGUAGE_LABELS =
  ADMIN_LANGUAGE_LABELS satisfies Readonly<Record<ShopLanguage, string>>;

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
