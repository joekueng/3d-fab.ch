import { Subject } from 'rxjs';
import { DefaultUrlSerializer, Router, UrlTree } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { LanguageService } from './language.service';

describe('LanguageService', () => {
  function createTranslateMock() {
    const onLangChange = new Subject<{ lang: string }>();
    const translate = {
      currentLang: '',
      addLangs: jasmine.createSpy('addLangs'),
      setDefaultLang: jasmine.createSpy('setDefaultLang'),
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
      navigateByUrl: jasmine.createSpy('navigateByUrl'),
    };

    return router as unknown as Router;
  }

  it('prefixes URL with default language when missing', () => {
    const translate = createTranslateMock();
    const router = createRouterMock('/calculator?session=abc');
    const navigateSpy = router.navigateByUrl as unknown as jasmine.Spy;

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const service = new LanguageService(translate, router);

    expect(translate.use).toHaveBeenCalledWith('it');
    expect(navigateSpy).toHaveBeenCalledTimes(1);

    const firstCall = navigateSpy.calls.mostRecent();
    const tree = firstCall.args[0] as UrlTree;
    const navOptions = firstCall.args[1] as { replaceUrl: boolean };
    expect(router.serializeUrl(tree)).toBe('/it/calculator?session=abc');
    expect(navOptions.replaceUrl).toBeTrue();
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
});
