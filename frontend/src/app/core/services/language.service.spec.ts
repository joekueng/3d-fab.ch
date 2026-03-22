import { Subject } from 'rxjs';
import {
  DefaultUrlSerializer,
  NavigationEnd,
  Router,
  UrlTree,
} from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { LanguageService } from './language.service';
import { RequestLike } from '../../../core/request-origin';

describe('LanguageService', () => {
  function createTranslateMock() {
    const onLangChange = new Subject<{ lang: string }>();
    const translate = {
      currentLang: '',
      addLangs: jasmine.createSpy('addLangs'),
      setFallbackLang: jasmine.createSpy('setFallbackLang'),
      use: jasmine.createSpy('use').and.callFake((lang: string) => {
        translate.currentLang = lang;
        onLangChange.next({ lang });
      }),
      onLangChange,
    };

    return translate as unknown as TranslateService;
  }

  function createRouterMock(initialUrl: string) {
    const serializer = new DefaultUrlSerializer();
    const events$ = new Subject<unknown>();

    const createUrlTree = (
      commands: unknown[],
      extras?: { queryParams?: Record<string, string>; fragment?: string },
    ): UrlTree => {
      const segments = commands
        .filter((entry) => typeof entry === 'string' && entry !== '/')
        .map((entry) => String(entry));

      let url = `/${segments.join('/')}`;
      if (url === '') {
        url = '/';
      }

      const queryParams = extras?.queryParams ?? {};
      const query = new URLSearchParams();
      Object.entries(queryParams).forEach(([key, value]) => {
        query.set(key, value);
      });
      const queryString = query.toString();
      if (queryString) {
        url += `?${queryString}`;
      }

      if (extras?.fragment) {
        url += `#${extras.fragment}`;
      }

      return serializer.parse(url);
    };

    const router = {
      url: initialUrl,
      events: events$.asObservable(),
      parseUrl: (url: string) => serializer.parse(url),
      createUrlTree,
      serializeUrl: (tree: UrlTree) => serializer.serialize(tree),
      navigateByUrl: jasmine
        .createSpy('navigateByUrl')
        .and.callFake((tree: UrlTree) => {
          const nextUrl = serializer.serialize(tree);
          router.url = nextUrl;
          events$.next(new NavigationEnd(1, nextUrl, nextUrl));
          return Promise.resolve(true);
        }),
    };

    return router as unknown as Router;
  }

  it('prefixes URL with default language when missing', () => {
    const translate = createTranslateMock();
    const router = createRouterMock('/calculator?session=abc');
    const navigateSpy = router.navigateByUrl as unknown as jasmine.Spy;
    const request: RequestLike = {
      headers: {
        'accept-language': 'it-CH,it;q=0.9,en;q=0.8',
      },
    };

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const service = new LanguageService(translate, router, request);

    expect(translate.use).toHaveBeenCalledWith('it');
    expect((translate as any).setFallbackLang).toHaveBeenCalledWith('it');
    expect(navigateSpy).toHaveBeenCalledTimes(1);

    const firstCall = navigateSpy.calls.mostRecent();
    const tree = firstCall.args[0] as UrlTree;
    const navOptions = firstCall.args[1] as { replaceUrl: boolean };
    expect(router.serializeUrl(tree)).toBe('/it/calculator?session=abc');
    expect(navOptions.replaceUrl).toBeTrue();
  });

  it('uses the preferred browser language on the root URL', () => {
    const translate = createTranslateMock();
    const router = createRouterMock('/');
    const navigateSpy = router.navigateByUrl as unknown as jasmine.Spy;
    const request: RequestLike = {
      headers: {
        'accept-language': 'de-CH,de;q=0.9,en;q=0.8,it;q=0.7',
      },
    };

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const service = new LanguageService(translate, router, request);

    expect(translate.use).toHaveBeenCalledWith('de');
    expect(navigateSpy).toHaveBeenCalledTimes(1);

    const firstCall = navigateSpy.calls.mostRecent();
    const tree = firstCall.args[0] as UrlTree;
    expect(router.serializeUrl(tree)).toBe('/de');
  });

  it('uses the default language for non-root URLs without a language prefix', () => {
    const translate = createTranslateMock();
    const router = createRouterMock('/calculator?session=abc');
    const navigateSpy = router.navigateByUrl as unknown as jasmine.Spy;
    const request: RequestLike = {
      headers: {
        'accept-language': 'de-CH,de;q=0.9,en;q=0.8,it;q=0.7',
      },
    };

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const service = new LanguageService(translate, router, request);

    expect(translate.use).toHaveBeenCalledWith('de');
    expect(navigateSpy).toHaveBeenCalledTimes(1);

    const firstCall = navigateSpy.calls.mostRecent();
    const tree = firstCall.args[0] as UrlTree;
    expect(router.serializeUrl(tree)).toBe('/it/calculator?session=abc');
  });

  it('switches language while preserving path and query params', () => {
    const translate = createTranslateMock();
    const router = createRouterMock('/it/calculator?session=abc&mode=advanced');
    const navigateSpy = router.navigateByUrl as unknown as jasmine.Spy;
    const service = new LanguageService(translate, router);

    expect(navigateSpy).not.toHaveBeenCalled();

    service.switchLang('de');

    expect(translate.use).toHaveBeenCalledWith('de');
    expect(navigateSpy).toHaveBeenCalledTimes(1);

    const call = navigateSpy.calls.mostRecent();
    const tree = call.args[0] as UrlTree;
    expect(router.serializeUrl(tree)).toBe(
      '/de/calculator?session=abc&mode=advanced',
    );
  });

  it('builds localized paths for internal links while preserving query and hash', () => {
    const translate = createTranslateMock();
    const router = createRouterMock('/de/shop');
    const service = new LanguageService(translate, router);

    expect(service.localizedPath('/privacy')).toBe('/de/privacy');
    expect(service.localizedPath('/it/contact?topic=seo#form')).toBe(
      '/de/contact?topic=seo#form',
    );
  });

  it('switches product pages using the resolved localized route overrides', () => {
    const translate = createTranslateMock();
    const router = createRouterMock('/it/shop/p/12345678-supporto-cavo');
    const navigateSpy = router.navigateByUrl as unknown as jasmine.Spy;
    const service = new LanguageService(translate, router);

    service.setLocalizedRouteOverrides({
      it: '/it/shop/p/12345678-supporto-cavo',
      de: '/de/shop/p/12345678-kabelhalter',
    });
    navigateSpy.calls.reset();

    service.switchLang('de');

    const call = navigateSpy.calls.mostRecent();
    const tree = call.args[0] as UrlTree;
    expect(router.serializeUrl(tree)).toBe('/de/shop/p/12345678-kabelhalter');
  });
});
