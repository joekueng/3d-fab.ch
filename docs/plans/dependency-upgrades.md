# Aggiornamento Node, Angular, Gradle e dipendenze

Stato al **29 settembre 2026: completato e verificato localmente**. Il rilascio
resta progressivo dev → int → prod secondo il normale processo di deploy.

## Versioni risultanti

| Componente | Prima | Dopo |
| --- | --- | --- |
| Node | 22 | **24.21.0 LTS** in `.nvmrc`, Docker e Gitea |
| Angular, CLI, Material, CDK, SSR | 19.2.19 | **22.2.0** |
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
