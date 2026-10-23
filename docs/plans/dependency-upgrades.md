# Aggiornamento Node, Angular, Gradle e dipendenze

Stato al **29 settembre 2026: completato e verificato localmente**. Il rilascio
resta progressivo dev → int → prod secondo il normale processo di deploy.

## Versioni risultanti

| Componente | Prima | Dopo |
| --- | --- | --- |
| Node | 22 | **24.21.0 LTS** in `.nvmrc`, Docker e Gitea |
| Angular, CLI, Material, CDK, SSR | 19.2.19 | **22.2.0** |
| ngx-translate core / http-loader | 17.0.0 | **18.0.0** |
| TypeScript | 5.7.3 | **6.0.3** |
| Express / tipi | 4.22.1 / 4.17.25 | **5.2.1 / 5.0.6** |
| Three / tipi | 0.182.0 / 0.182.0 | **0.186.1 / 0.186.0** |
| Spring Boot | 3.4.1 | **4.1.1** |
| Gradle wrapper | 9.1.0 | **9.8.0**, con checksum della distribuzione |
| Java | 21 | **21**, intenzionalmente invariato |

Sono stati aggiornati anche GeoIP 5.2.0, OpenHTMLToPDF 1.1.87, jsoup 1.23.2
e LWJGL 3.4.3. Il backend usa gli starter modulari di Boot 4.1 e mantiene
temporaneamente `spring-boot-jackson2` mentre i servizi applicativi restano su
Jackson 2. Locking Gradle e verifica checksum degli artifact sono attivi.

