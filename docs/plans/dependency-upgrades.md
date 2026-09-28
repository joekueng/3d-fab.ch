# Piano aggiornamento Node, Gradle e dipendenze

Verifica: **28 settembre 2026**. Questa modifica introduce il piano e i controlli
di pubblicazione; non aggiorna runtime, librerie o lockfile. Le versioni candidate
vanno ricontrollate al momento della migrazione.

## Situazione rilevata

| Componente | Repository / ambiente | Candidato e vincoli |
| --- | --- | --- |
| Node | `.nvmrc` 22; engines `^22.0.0`; Docker e Gitea 22; terminale locale **26.0.0** | **24.21.0 LTS** come obiettivo produzione; 26.10.0 è Current, non LTS |
| Angular, CLI, Material, CDK, SSR | **19.2.19** | npm indica **22.2.0**; migrare una major alla volta: 19 → 20 → 21 → 22 |
| TypeScript | dichiarato `~5.7.2`, lock **5.7.3** | Selezionare il range richiesto da ogni major Angular; non installare automaticamente `latest` (**7.0.2**) |
| Gradle wrapper | **9.1.0**, senza `distributionSha256Sum` | release corrente verificata **9.8.0**, da coordinare con Spring Boot |
| Spring Boot | **3.4.1** | documentazione stable **4.1.1**; percorso 3.4 → 3.5 → 4.0 → 4.1 |
| Java | toolchain e immagini **21** | mantenere 21 durante questa migrazione per isolare le regressioni |
| Spring dependency management plugin | **1.1.7** | versione pubblicata; verificare compatibilità con Boot/Gradle scelti |

