import { PublicMediaUsageCollectionMap } from '../../core/services/public-media.service';
import {HomeCapabilityConfig} from './home-page.types';

export const EMPTY_MEDIA_COLLECTIONS: PublicMediaUsageCollectionMap = {};

export const HOME_CAPABILITY_CONFIGS: readonly HomeCapabilityConfig[] = [
  {
    usageKey: 'capability-prototyping',
    titleKey: 'HOME.CAP_1_TITLE',
    textKey: 'HOME.CAP_1_TEXT',
  },
  {
    usageKey: 'capability-custom-parts',
    titleKey: 'HOME.CAP_2_TITLE',
    textKey: 'HOME.CAP_2_TEXT',
  },
  {
    usageKey: 'capability-small-series',
    titleKey: 'HOME.CAP_3_TITLE',
    textKey: 'HOME.CAP_3_TEXT',
  },
  {
    usageKey: 'capability-cad',
    titleKey: 'HOME.CAP_4_TITLE',
    textKey: 'HOME.CAP_4_TEXT',
  },
];

export const HOME_MEDIA_REQUESTS = [
  {
    usageType: 'HOME_SECTION' as const,
    usageKey: 'shop-gallery',
  },
  {
    usageType: 'HOME_SECTION' as const,
    usageKey: 'founders-gallery',
  },
  ...HOME_CAPABILITY_CONFIGS.map((config) => ({
    usageType: 'HOME_SECTION' as const,
    usageKey: config.usageKey,
  })),
] as const;
