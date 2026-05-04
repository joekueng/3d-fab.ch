import { PublicMediaUsageCollectionMap } from '../../core/services/public-media.service';
import {
  HomeCapabilityConfig,
  HomeProjectGlow,
} from './home-page.types';

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

export const DEFAULT_HOME_PROJECT_GLOW: HomeProjectGlow = {
  top: '#b8cbd0',
  right: '#9c7d55',
  bottom: '#757f65',
  left: '#d8d3c6',
  topLeft: '#d8d3c6',
  topRight: '#b58a5c',
  bottomRight: '#6f5f4c',
  bottomLeft: '#8b8d78',
};