Fonti ufficiali: [release Node](https://nodejs.org/en/about/previous-releases),
[matrice Angular](https://angular.dev/reference/versions),
[guida aggiornamento Angular](https://angular.dev/update-guide),
[release Gradle](https://docs.gradle.org/current/release-notes.html),
[requisiti Boot 3.4](https://docs.spring.io/spring-boot/3.4/system-requirements.html),
[Boot 3.5](https://docs.spring.io/spring-boot/3.5/system-requirements.html) e
[Boot corrente](https://docs.spring.io/spring-boot/system-requirements.html).

**Vincoli bloccanti:** Angular 19 è elencato tra le versioni non più supportate e
la sua matrice ammette Node 18/20/22, non 24/26. Boot 3.4 e 3.5 dichiarano supporto
a Gradle 7/8: la coppia attuale Boot 3.4.1 + Gradle 9.1.0 è fuori da tale matrice,
anche se una build locale può riuscire. Boot 4.1 supporta Gradle 8.14+ e 9.x.

## Controllo dei package

Eseguiti `npm outdated --json`, `npm audit --package-lock-only --ignore-scripts --json`
e `node scripts/check-external-packages.mjs`. Quest'ultimo ha verificato **45/45**
elementi: 35 pacchetti npm diretti risolti dal lockfile, 8 coordinate Maven con
versione esplicita e 2 plugin Gradle. Identità/versione e metadati dei pacchetti npm
diretti corrispondono al registro. Nessuno di questi risulta deprecato al controllo.
Questo non certifica sicurezza, autenticità del maintainer o correttezza delle API.

L'audit completo segnala **73 dipendenze interessate**: 4 critical, 40 high,
25 moderate, 4 low. Include tool di sviluppo e dipendenze transitive; non è il
conteggio di vulnerabilità uniche o di exploit verificati nell'applicazione.
Con `--omit=dev` risultano **13** dipendenze interessate: 1 critical, 5 high,
6 moderate, 1 low. La critical runtime riguarda `@angular/ssr`; nell'albero
completo le altre critical riguardano `shell-quote`, `tar` e `websocket-driver`.
Sono da prioritizzare Angular/SSR (sanitizzazione e transfer cache), Express e
l'override `tar: 7.5.6`, che compare ancora negli avvisi. La prima immagine frontend
contiene anche le dipendenze di sviluppo: valutare uno stage runtime separato.

| Dipendenze frontend | Lock attuale | Versione disponibile riportata da npm | Azione |
| --- | --- | --- | --- |
| ngx-translate core / http-loader | 17.0.0 | 18.0.0 | controllare peer Angular e caricamento delle quattro lingue |
| Express / @types/express | 4.22.1 / 4.17.25 | 5.2.1 / 5.0.6 | prima valutare patch 4.22.3; major separata con verifica SSR e routing |
| Three / @types/three | 0.182.0 / 0.182.0 | 0.186.1 / 0.186.0 | aggiornare insieme; verificare caricamento modelli e controlli 3D |
| @types/node | 18.19.130 | 26.6.3 | scegliere tipi della major runtime adottata, non automaticamente latest |
| zone.js | 0.15.0 | 0.16.3 | lasciare la scelta ai peer della versione Angular |
| @types/jasmine / jasmine-core | 5.1.8 / 5.6.0 | 6.0.0 / 7.0.2 | aggiornare in coerenza con runner e adapter, non insieme alla cieca |
| karma-jasmine-html-reporter | 2.1.0 | 2.3.0 | verificare peer e test browser |
| Playwright / axe-core Playwright | 1.63.0 / 4.13.0 | non segnalati da outdated | mantenere inizialmente; sincronizzare browser installati se cambiano |
| RxJS / tslib / escape-html | 7.8.2 / 2.8.1 / 1.0.3 | non segnalati da outdated | mantenere salvo requisiti della migrazione |

Le versioni npm sono metadati del [registro ufficiale](https://registry.npmjs.org/),
non una raccomandazione di aggiornare tutto alla versione massima.

| Coordinate Maven esplicite | Attuale | `release` da Maven Central | Verifica specifica |
| --- | --- | --- | --- |
| com.maxmind.geoip2:geoip2 | 5.0.2 | 5.2.0 | lookup GeoLite e fallback |
| xyz.capybara:clamav-client | 2.1.2 | 2.1.2 | scansione e rifiuto upload |
| io.github.openhtmltopdf:openhtmltopdf-pdfbox | 1.1.37 | 1.1.87 | rendering e dipendenze PDFBox |
| io.github.openhtmltopdf:openhtmltopdf-svg-support | 1.1.37 | 1.1.87 | aggiornare con pdfbox; verificare SVG e font |
| net.codecrete.qrbill:qrbill-generator | 3.4.0 | 3.4.0 | QR bill e fatture |
| org.apache.james.jdkim:apache-jdkim-library | 0.5 | 0.5 | verifica DKIM, dipendenze transitive e supporto upstream |
| org.jsoup:jsoup | 1.18.3 | 1.23.2 | parsing/sanitizzazione HTML |
| org.lwjgl:lwjgl-bom | 3.3.4 | 3.4.3 | risoluzione e caricamento native Linux/macOS/ARM64 |

Fonte riproducibile: `https://repo.maven.apache.org/maven2/<group-path>/<artifact>/maven-metadata.xml`.
I POM delle versioni attuali sono stati verificati; `release` non prova compatibilità.
Starter, PostgreSQL, H2, Lombok e altre dipendenze gestite dai BOM richiedono ancora
l'inventario risolto Gradle e una scansione advisory JVM: non sono stati dichiarati sicuri.

## Sequenza di lavoro

1. **Baseline e priorità di sicurezza.** Usare Node 22 compatibile per riprodurre
   build/test attuali. Salvare report npm completo e runtime, inventario Gradle
   runtime/test, avvisi e test falliti preesistenti. Valutare patch compatibili
   per gli avvisi più urgenti e riesaminare l'override tar senza forzare le major.
2. **Frontend, migrazioni incrementali.** Eseguire le migrazioni ufficiali Angular
   19 → 20 → 21 → 22, aggiornando insieme core/CLI/compiler/SSR/CDK/Material e
   TypeScript secondo i peer di ciascuna tappa. Node 22 resta il ponte iniziale;
   passare a Node 24 LTS quando la tappa Angular lo supporta. Verificare subito
   CommonEngine, Express, hydration, transfer cache, stati HTTP 404/503 e i18n.
   I metadati npm verificati di compiler-cli 22.2.0 richiedono TypeScript >=6.0 <6.1
   e Node ^22.22.3 / ^24.15.0 / >=26.0.0. Core 22.2.0 ammette zone.js ~0.15.0 o
   ~0.16.0 e RxJS ^6.5.3 o ^7.4.0. Ricontrollare questi peer per la patch scelta.
3. **Allineamento Node/npm.** Aggiornare nello stesso cambiamento `.nvmrc`, engines,
   `@types/node`, entrambi i Dockerfile frontend, `scripts/e2e/browser.Dockerfile`,
   tutti i workflow `.gitea/workflows/` (incluso dependency-checks) e README.
   Fissare una versione npm verificata per rigenerare il lockfile. Rimuovere
   `--legacy-peer-deps` dopo un'installazione pulita con peer strict.
4. **Backend e Gradle.** Stabilire un ponte supportato su Gradle 8.14.x, scegliendo
   una patch pubblicata, e aggiornare Boot 3.4 → 3.5. Risolvere le deprecazioni;
   migrare poi a Boot 4.0 e 4.1 seguendo le guide ufficiali. Valutare cambiamenti
   Spring Security, Jackson, Hibernate, servlet e starter modularizzati. Solo con
   Boot compatibile portare il wrapper a Gradle 9.8.0 (o successiva release verificata),
   rigenerando wrapper JAR/script/properties e aggiungendo il checksum ufficiale.
   Il passaggio temporaneo a Gradle 8 serve a rientrare nella matrice supportata.
5. **Librerie indipendenti.** Migrare separatamente Express 5, Three, PDF/SVG,
   GeoIP, jsoup e LWJGL; ciascuna modifica deve avere le verifiche della tabella.
   Lasciare i componenti gestiti da Boot al BOM salvo eccezione motivata.
6. **Rafforzamento continuativo.** Aggiungere locking Gradle e checksum/signature
   verification dopo revisione dell'origine degli artifact; generare i checksum
   dal contenuto scaricato non dimostra da solo che l'origine sia fidata. Integrare
   una scansione JVM e npm audit in CI con gestione esplicita degli avvisi esistenti
   (responsabile, motivazione, scadenza). Verificare checksum di OrcaSlicer, FFmpeg,
   Gitleaks, immagini/action CI e tool esterni come Semgrep/Prettier; questi non sono
   coperti dal controllo dei manifest applicativi aggiunto ora.

## Criteri di accettazione e rilascio

- Ogni tappa ha una modifica separata e versioni/fonti registrate secondo la
  [policy dipendenze](../dependency-policy.md). Nessun aggiornamento indiscriminato.
- Frontend: installazione pulita, typecheck, `npm run test:ci`, `npm run build`,
  `npm run check:shop-ssr`, `npm run check:i18n`, `npm run check:ui-reuse`,
  typecheck E2E e flussi Chromium sullo stack usa e getta.
- Backend: `./gradlew compileJava test bootJar --warning-mode all`; prova startup
  con PostgreSQL usa e getta e controllo compatibilità dello schema. Verificare
  login/CSRF, checkout/TWINT simulato, upload/ClamAV, SSE e rendering email/fatture.
  Eseguire un confronto slicing con fixture solo quando il percorso coinvolto cambia.
- Build Docker effettive con il runtime scelto; gli avvisi high/critical richiedono
  correzione o una decisione documentata sulla raggiungibilità e sul rischio.
- Rilascio progressivo dev → int → prod dopo i controlli. Conservare digest delle
  immagini precedenti; per Boot/Hibernate valutare backup e differenze schema prima
  del deploy: con `ddl-auto=update` il rollback della sola immagine può non bastare.

## Limiti della verifica iniziale

Lo script passa 45/45 verifiche live. Sono stati inoltre simulati metadati validi,
integrità errata, HTTP 404, errore di rete e deprecazione: i quattro casi negativi
terminano con exit code 1. Passati controllo sintattico Node e `git diff --check`.
Il workflow Gitea è stato aggiunto ma non eseguito sul runner remoto.

Non sono stati eseguiti aggiornamenti, installazioni, build applicative, deployment
o una scansione completa delle transitive JVM. I controlli automatici aggiunti ora
verificano pubblicazione e coerenza dei metadati; la verifica delle API e dell'identità
del progetto richiede le fonti ufficiali e i test specificati dalla policy.