Le versioni sono state controllate secondo la [policy dipendenze](../dependency-policy.md)
contro le fonti ufficiali: [release Node](https://nodejs.org/en/about/previous-releases),
[matrice Angular](https://angular.dev/reference/versions),
[guida aggiornamento Angular](https://angular.dev/update-guide),
[release Gradle](https://docs.gradle.org/current/release-notes.html) e
[requisiti Spring Boot](https://docs.spring.io/spring-boot/system-requirements.html).

## Compatibilità e produzione

- Le route catch-all SSR usano la sintassi Express 5 `/{*path}`; gli asset sono
  serviti da middleware statico senza wildcard. Il test `check:shop-ssr` importa
  il bundle di produzione e copre rendering concorrente e risposte 200/503.
- Angular 22 valida gli host SSR. `NG_ALLOWED_HOSTS` resta obbligatorio e non viene
  disabilitato: Compose consente i tre host pubblici noti e lo stack E2E consente
  esplicitamente solo loopback.
- L'immagine frontend è multi-stage. Lo stage runtime installa con
  `npm ci --omit=dev --ignore-scripts`, copia solo `dist/` e gira come utente
  `node`; CLI, Karma, TypeScript e gli altri strumenti di sviluppo non entrano
  nell'immagine pubblicata.
- Il lockfile npm usa peer strict, senza `--legacy-peer-deps`. L'override `tar`
  resta fissato a 7.5.22.

## Audit e controlli di pubblicazione

Il refresh delle dipendenze transitive di Karma ha corretto gli 11 avvisi rimasti
(`brace-expansion`, `engine.io`, `flatted`, `follow-redirects`, `lodash`,
`minimatch`, `picomatch`, `socket.io-adapter`, `socket.io-parser`, `tmp` e `ws`).
Al controllo del 29 settembre 2026:

- `npm audit --package-lock-only --ignore-scripts`: **0 vulnerabilità**;
- `npm audit --package-lock-only --ignore-scripts --omit=dev`: **0 vulnerabilità**;
- `node scripts/check-external-packages.mjs`: **44/44 verifiche superate**.

Il workflow `dependency-checks` ripete entrambi gli audit quando cambiano manifest
o lockfile, oltre al controllo dei metadati esterni.

Il controllo esterno verifica pubblicazione, versione, URL/integrità e deprecazione
dei pacchetti diretti/versionati; non certifica il maintainer né sostituisce la
revisione delle API o una scansione advisory JVM.

## Verifica completata

- frontend: installazione pulita Node 24, build produzione, typecheck, parità i18n,
  riuso UI, **177 test**, bridge SSR e audit completo/produzione;
- backend: **350 test**, `compileJava`, `bootJar`, build Gradle, lock e checksum;
- pacchetti esterni: **44/44**;
- Docker frontend: build multi-stage reale, avvio non-root, redirect `/` 302,
  deep-link SSR 200, asset statico 200 con cache annuale e albero runtime privo di
  dipendenze dev.

La build Angular conserva due warning di budget SCSS preesistenti per home e
dashboard admin. I workflow Gitea e il deploy remoto non sono stati eseguiti
localmente. Prima del passaggio in produzione conservare i digest precedenti e,
poiché Hibernate usa ancora `ddl-auto=update`, verificare backup e differenze di
schema: il rollback della sola immagine potrebbe non essere sufficiente.

## Aggiornamento patch frontend del 21 ottobre 2026

Angular framework, CLI, build, SSR, Material e CDK sono allineati alla **22.2.3**.
Il lockfile è generato da npm; gli altri pacchetti diretti restano invariati.
`follow-redirects` passa alla **1.16.1** entro il range già dichiarato dal suo
chiamante. L'override limitato a `@angular/build` seleziona **Vite 8.3.3** al posto
della versione 8.3.0 fissata dal builder. Rimuovere questo override quando il
builder includerà Vite 8.3.3 o una versione successiva compatibile.

Identità, pubblicazione, requisiti e integrità sono stati verificati il
21 ottobre 2026 contro i metadati npm delle versioni selezionate:
[@angular/core 22.2.3](https://registry.npmjs.org/@angular%2fcore/22.2.3),
[@angular/build 22.2.3](https://registry.npmjs.org/@angular%2fbuild/22.2.3),
[Vite 8.3.3](https://registry.npmjs.org/vite/8.3.3) e
[follow-redirects 1.16.1](https://registry.npmjs.org/follow-redirects/1.16.1),
oltre agli altri pacchetti Angular e a tutte le versioni transitive cambiate.
Le identità sono riscontrate con le guide ufficiali di
[Angular](https://angular.dev/installation),
[Material](https://material.angular.dev/guide/getting-started),
[Vite](https://vite.dev/guide/) e il
[repository follow-redirects](https://github.com/follow-redirects/follow-redirects).

I requisiti pubblicati confermano Node 24, TypeScript 6.0, RxJS 7.8 e Zone 0.16;
i peer Angular e Material/CDK sono coerenti. Non sono introdotte nuove API
applicative. Il bridge SSR esistente è verificato sul bundle reale aggiornato.

Verifica locale: installazioni pulite con `--strict-peer-deps`, prima con
`--ignore-scripts` e poi normali; metadati esterni **45/45**; **45 versioni nuove
o cambiate** verificate anche per origine e integrità; audit runtime pulito e
policy di audit completo superata senza modifiche alle eccezioni; **228/228 test
Angular**, **7/7 test del controllo audit**, build produzione e test SSR superati.
Passano anche typecheck app/spec/E2E, i18n, riuso UI, inventario route e uno smoke
del server di sviluppo con Vite. I due warning SCSS preesistenti restano.
Le verifiche host usano Node **24.15.0**, compatibile con i requisiti dichiarati;
lo stack Docker usa Node **24.21.0** fissato dal repository.
La suite fullstack Chromium sullo stack Docker temporaneo passa **45/45 E2E**,
inclusi upload OBJ/STEP/STP, preventivi, ripristino sessioni, checkout e fatture
CAD. Lo stack e i relativi volumi sono rimossi al termine. La matrice degli altri
browser e i workflow remoti non sono stati eseguiti in questa verifica.
