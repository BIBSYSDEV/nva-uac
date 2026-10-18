# Bruk av Cristin API (upstream) i nva-cristin-service og nva-identity-service

Rapporten viser hva NVA faktisk bruker av Cristin REST API, utledet fra request- og response-modellene og klientkoden på `main` (oktober 2026).
Seksjon 1–9 gjelder `nva-cristin-service`. Seksjon 10 gjelder `nva-identity-service` og er hentet fra `nva-identity-service/cristin-upstream-contract.md`.
POC-koden (`cristin-adapter`) og `build/`-mapper er utelatt.
Rapporten er laget ved å lese koden. Ingenting er kjørt mot Cristin, så linjenumre er veiledende.

**Forkortelser for stier:**

| Prefiks | Sti |
|---|---|
| `C/` | `cristin-commons/src/main/java/no/unit/nva/` |
| `P/` | `cristin-project/src/main/java/no/unit/nva/cristin/projects/` |
| `PE/` | `cristin-person/src/main/java/no/unit/nva/cristin/person/` |
| `O/` | `cristin-organization/src/main/java/no/unit/nva/cristin/organization/` |
| `F/` | `cristin-funding/src/main/java/no/unit/nva/cristin/funding/sources/` |
| `K/` | `cristin-keyword/src/main/java/no/unit/nva/cristin/keyword/` |
| `B/` | `cristin-biobank/src/main/java/no/unit/nva/biobank/` |
| `T` | `template.yaml` |
| `S` | `docs/cristin-proxy-swagger.yaml` |

---

## 1. Oversikt over alle upstream-endepunkter

`{API}` er verdien av `CRISTIN_API_URL`, se 2.1.

| # | Metode | Upstream-sti | NVA-endepunkt | Modul |
|---|---|---|---|---|
| U1 | GET | `/projects/{id}` | `GET /project/{id}`, sjekk før PATCH, berikelse av søketreff | project |
| U2 | GET | `/projects` | `GET /project/`, `GET /organization/{id}/projects` | project |
| U3 | GET | `/projects/facets` | `GET /project/` med aggregerings-versjon | project |
| U4 | GET | `/projects/categories` | `GET /category/project/` | project |
| U5 | POST | `/projects` | `POST /project/` | project |
| U6 | PATCH | `/projects/{id}` | `PATCH /project/{id}/` | project |
| U7 | GET | `/persons` | `GET /person/`, `GET /organization/{id}/persons`, `POST /person/identityNumber/` | person |
| U8 | GET | `/persons/facets` | `GET /person/` med aggregerings-versjon | person |
| U9 | GET | `/persons/{id}`, `/persons/ORCID:{orcid}` | `GET /person/{id}`, berikelse av søketreff | person |
| U10 | POST | `/persons` | `POST /person/` | person |
| U11 | PATCH | `/persons/{id}` | `PATCH /person/{id}/` | person |
| U12 | GET | `/persons/{id}/affiliations` | `GET /person/{id}/employment/` | person |
| U13 | POST | `/persons/{id}/affiliations` | `POST /person/{id}/employment/` | person |
| U14 | PATCH | `/persons/{id}/affiliations/{eid}` | `PATCH /person/{id}/employment/{eid}/` | person |
| U15 | DELETE | `/persons/{id}/affiliations/{eid}` | `DELETE /person/{id}/employment/{eid}` | person |
| U16 | GET | `/persons/{id}/institutions/{inst}` | `GET /person/{id}/organization/{orgId}/` | person |
| U17 | PATCH | `/persons/{id}/institutions/{inst}` | `PATCH /person/{id}/organization/{orgId}/` | person |
| U18 | GET | `/persons/{id}/picture` | `GET /person/{id}/picture/` | person |
| U19 | PUT | `/persons/{id}/picture` | `PUT /person/{id}/picture/` | person |
| U20 | GET | `/persons/affiliations/positions` | `GET /position/` | person |
| U21 | GET | `/countries` | `GET /country/` | person |
| U22 | GET | `/persons/orcid` | `GET /person/orcid/` | person |
| U23 | GET | `/units/{id}` | `GET /organization/{id}`, og rekursivt i v1 | organization |
| U24 | GET | `/units` (`parent_unit_id`, `name`, `id`) | `GET /organization/{id}`, `GET /organization` | organization |
| U25 | GET | `{parent_unit.url}`, `{subunits[].url}` | `GET /organization/...`, kun v1 (rekursivt) | organization |
| U26 | GET | `/fundings/sources` | `GET /funding-sources`, `GET /funding-sources/{id}` | funding |
| U27 | GET | `/keywords` | `GET /keyword` | keyword |
| U28 | GET | `/keywords/{id}` | `GET /keyword/{id}` | keyword |
| U29 | POST | `/keywords` | `POST /keyword` | keyword |
| U30 | GET | `/biobanks/{id}` | `GET /biobank/{id}` | biobank |
| I1 | GET | `/persons/resolve?national_id=` | innlogging (person på fødselsnummer) | **nva-identity-service** |
| I2 | GET | `/persons/{id}` | innlogging / oppslag på id | **nva-identity-service** |
| I3 | POST | `/persons` | opprett person ved første innlogging | **nva-identity-service** |
| I4 | GET | `{affiliations[].institution.url}` (i praksis `/institutions/{id}`) | innlogging, én gang per aktiv affiliation | **nva-identity-service** |

**Ikke i bruk:**
- `/institutions` direkte i nva-cristin-service. nva-identity-service kaller det indirekte via `institution.url` (I4).
- `/results`
- biobank-listen: `fetchBiobanksWithParameters` finnes, men ingen handler kaller den.

**Annet:** Route53-helsesjekken i `T` går mot `/v2/units`.

---

## 2. Felles oppførsel (cristin-commons)

### 2.1 Miljøvariabler og secrets

| Navn | Bruk | Kilde / verdi |
|---|---|---|
| `CRISTIN_API_URL` | Base for alle upstream-URL-er (`C/cristin/model/Constants.java:21`). Funding leser den på nytt selv. | Parameter `CristinApiUrl` (`T:58-64`). Standard `https://api.cristin-test.uio.no/v2`, tillatt også `https://api.cristin.no/v2`. |
| `CRISTIN_BOT_FILTER_BYPASS_HEADER_NAME` / `..._VALUE` | Navn og verdi for bot-filter-headeren | SSM-parametere `CristinBotFilterBypassHeaderName` / `...Value` (`T:25-26, 81-87`) |
| Secret `CristinClientBasicAuth` (`username`, `password`) | Basic-auth mot Cristin (`C/cristin/common/client/CristinAuthenticator.java:24`) | AWS Secrets Manager |
| `BASE_PATH`, `DOMAIN_NAME` | Brukes bare til NVA-id-er og -lenker, aldri til upstream | `T:18,20` |

### 2.2 HTTP-klienter

| Variant | Auth | Opprettet i | Innstillinger |
|---|---|---|---|
| **D** (default) | Ingen | `C/client/HttpClientProvider.java:12-14` | HTTP/1.1, `Redirect.ALWAYS`, 15 s connect timeout, ingen timeout per request (Lambda-timeout 40 s, `T:9`) |
| **A** (challenge) | `java.net.Authenticator` med Basic. Legitimasjon sendes **bare** som svar på en 401 med `WWW-Authenticate: Basic`. JDK-en kan deretter cache den innenfor samme klientinstans. | `CristinAuthenticator.java:56-59` | Som D |
| **B** (preemptiv) | Legger `Authorization: Basic base64(user:pass)` direkte på requesten, oppå klient D | `ApiClient.java:100,145` (`basicAuthHeader()`), `FetchPictureApiClient.java:73` | – |

### 2.3 Request-metoder og hvilke headere de sender

| Metode (`C/cristin/common/client/`) | Headere | `lang` | Retry |
|---|---|---|---|
| `fetchGetResult`, `fetchQueryResults` | bot | ja | ja |
| `fetchGetResultWithAuthentication` | bot + B | ja | ja |
| `fetchQueryResultsOneByOne` (async) | bot | ja | ja |
| `authorizedFetchQueryResultsOneByOne` (async) | bot + B | ja | ja |
| `sendRequestMultipleTimes` (async, kun org v1) | bot | ja | resilience4j + egen løkke, maks 2 forsøk med 500 ms pause |
| `PostApiClient.post(uri, body)` | `Content-Type: application/json; charset=utf-8` + bot | nei | ja |
| `PostApiClient.post(uri, body, inst)` | som over + `Cristin-Representing-Institution: <inst>` | nei | ja |
| `PatchApiClient.patch(uri, body)` | `Content-Type: application/merge-patch+json` + bot | nei | ja |
| `PatchApiClient.patch(uri, body, inst)` | som over + `Cristin-Representing-Institution` | nei | ja |

**Hva headerne og parameterne betyr:**
- **bot:** headeren `${CRISTIN_BOT_FILTER_BYPASS_HEADER_NAME}: ${CRISTIN_BOT_FILTER_BYPASS_HEADER_VALUE}`.
- **`lang`:** `lang=en,nb,nn` legges sist i URI-en via `C/utils/UriUtils.addLanguage`, og blir kodet som `lang=en%2Cnb%2Cnn`.
- **Retry:** resilience4j, 3 forsøk med 100 ms mellom. Det prøves på nytt ved exception eller 5xx (`C/client/RetryConfigProvider.java:33-60`). Etter tredje 5xx brukes den siste responsen.

**Headere som aldri sendes upstream:**
- `Accept` sendes aldri, og heller ingen versjonsheader. NVA-klientens `Accept: ...; version=` bestemmer bare hvilken intern klient som brukes (se 2.6).

### 2.4 Statusmapping (`ApiClient.checkHttpStatusCode`, `:275-290`)

| Upstream | NVA |
|---|---|
| 200–300 | suksess |
| 400 | 400. For POST og PATCH tas upstream-body med i meldingen. |
| 401 | 401 |
| 403 | 502, og det logges en advarsel om bot-headeren |
| 404 | 404 |
| ≥ 500 | 502, og body logges |
| annen ≥ 300 | 500 |
| JSON som ikke kan deserialiseres | 502 |

**Unntak:**
- Org v1 gir 404 for upstream 404. Andre feil gir `FailedHttpRequestException` (502) med `"{status}:{body}"`.
- Bilde-PUT: 400 og 415 gir NVA 400.

### 2.5 Response-headere (`C/cristin/model/SearchResponse.java:183-241`)

| Header | Bruk |
|---|---|
| `x-total-count` (uavhengig av store/små bokstaver) | Gir `size`, og 0 hvis den mangler. `firstRecord` = (page−1)×results+1. NVA svarer 400 («page out of scope») hvis `size < firstRecord`, eller hvis `size == 0` og `page > 1`. |
| `link` | Sjekkes bare som substring. Inneholder den `rel="next"` og `size >= firstRecord + results`, blir `nextResults` = NVA-URI med page+1. Inneholder den `rel="prev"` og `page > 1`, blir `previousResults` = page−1. **URL-ene i `Link` leses aldri.** |
| `X-Total-Count` via `ApiClient.getCount` | Brukes bare av org v1, og faller tilbake på antall treff |
| `x-total-count` via `MultiPageProcessor` | Org v2023, se 6.3 |

Ingen andre response-headere leses.

### 2.6 Valg av versjon fra NVA-`Accept`

`version=` leses fra NVA-requestens `Accept`-header med `ContentType.parse(...).getParameter("version")` (`C/utils/VersioningUtils.java`).

| Endepunkt | `version=` | Klient |
|---|---|---|
| `GET /project/`, `GET /person/` | `2023-11-03-aggregations`, `aggregations`, `2023-11-03` | Facet-klient (U3 / U8) |
| | `1`, mangler eller ukjent verdi | v1 (U2 / U7) |
| `GET /organization`, `GET /organization/{id}` | `2023-05-26`, mangler eller ukjent verdi (f.eks. `2`) | v2023-05-26 |
| | `1` | Legacy v1 |

Ingen andre handlere ser på versjonen.

### 2.7 Felles regex og konstanter (`C/cristin/model/Constants.java:47-56` m.fl.)

| Navn | Verdi |
|---|---|
| `PATTERN_IS_NON_EMPTY` | `.+` |
| `PATTERN_IS_NUMBER` | `[1-9]\d*` |
| `PATTERN_IS_LANGUAGE` | `(en\|nb\|nn\|\,)+` |
| `PATTERN_IS_STATUS` | `(?i)CONCLUDED\|ACTIVE\|NOT[ +]*STARTED` |
| `PATTERN_IS_TITLE` | `^[æøåÆØÅ\w-,\.:; ]+$` |
| `PATTERN_IS_DATE` | `(\d){4}-(\d){2}-(\d){2}[T]*[(\d){2}:(\d){2}:(\d){2,6}Z]*` |
| `PATTERN_IS_URL` | `(http(s):\/\/.)[-a-zA-Z0-9@:%._\+~#=]{2,256}\.[a-z]{2,6}\b([-a-zA-Z0-9@:%_\+.~#?&//=]*)` (krever https) |
| Unit-id (`C/model/Organization.java:28`) | `^(?:[0-9]+\.){3}[0-9]{1,3}$`, f.eks. `194.63.10.0` |
| ORCID | `[0-9]{4}-[0-9]{4}-[0-9]{4}-[0-9]{3}[0-9\|Xx]` (`\|` er en bokstavelig pipe i tegnklassen) |
| «Gyldig identifikator» (`C/cristin/common/Utils.java:55-62,193-199`) | `Integer.parseInt(s) > 0`. Den **rå strengen** sendes videre, så `+5` og `007` sendes upstream uendret. |
| `removeUnitPartFromIdentifierIfPresent` | Beholder teksten før første `.`, slik at `194.63.0.0` blir `194` |
| Kommasplitt for flerverdi-parametere | `,\|%2C` (`C/cristin/model/CristinQuery.java:44`) |
| Standard `page` / `results` | `1` / `5` (`C/cristin/common/handler/CristinQueryHandler.java:31-46`), med unntak nevnt per endepunkt |

### 2.8 Bygging og koding av URI

- Query-parametere legges til med `UriWrapper.addQueryParameter(s)` (Apache `URIBuilder`), som prosent-koder alt som ikke er unreserved. Mellomrom blir `%20`, komma blir `%2C` og kolon blir `%3A`.
- **Prosjekt:** parameterne sorteres alfabetisk på Cristin-navn (TreeMap). Facet-parametere kommer deretter, også sortert, og `lang` kommer sist.
- **Person, organisasjon og keyword:** parameterne ligger i et `ConcurrentHashMap`, så rekkefølgen er ikke garantert. `lang` kommer sist.
- **Stisegmenter:** settes inn rått, og kolonet i `ORCID:` beholdes.
- **Facets-varianten (person og prosjekt):** `/facets` legges på *etter* at query er bygget, og den dekodede query-strengen kopieres. `&`, `=`, `+` og `,` inni verdier er da ikke lenger escapet, og `lang`-steget gjør en bokstavelig `+` om til mellomrom.

### 2.9 NVA-roller som styrer upstream-kall

| Predikat | Definisjon (`C/utils/AccessUtils.java`) |
|---|---|
| `requesterIsUserAdministrator` | `MANAGE_OWN_AFFILIATION` eller intern backend |
| `clientIsCustomerAdministrator` | `MANAGE_CUSTOMERS` |
| `extractCristinInstitutionIdentifier` (`Utils.java:157-176`) | Gir `"any"` for intern backend. Ellers institusjonsnummeret til toppnivå-org-en fra tokenet, og bare for brukeradministratorer (ellers 403). |

---

## 3. cristin-project

### 3.1 U1: `GET /projects/{id}`

- **Klient:** `P/fetch/FetchCristinProjectApiClient.java`. URI-en lages med `CristinQuery.fromIdentifier(id)`.
- **Sti:** `id` valideres med `Integer.parseInt > 0`, og den rå strengen sendes.
- **Query:** NVA-query-parametere gir 400. Upstream får bare `lang`.
- **Eksempel:** `{API}/projects/12345?lang=en%2Cnb%2Cnn`

| Brukstilfelle | Klient | Betingelse |
|---|---|---|
| `GET /project/{id}`, første forsøk | D + bot | Alltid |
| `GET /project/{id}`, fallback | **ny** A-klient + bot | Upstream svarte 401 og brukeren har `MANAGE_OWN_RESOURCES` (ellers 403). Svaret returneres bare hvis `creator.cristin_person_id` er brukerens Cristin-id. |
| Sjekk før PATCH | A + bot (samme klient som PATCH) | Brukeren mangler `MANAGE_ALL_PROJECTS`. Krever `MANAGE_OWN_RESOURCES`, og brukeren må være creator eller ha rollen `PRO_MANAGER`. Kjøres før payload-validering. |
| Berikelse etter søk (U2/U3) | D + bot, async, ett kall per treff | Alltid |

- **Respons:** `CristinProject`, se 3.6. Den må bestå `hasEnrichedContent()`, det vil si ha `cristin_project_id`, en ikke-tom `title`, `start_date` og `coordinating_institution`. Ellers svarer NVA 502.
- **Response-headere:** ingen.

### 3.2 U2 og U3: søk (`GET /projects` og `GET /projects/facets`)

**Kode:**
- Klienter: `P/query/QueryCristinProjectApiClient.java` (v1), `P/query/version/facet/QueryProjectWithFacetsClient.java` (facets) og `P/query/organization/QueryCristinOrganizationProjectApiClient.java` (org).
- Bygging: `P/common/QueryBuilderProject.java`, `ParameterKeyProject.java`, `QueryProject.java`.

**Navnematching:**
- Et innkommende parameternavn matches mot **enten Cristin-navnet eller NVA-navnet** (`ParameterKeyProject.keyFromString`, `:228-235`).
- Ukjente navn gir 400.
- Verdien må matche nøkkelens regex i sin helhet (`String.matches`).
- Nøkler merket DECODE URL-dekodes først.

#### Parametermatrise

Kodingen er NONE der ikke annet står.

| NVA-navn | Cristin-navn | Regex | Transformasjon / merknad |
|---|---|---|---|
| `query` | `title` **eller** `project_code` | `.+` | Positivt heltall blir sendt som `project_code`, alt annet som `title` (`grantOrTitleKey`, QBP:398) |
| `title` | `title` | `.+` | Vinner over `name` når navnet er `title` |
| `name` | `title` | `PATTERN_IS_TITLE` | Strengere tegnsett |
| `grantId` | `project_code` | number | |
| `organization` | `parent_unit_id` | `PATTERN_IS_URL` | DECODE. Bare siste stisegment i URL-en sendes (`QueryProject.java:42`). Sendes `parent_unit_id=185.90.0.0` direkte som query-parameter, feiler URL-regexen og gir 400. |
| sti `{identifier}` (kun `/organization/{id}/projects`) | `parent_unit_id` | unit-id-regex | DECODE. Et rent heltall eller annen tekst gir 400. |
| `depth` | `levels` | number | |
| `language` / `lang` | – | `PATTERN_IS_LANGUAGE` | **Droppes** og logges bare. `lang=en,nb,nn` sendes alltid. |
| `status` | `status` | `PATTERN_IS_STATUS` | DECODE. `ProjectStatus.valueOf(upper)`: NOTSTARTED → `not started`, ACTIVE → `active`, CONCLUDED → `concluded`. Gyldig input er `active`, `concluded` og `notstarted`, uavhengig av store/små bokstaver. `not started` med mellomrom passerer regexen, men feiler i `valueOf` og gir 400. |
| `page` | `page` | number | Standard `1` |
| `results` | `per_page` | number | Standard `5`, ingen øvre grense |
| `sort` | `sort` | `.+` | Ingen enum. Dokumentasjonen nevner `start_date` og `end_date`, tester bruker `start_date` og `start_date desc`. Cristin avviser ugyldige verdier. |
| `creator` | `creator` | number | |
| `cristinId` | `id` | number | |
| `multiple` | `multiple` | `.+` | |
| `biobank` | `biobank` | `.+` | flerverdi |
| `keyword` | `keyword` | `.+` | flerverdi |
| `participant` / `participantFacet` | `participant` | `.+` | flerverdi, de to navnene slås sammen |
| `category` / `categoryFacet` | `category` | `.+` | flerverdi, de to navnene slås sammen |
| `funding_source` / `fundingSourceFacet` | `funding_source` | `.+` | flerverdi, de to navnene slås sammen |
| `funding` | `funding` | `.+` | format `KODE:prosjektkode` |
| `institution` | `institution` | `.+` | |
| `project_manager` | `project_manager` | `.+` | |
| `unit` | `unit` | `.+` | |
| `user` | `user` | `.+` | |
| `approval_reference_id` | `approval_reference_id` | `.+` | |
| `approved_by` | `approved_by` | `.+` | |
| `modified_since` | `modified_since` | `PATTERN_IS_DATE` | |
| `sectorFacet` | `sector` | `.+` | **bare U3** |
| `coordinatingFacet` | `facet_coordinating` | `.+` | bare U3 |
| `responsibleFacet` | `facet_responsible` | `.+` | bare U3 |
| `healthProjectFacet` | `facet_health` | `.+` | bare U3 |
| `participantOrgFacet` | `facet_participating` | `.+` | bare U3 |

#### Flerverdi og droppede parametere

**Flerverdi:**
- Gjelder `biobank`, `keyword`, `participant`, `category` og `funding_source`.
- Verdiene splittes på `,|%2C`, og nøkkelen gjentas for hver verdi.
- Gjentas NVA-parameteren, bygges det også en kommaliste.
- Andre parametere beholder kommaet som én `%2C`-kodet verdi.

**Tas imot, men droppes:**
- `language` / `lang`.
- I U2 (v1) droppes alle rene facet-parametere: de lagres, men `toURI` legger dem aldri på.

**Kant-tilfelle:** `organization=<URL>` som query-parameter sammen med sti-id på `/organization/{id}/projects` gir to `parent_unit_id`. Det vil trolig gi duplicate-key og 500 (ikke testet).

#### Response

- **U2:** `CristinProject[]`, der bare `cristin_project_id` leses.
- **U3:** `{ data: CristinProject[], facets: Map<String, CristinFacet[]> }`, der bare `data[].cristin_project_id` leses fra `data`.
- **Berikelse (N+1):** ett async U1-kall per treff. Bare 200-svar som består `hasEnrichedContent` beholdes. Feiler berikelsen, brukes søketreffet hvis det består `hasEnrichedContent`.
- **Response-headere:** `x-total-count` og `link`. U3 bygger lenkene med facet-parametrene lagt til.

#### Facet-nøkler i responsen (`C/cristin/model/query/CristinFacetKey.java`)

| Cristin | NVA |
|---|---|
| `institution_idfacet` | `organizationFacet` |
| `sector_idfacet` | `sectorFacet` |
| `institution_coordinating_idfacet` | `coordinatingFacet` |
| `institution_responsible_idfacet` | `responsibleFacet` |
| `category_idfacet` | `categoryFacet` |
| `health_project_type_idfacet` | `healthProjectFacet` |
| `person_idfacet` | `participantFacet` |
| `person_institution_idfacet` | `participantOrgFacet` |
| `funding_source_idfacet` | `fundingSourceFacet` |

Andre nøkler droppes uten melding. Hver facet-oppføring har ett av disse formatene:

| Format | Felter |
|---|---|
| `CristinCodeFacet` | `code`, `name{}`, `count` |
| `CristinInstitutionFacet` | `cristin_institution_id`, `institution_name{}`, `count` |
| `CristinUnitFacet` | `cristin_unit_id`, `unit_name{}`, `count` |
| `CristinPersonFacet` | `cristin_person_id`, `name`, `count` |

#### Eksempel-URL-er

| Variant | URL |
|---|---|
| tittel | `{API}/projects?page=1&per_page=5&title=reindeer&lang=en%2Cnb%2Cnn` |
| `query=1234567` | `{API}/projects?page=1&per_page=5&project_code=1234567&lang=en%2Cnb%2Cnn` |
| status + sort | `{API}/projects?page=1&per_page=5&sort=start_date%20desc&status=not%20started&title=x&lang=en%2Cnb%2Cnn` |
| organisasjon | `{API}/projects?levels=2&page=1&parent_unit_id=185.90.0.0&per_page=5&lang=en%2Cnb%2Cnn` |
| flerverdi | `{API}/projects?approval_reference_id=2017%2F1593&approved_by=REK&biobank=533895&funding_source=NFR&institution=uib&keyword=nature&levels=7&page=1&participant=St&per_page=5&project_manager=st&sort=start_date&unit=184.12.60.0&lang=en%2Cnb%2Cnn` |
| uten parametere | `{API}/projects?page=1&per_page=5&lang=en%2Cnb%2Cnn` |
| facets | `{API}/projects/facets?category=PHD&page=1&per_page=5&title=x&facet_coordinating=185.0.0.0&sector=UNI&lang=en%2Cnb%2Cnn` |

### 3.3 U4: `GET /projects/categories`

- **Klient:** `P/category/CategoryApiClient.java`, D + bot, bare `lang`.
- **Respons:** `CristinTypedLabel[]`, der både `code` og `name{lang:text}` brukes.
- **Headere:** `x-total-count` og `link`, med dummy-parametere `page=1` og `results=2000`.

### 3.4 U5: `POST /projects`

- **Klient og headere:** `P/create/CreateCristinProjectApiClient.java`. Klient A med `Content-Type: application/json; charset=utf-8` og bot-header. Verken `lang` eller `Cristin-Representing-Institution` sendes.
- **Tilgang:** krever `MANAGE_OWN_RESOURCES`, ellers 403 uten upstream-kall.
- **Body:** `CristinProject` bygget av `CristinProjectBuilder`. Feltene `null` og tomme verdier droppes.

| Felt | Innhold |
|---|---|
| `main_language` | ISO 639-1 |
| `title` | `{lang: tekst}`, hovedtittel og alternative titler |
| `status` | |
| `start_date`, `end_date` | ISO-instant med millisekunder |
| `academic_summary`, `popular_scientific_summary`, `method`, `equipment` | språk-map |
| `project_funding_sources[]` | `{funding_source_code, project_code, funding_source_name}` |
| `participants[]` | `{cristin_person_id, first_name, surname, url, email, phone, roles[{role_code, institution{cristin_institution_id, institution_name, url} \| unit{cristin_unit_id}}]}` |
| `coordinating_institution`, `institutions_responsible_for_research[]` | `{unit{cristin_unit_id}}` eller `{institution{...}}` |
| `health_project_type`, `health_project_type_name`, `clinical_trial_phase` | |
| `external_sources[]` | `{source_short_name, source_reference_id}` |
| `approvals[]` | `{approved_date, approved_by, approval_status, application_code, approval_reference_id, approved_by_name}` |
| `keywords[]`, `project_categories[]` | `{code}`, der `name` er null |
| `related_projects[]` | **fulle** Cristin-URI-er `{API}/projects/{id}` |
| `contact_info` | `{contact_person, institution, email, phone}` |
| `exempt_from_public_disclosure` | |
| `creator` | Bygget fra JWT: `cristin_person_id`, `url` og `roles[{role_code: PRO_CREATOR, unit\|institution}]` |
| `external_url` | |

Disse feltene sendes aldri: `publishable`, `published`, `created`, `last_modified`, `total_funding_amount`, `identified_cristin_person`.

**Respons:** full `CristinProject`, som mappes som i 3.6. NVA svarer 201.

### 3.5 U6: `PATCH /projects/{id}`

- **Klient og headere:** `P/update/UpdateCristinProjectApiClient.java`. Klient A med `Content-Type: application/merge-patch+json` og bot-header. Verken `lang` eller institusjonsheader sendes.
- **Tilgang:** `MANAGE_ALL_PROJECTS` hopper over sjekken før PATCH. Ellers kjøres U1 først, se 3.1.
- **Body:** et `ObjectNode` bygget i `CristinProjectPatchJsonCreator`. Bare NVA-felter som er med i input blir sendt.

| NVA | Cristin |
|---|---|
| `language` + `title` + `alternativeTitles` | `main_language`, `title{lang: tekst}` |
| `coordinatingInstitution` | `coordinating_institution` |
| `contributors` | `participants[]` |
| `startDate` / `endDate` | `start_date` / `end_date` (millisekunder) |
| `funding` | `project_funding_sources[{funding_source_code, project_code}]` eller null |
| `keywords` | `keywords[{code}]` |
| `projectCategories` | `project_categories[{code}]` |
| `relatedProjects` | `related_projects` som **rene id-er** eller null, mens POST sender fulle URI-er |
| `academicSummary`, `popularScientificSummary`, `method`, `equipment` | samme navn i snake_case |
| `contactInfo` | `contact_info{contact_person, institution, email, phone}`, der hvert felt kan settes til null |
| `institutionsResponsibleForResearch` | `institutions_responsible_for_research[]` eller null |
| `webPage` | `external_url` |

PATCH kan ikke endre: `status`, helsefelter, `approvals`, `external_sources`, `exempt_from_public_disclosure`, `creator`, `total_funding_amount`.

**Respons:** bare statuskoden sjekkes. NVA svarer 204.

### 3.6 Responsmodellen `CristinProject`

Alle feltene i `CristinProject` brukes i mappingen til NVA (`P/model/nva/NvaProjectBuilder.java`):

`cristin_project_id`, `publishable`, `published`, `title`, `main_language`, `start_date`, `end_date`, `status`, `created{source_short_name, date}`, `last_modified{...}`, `coordinating_institution` (unit foretrekkes framfor institution), `project_funding_sources[]`, `contact_info`, `total_funding_amount{currency_code, amount}`, `participants[]`, `academic_summary`, `popular_scientific_summary`, `method`, `equipment`, `project_categories[]{code, name}`, `keywords[]{code, name}`, `external_sources[]`, `related_projects[]` (siste stisegment blir NVA-URI), `institutions_responsible_for_research[]`, `health_project_type`, `health_project_type_name`, `clinical_trial_phase`, `approvals[]`, `exempt_from_public_disclosure`, `creator` (rollekoder tvinges til `PRO_CREATOR`), `external_url`.

Nøstede typer (`C/cristin/model/`):

| Klasse | Brukte felter | Deserialisert, men ubrukt |
|---|---|---|
| `CristinPerson` (deltaker) | `cristin_person_id` (bare hvis `identified_cristin_person` er null eller true), `first_name`, `surname`, `email`, `phone`, `roles`, `identified_cristin_person` | `url` |
| `CristinRole` | `role_code`, `institution`, `unit` | – |
| `CristinInstitution` | `cristin_institution_id`, `institution_name` | `url` |
| `CristinUnit` | `cristin_unit_id`, `unit_name` | `url` |

### 3.7 Enumerasjoner i prosjektmodulen

| Domene | NVA → Cristin |
|---|---|
| Status | NOTSTARTED → `not started`, ACTIVE → `active`, CONCLUDED → `concluded` |
| Rolle | ProjectManager → `PRO_MANAGER`, ProjectParticipant → `PRO_PARTICIPANT`, ProjectCreator → `PRO_CREATOR`, LocalProjectManager → `PRO_LMANAGER` |
| Helseprosjekttype | Drugstudy → `DRUGSTUDY`, Otherclin → `OTHERCLIN`, Otherstudy → `OTHERSTUDY` |
| Klinisk fase | PhaseI–IV → `1`–`4` |
| Godkjenningsmyndighet | RegionalEthicalCommittees → `REK`, NorwegianAnimalResearchAuthority → `NARA`, NorwegianDataProtectionAuthority → `NDPA`, NorwegianMedicinesAgency → `NMA`, NorwegianDirectorateOfHealth → `DIRHEALTH` |
| Godkjenningsstatus | NotApplied → `NOTAPPLIED`, Applied → `APPLIED`, Approved → `APPROVED`, Declined → `DECLINED`, Rejected → `REJECTION` |
| Søknadskode | EthicalApproval → `ETICHAPPR`, BioBank → `BIOBANK`, DrugTrial → `DRUGTRIAL`, BioTechnology → `BIOTECHN`, MedicalEquipment → `MEDEQUIP`, AnimalTesting → `TESTANIMAL`, SensitiveInformation → `SENSINFO` |
| Sort | ingen enum (fritekst) |

---

## 4. cristin-person

### 4.1 U7 og U8: personsøk (`GET /persons`, `GET /persons/facets`)

**Kode:** handler `PE/query/QueryCristinPersonHandler.java`, klienter `PE/client/CristinPersonApiClient.java` og `PE/query/version/facet/QueryPersonWithFacetsClient.java`, query-bygger `PE/client/CristinPersonQuery.java`.

Tillatte NVA-nøkler er `name`, `organization`, `page`, `results`, `verified`, `sectorFacet`, `organizationFacet` og `sort`. Andre nøkler gir 400.

| NVA | Cristin | Validering | Standard | Transformasjon |
|---|---|---|---|---|
| `page` | `page` | Positivt heltall, ellers 400 | `1` | – |
| `results` | `per_page` | Positivt heltall, ingen øvre grense | `5` | – |
| `name` | `name` | ingen | – | `URLDecoder.decode` i tillegg til API Gateway-dekodingen, så `+` blir mellomrom |
| `organization` | `institution` | ingen (fritekst, f.eks. `185` eller et akronym) | – | URL-dekodes |
| `verified` | `verified` | `true`/`false` uavhengig av store/små bokstaver. **Andre verdier droppes uten melding.** | – | – |
| `sort` | `sort` | ingen, og ingen enum | – | – |
| `organizationFacet` | `institution` (gjentas) | – | – | **Bare U8.** Splittes på komma. Kan gi flere `institution` sammen med `organization`. |
| `sectorFacet` | `sector` (gjentas) | – | – | **Bare U8.** Splittes på komma. |

**Klient og auth:**

| Kaller | U7 (v1) | U8 (facets) |
|---|---|---|
| Anonym eller uten rettigheter | D + bot | D + bot |
| `MANAGE_OWN_AFFILIATION`, intern backend eller `MANAGE_CUSTOMERS` | D + bot + **B**, både på søket og berikelsen | D + bot (alltid anonym) |

- **Respons U7:** `CristinPerson[]`, der bare `cristin_person_id` leses.
- **Respons U8:** `{data, facets}`. Fra `data` leses bare `cristin_person_id`. Facet-mappingen er den samme som i 3.2.
- **Berikelse (N+1):** async `GET /persons/{id}?lang=...` per treff, med samme auth som søket. Feiler et kall, brukes det tynne søketreffet.
- **Headere:** `x-total-count` og `link`.

**Eksempler:**
- `{API}/persons?per_page=5&name=Ola%20Nordmann&page=1&institution=185&verified=true&sort=name&lang=en%2Cnb%2Cnn`
- `{API}/persons/facets?page=1&per_page=5&name=Ola&sector=UC&institution=185&institution=194&lang=en%2Cnb%2Cnn`

### 4.2 U9: hent person (`GET /persons/{id}`, `GET /persons/ORCID:{orcid}`)

**Kode:** handler `PE/fetch/FetchCristinPersonHandler.java`.

| Sti-id | Gyldig når | Upstream |
|---|---|---|
| ORCID (sjekkes først) | ORCID-regex | `/persons/ORCID:{orcid}` |
| Cristin-id | Positivt heltall | `/persons/{id}` |

- **Query:** NVA-query-parametere gir 400. Upstream får bare `lang`.
- **Auth:**
  - Anonym: D + bot, med anonym mapping. En reservert person returneres bare med `id`.
  - Brukeradministrator, intern backend eller `MANAGE_CUSTOMERS`: D + bot + B, med autorisert mapping.

**Responsmodell `CristinPerson`** (`PE/model/cristin/CristinPerson.java`):

| Cristin-felt | Bruk |
|---|---|
| `cristin_person_id` | `id` og CristinIdentifier |
| `orcid.id` | ORCID-identifikator |
| `first_name`, `surname`, `first_name_preferred`, `surname_preferred` | `names` |
| `tel`, `email`, `web_page` | `contactDetails` |
| `picture_url` | Bare om den finnes, gir `image` = `.../person/{id}/picture` |
| `affiliations[].active`, `.position`, `.unit.cristin_unit_id` | `affiliations` |
| `reserved` | Anonym kaller + true gir bare `id`. Autorisert kaller får feltet `reserved`. |
| `norwegian_national_id` | Bare autorisert |
| `detailed_affiliations[].id`, `.position.code`, `.start_date`, `.end_date`, `.fte_percentage`, `.affiliation.unit.cristin_unit_id` (ellers `.institution.cristin_institution_id`) | Bare autorisert, gir `employments` |
| `identified_cristin_person` | `verified`. Settes også til true hvis NVI `verified_by` har en numerisk id. |
| `keywords[]{code, name}`, `countries[]{code, name}` | `keywords`, `countries` |
| `background`, `place`, `collaboration` | samme navn |
| `person_nvi.verified_by{cristin_person_id, first_name, surname}`, `.verified_at.unit{cristin_unit_id, unit_name}`, `.verified_at.institution{acronym, country, institution_name, corresponding_unit.cristin_unit_id}`, `.verified_date` | `nvi` |
| `awards[]{title, year, type{code,name}, distribution{code,name}, affiliation}` | `awards` |

**Deserialisert, men ubrukt:** `affiliations[].unit.unit_name` og `.url`, `detailed_affiliations[].active`, `.url`, `.position.name` og `.position.enabled`, organisasjonsnavn i `detailed_affiliations`, `person_nvi.verified_by.url`, og alle `*.url` på unit og institution.

### 4.3 U7: person fra fødselsnummer (`POST /person/identityNumber/`)

- **Input:** `{"type":"NationalIdentificationNumber","value":<nin>}`, validert med `FodselsnummerValidator`. NVA-query-parametere gir 400.
- **Tilgang:** brukeradministrator, intern backend eller `MANAGE_CUSTOMERS`, ellers 403.
- **Kall 1:** `GET /persons?national_id={nin}&lang=...`, uten `page` og `per_page`. Et tomt svar gir 404.
- **Kall 2:** `GET /persons/{første treff sin cristin_person_id}?lang=...`, med autorisert mapping.
- **Auth:** A + bot på begge kallene, uten B.
- Fødselsnummeret maskeres i logger.

### 4.4 U7: personer i en organisasjon (`GET /organization/{id}/persons`)

**Kode:** `PE/query/organization/ListCristinOrganizationPersonsHandler.java`, `CristinOrganizationPersonsClient.java`.

| NVA | Cristin | Validering | Standard |
|---|---|---|---|
| sti `id` | `parent_unit_id` | Bare unit-id-regex. Et rent institusjonsnummer gir 400. | – |
| `page` | `page` | Positivt heltall | `1` |
| `results` | `per_page` | Positivt heltall | `5` |
| `name` | `name` | ingen, og **ingen URL-dekoding** (i motsetning til 4.1) | – |
| `sort` | `sort` | ingen | – |

- Andre nøkler gir 400.
- **Auth:** som U7 i 4.1, altså B for administratorer både på søket og berikelsen.
- **Eksempel:** `{API}/persons?parent_unit_id=194.63.10.0&page=1&per_page=5&name=Ola&lang=en%2Cnb%2Cnn`

### 4.5 U10: opprett person (`POST /persons`)

**Kode:** `PE/create/CreateCristinPersonHandler.java`, `CreateCristinPersonApiClient.java`, `PE/model/nva/adapter/PersonToCristinFormat.java`.

- **Headere:** klient A, `Content-Type: application/json; charset=utf-8` og bot. `Cristin-Representing-Institution` sendes **bare** når payloaden har `employments` eller `nvi`, og da kreves det også at kalleren er brukeradministrator.
- **Tilgang:** mangler fødselsnummer i payloaden, eller er det ulikt kallerens eget, må kalleren være brukeradministrator.
- **Validering:**
  - `FirstName` og `LastName` er påkrevd, og det kan være maks ett fødselsnummer med gyldig sjekksum.
  - Hver employment krever en stillingskode etter `#` i `type` og en `organization` der siste stisegment er en unit-id eller et positivt heltall.
  - `nvi` krever `verifiedBy.id` og `verifiedAt.id`.
- **Body** (`NON_ABSENT`, så tomme samlinger blir likevel sendt):
  - Navn og identitet: `first_name`, `surname`, `first_name_preferred`, `surname_preferred`, `norwegian_national_id`, `reserved`.
  - Kontaktinfo: `tel`, `email`, `web_page`.
  - Koder: `keywords[{code}]` og `countries[{code}]`, med store bokstaver.
  - `background`, `place`, `collaboration`.
  - `detailed_affiliations[{affiliation:{unit:{cristin_unit_id}} | {institution:{cristin_institution_id}}, position:{code, enabled:false}, start_date, end_date, fte_percentage}]`.
  - `person_nvi{verified_by:{cristin_person_id}, verified_at:{unit:{cristin_unit_id}}}`.
  - Sendes alltid, også tomme: `affiliations: []` og `awards: []`.
- **Ikke mappet:** awards fra NVA-payloaden mappes ikke ved opprettelse.
- **Respons:** `CristinPerson` med autorisert mapping. NVA svarer 201.

### 4.6 U11: oppdater person (`PATCH /persons/{id}`)

**Kode:** `PE/update/UpdateCristinPersonHandler.java`, `CristinPersonPatchJsonCreator.java`, `PersonPatchFieldFilter.java`, `PersonPatchValidator.java`.

- **Headere:** klient A, `Content-Type: application/merge-patch+json` og bot. Institusjonsheaderen sendes bare hvis patchen inneholder `detailed_affiliations` eller `person_nvi`. Verdien er `any` for intern backend, ellers institusjonsnummeret.

| NVA | Cristin | Hvem |
|---|---|---|
| `orcid` | `orcid{id}` | admin og personen selv |
| `firstName`, `lastName` | `first_name`, `surname` (kan ikke settes til null) | admin |
| `preferredFirstName`, `preferredLastName` | `first_name_preferred`, `surname_preferred` | admin og personen selv |
| `reserved` | `reserved` (bare boolean, og sendes bare når den er `true`) | admin |
| `employments` | `detailed_affiliations` | admin |
| `keywords[].type` | `keywords[{code}]` (**ikke** store bokstaver) | admin og personen selv |
| `background`, `place`, `collaboration` | samme navn | admin og personen selv |
| `nvi` / null | `person_nvi` / null. Droppes hvis `verifiedAt` ikke er kallerens institusjon. | admin |
| `contactDetails.telephone`, `.email`, `.webPage` | `tel`, `email`, `web_page` (null er lov) | admin og personen selv |
| `countries[].type` | `countries[{code}]` (store bokstaver) | admin og personen selv |
| `awards` / null | `awards[{title, year, type{code}, distribution{code}, affiliation{unit\|institution id}}]` | admin og personen selv |

- **«Personen selv»** betyr at Cristin-id-en i tokenet er lik sti-id-en. Er den ikke det, gir det 400. Alle andre kallere får 403.
- **Validering:** `orcid` må matche ORCID-regexen. `nvi.verifiedAt.id` må slutte på en unit-id, og `verifiedBy.id` på et positivt heltall. Tom Cristin-output gir 400.
- **Respons:** body ignoreres. NVA svarer 204.

### 4.7 U12–U15: ansettelser (`/persons/{id}/affiliations`)

Klient A brukes for alle. Sti-id-ene `{id}` og `{eid}` må være positive heltall.

| Kall | Headere | Tilgang | Body | Respons |
|---|---|---|---|---|
| U12 `GET .../affiliations?lang=...` | A + bot | brukeradministrator | – | `CristinPersonEmployment[]`: `id`, `affiliation`, `position.code`, `start_date`, `end_date`, `fte_percentage` (`active` og `url` er ubrukt). `size` er lengden på listen, og headere leses ikke. |
| U13 `POST .../affiliations` | A + JSON + bot + institusjon (`any` ved `MANAGE_CUSTOMERS`, ellers institusjonsnummer) | brukeradministrator eller `MANAGE_CUSTOMERS` | `{affiliation, position:{code, enabled:false}, start_date, end_date, fte_percentage}` | `CristinPersonEmployment`, NVA svarer 201 |
| U14 `PATCH .../affiliations/{eid}` | A + merge-patch + bot + institusjon | brukeradministrator | se tabellen under | ignoreres, NVA svarer 204 |
| U15 `DELETE .../affiliations/{eid}` | A + institusjon. **Verken bot-header eller `lang`** (`DeletePersonEmploymentClient.java:72-73`) | brukeradministrator | – | bare status: 400 gir 400, 404 gir 404 |

Feltmapping for POST og PATCH:

| NVA | Cristin |
|---|---|
| `type` (tekst etter siste `#`) | `position.code` |
| `organization` | `affiliation.unit.cristin_unit_id` hvis siste stisegment matcher unit-id-regexen. Ellers `affiliation.institution.cristin_institution_id` hvis det er et positivt heltall. Ellers 400. |
| `startDate`, `endDate` | `start_date`, `end_date` (`...T00:00:00.000Z`). Ved PATCH kan `startDate` ikke være null. |
| `fullTimeEquivalentPercentage` | `fte_percentage` (double) |

### 4.8 U16 og U17: personens institusjonsinfo (`/persons/{id}/institutions/{inst}`)

- **Sti:** `{inst}` er NVA-`orgId` uten unit-delen (`194.63.0.0` blir `194`), og den sjekkes som positivt heltall.
- **Tilgang:** bare brukeradministrator. NVA-query-parametere gir 400.
- **GET:** A + bot + `lang`. Responsen `{email, phone}` brukes i sin helhet.
- **PATCH:** A + merge-patch + bot, **uten** institusjonsheader. Body er `{email?, phone?}`: bare nøkler som er med i input skrives, og en eksplisitt `null` sendes videre. Mangler begge nøklene, svarer NVA 400. Responsen ignoreres.

### 4.9 U18 og U19: bilde (`/persons/{id}/picture`)

| Kall | Headere | Merknad |
|---|---|---|
| U18 GET | D + bot + **B**, uten `lang` og uten retry | Rå bytes blir base64-kodet til `{base64Data}`. Content-Type sjekkes ikke. |
| U19 PUT | A + bot + `Content-Type: image/jpeg`, uten `lang` | Bare personen selv (ellers 403, og manglende id gir 401). Er `base64Data` null, sendes en tom body (sletting). Ellers må verdien være gyldig base64 og et bilde som `ImageIO` kan lese, ellers 400. Upstream 400 og 415 gir NVA 400. |

### 4.10 U20–U22: referansedata

| Kall | Headere | NVA-parametere | Respons |
|---|---|---|---|
| U20 `GET /persons/affiliations/positions?lang=...` | D + bot | `active=true\|false` (ellers 400) filtrerer på `enabled` **lokalt** og sendes ikke upstream. Andre parametere ignoreres. | `CristinPositionCode[]`: `code`, `name`, `enabled` |
| U21 `GET /countries?lang=...` | D + bot | ignoreres | `CristinCountry[]`: `code`, `code_alpha3`, `name` |
| U22 `GET /persons/orcid?lang=...` | D + bot + **B** | ignoreres | `CristinPersonOrcid[]`: `cristin_person_id`, `orcid.id` |

### 4.11 Enumerasjoner i personmodulen

| Domene | Verdier |
|---|---|
| Prisens type | `Research` ↔ `RESEARCH`, `ResearchDissemination` ↔ `DISSEMINATION`. Ukjente verdier blir null. |
| Prisens distribusjon | `International` ↔ `INTERNATIONAL`, `National` ↔ `NATIONAL`, `Internal` ↔ `INTERNAL` |
| `verified` | `true`, `false` |
| Stillingskoder | Ingen fast liste. Fritekst etter `#` i `type`-URI-en. |
| `sort` | Ingen, sendes videre som den er |

---

## 5. cristin-organization

### 5.1 NVA-input og validering

| Endepunkt | Parameter | Regel |
|---|---|---|
| `/organization/{identifier}` | sti | Unit-id-regex, ellers 400. URL-dekodes ikke. |
| | `depth` | `top` (standard), `full` eller `none`. Andre verdier gir 400. |
| | andre query-parametere | ignoreres uten melding |
| `/organization` | tillatte nøkler | `query`, `page`, `results`, `depth`, `version`, `sort`, `fullTree`. Andre gir 400. |
| | `page` | positivt heltall, standard `1` |
| | `results` | positivt heltall, standard **`5`** (swagger sier 10) |
| | `sort` | Valideres ikke. Swagger-enum: `country`, `nameEn`, `nameNb`, `nameNn`, eventuelt med ` asc` eller ` desc`. |
| | `fullTree` | Standard `false`. Bare `true` (uavhengig av store/små bokstaver) har effekt. |
| | `version` | tas imot, men **droppes** |

**Cache i API Gateway:**
- `GET /organization/{identifier}` caches i 3600 s, med nøkkel på sti, `depth` og `Accept`.
- `GET /organization` caches ikke.

### 5.2 Mapping fra NVA til Cristin (`O/common/QueryParamConverter.java`)

| NVA | Cristin | Transformasjon |
|---|---|---|
| `query` | `name` | bare hvis den finnes |
| `depth` | `levels` | `top` eller mangler gir `1`. `full` og `none` gir `32`. |
| `page` | `page` | – |
| `results` | `per_page` | – |
| `sort` | `sort` | Substring-erstatning: `nameNb` blir `name_nb`, `nameEn` blir `name_en` og `nameNn` blir `name_nn`. Resten sendes uendret. |
| `fullTree`, `version` | – | sendes ikke |

### 5.3 v2023-05-26 (standard)

| Kall | Når | Respons | Headere |
|---|---|---|---|
| `GET /units/{id}?lang=...` | alltid ved oppslag | `UnitDto` | – |
| `GET /units?parent_unit_id={id}&page=1&per_page=1000&lang=...` | oppslag med `depth` ≠ `none` | `UnitDto[]` | `x-total-count` via `MultiPageProcessor` |
| Samme kall med `page=2` | bare hvis `x-total-count` > 1000 | `UnitDto[]` | – |
| `GET /units?[name&]levels&page&per_page[&sort]&lang=...` | søk | `UnitDto[]` | `x-total-count`, `link` |
| Oppslagskallene over, for hvert søketreff | søk med `fullTree=true` | – | – |

**`MultiPageProcessor`:**
- Mangler `x-total-count`, tolkes den som `"0"`.
- Taket er 2000 subunits, siden side 3 aldri hentes.
- En ikke-numerisk header gir `NumberFormatException` og 500.

**Andre regler:**
- Med `depth=none` tvinges `hasPart` til null.
- Ved `fullTree` hoppes 404-treff over.

**Felter i `UnitDto`:**
- **Brukt:** `cristin_unit_id`, `unit_name`, `acronym`, `country`, `parent_unit.cristin_unit_id` og `parent_units[]`. Fra `parent_units[]` brukes `unit_name`, `acronym` og `country` til `partOf`.
- **Treet:** `subunits` på toppnivå erstattes av listen fra `parent_unit_id`-kallet. Treet bygges ut fra `parent_unit.cristin_unit_id`.
- **Ubrukt:** `institution` og `url`.

**Eksempler:**
- `{API}/units/185.15.2.10?lang=en%2Cnb%2Cnn`
- `{API}/units?parent_unit_id=185.15.2.10&page=1&per_page=1000&lang=en%2Cnb%2Cnn`
- `{API}/units?name=oslo&levels=1&page=1&per_page=5&sort=name_en%20asc&lang=en%2Cnb%2Cnn`

### 5.4 Legacy v1 (`Accept: ...; version=1`)

| Kall | Når | Respons | Felter brukt |
|---|---|---|---|
| `GET /units?id={id}&lang=...` | oppslag med `depth=none`. Krever nøyaktig ett treff, ellers 400. | `SubUnitDto[]` | `unit_name`, `acronym`, `country` |
| `GET /units/{id}?lang=...` | oppslag med `depth` = `top` eller `full` | `SubSubUnitDto` | `cristin_unit_id`, `unit_name`, `parent_unit.url`, `subunits[].url`, og på toppnivå `country` og `acronym` |
| `GET {parent_unit.url}?lang=...`, rekursivt oppover | etter kallet over | `SubSubUnitDto` | som over |
| `GET {subunits[].url}?lang=...`, rekursivt nedover (parallelt, uten dybdegrense) | etter kallet over | `SubSubUnitDto` | som over |
| `GET /units?[name]&levels&page&per_page[&sort]&lang=...`, deretter full rekursiv henting per treff | søk. `fullTree` ignoreres. | `SubUnitDto[]` | `X-Total-Count` via `getCount`. `Link` brukes ikke. |

---

## 6. cristin-funding

| NVA | Upstream | Merknad |
|---|---|---|
| `GET /funding-sources` | `GET /fundings/sources?lang=...` | Ingen andre parametere. Ukjente NVA-parametere droppes. |
| `GET /funding-sources/{identifier}` | **samme kall**, som henter hele listen | `identifier` dekodes med `URLDecoder` (UTF-8, så `+` blir mellomrom). Treff krever eksakt likhet med `code`, med skille mellom store og små bokstaver. Ingen treff gir 404. NVA-id-en kodes på nytt med `URLEncoder`. |

- **Headere:** D + bot. Ingen response-headere leses.
- **Cache:** 300 s i API Gateway.
- **Respons:** `CristinFundingSource[]` = `{code, name{lang:tekst}}`, der begge feltene brukes.

---

## 7. cristin-keyword

| Kall | Detaljer |
|---|---|
| U27 `GET /keywords?[name]&page&per_page&lang=...` | Tillatte NVA-nøkler er `query`, `page` og `results`. Andre gir 400. `query` sendes som `name`. `page` har standard `1` og `results` (sendt som `per_page`) har standard **`100`**. **Ugyldige verdier erstattes med standardverdien** i stedet for å gi 400. Leser `x-total-count` og `link`. Caches ikke. |
| U28 `GET /keywords/{id}?lang=...` | `id` sjekkes med `Integer.parseInt`, så `-5`, `+5` og `007` slipper gjennom selv om swagger sier `^[0-9]+$`. Den rå strengen sendes. Cachen er satt på `/keyword/{identifier}` mens swagger-stien er `/keyword/{id}`, så cachen gjelder kanskje ikke. |
| U29 `POST /keywords` | Krever `MANAGE_OWN_RESOURCES`. `labels.nb` og `labels.en` må være ikke-tomme. Body er `{"name": {...labels}}`, og `code` utelates. Headere: klient A, `Content-Type: application/json; charset=utf-8` og bot. Verken institusjonsheader eller `lang`. Ved upstream 400 tas body med i feilmeldingen. NVA svarer 201. |

**Respons:** `CristinTypedLabel` = `{code, name}`, der begge feltene brukes.

**Eksempel:** `{API}/keywords?name=climate&page=1&per_page=100&lang=en%2Cnb%2Cnn`

---

## 8. cristin-biobank

**Kall:** U30 `GET /biobanks/{identifier}?[status][&user][&page][&per_page][&sort]&lang=en,nb,nn` med D + bot.

- **Sti:** `identifier` må matche `[1-9]\d*`, så ledende null gir 400.
- **Query:** NVA-navnet matches mot Cristin-navnet eller NVA-navnet. Ukjente navn gir 400. Alle parameterne har én verdi, og **ingen standardverdier** brukes.

| NVA | Cristin | Regel |
|---|---|---|
| `lang` / `language` | – | **droppes**, men standard-`lang` sendes |
| `status` | `status` | `PATTERN_IS_STATUS`. Verdien URL-dekodes og kodes på nytt. |
| `user` | `user` | `.+` |
| `page` | `page` | `[1-9]\d*` |
| `results` / `per_page` | `per_page` | `[1-9]\d*` |
| `sort` | `sort` | `.+` |

- **Response-headere:** leses ikke.
- **Cache:** 300 s i API Gateway, med nøkkel **bare på sti-id-en**.
- **Eksempel:** `{API}/biobanks/1234567?status=NOT%20STARTED&lang=en%2Cnb%2Cnn`

**Responsmodell `CristinBiobank`:**
- **Brukt:**
  - id-er og navn: `cristin_biobank_id`, `biobank_id`, `type` (må være `GENERALBIOBANK` eller `SPECIFICBIOBANK`, ellers exception), `name`, `main_language`
  - datoer og status: `start_date`, `store_until_date`, `status`, `created{source_short_name, date}`, `last_modified{...}`
  - koblinger: `coordinating_institution.unit.cristin_unit_id`, `coordinator.cristin_person_id` (påkrevd, null gir NPE), `associated_project{cristin_project_id, title}`
  - lister: `external_sources[]`, `approvals[]{approved_date, approved_by, approval_status, application_code, approval_reference_id}`, `biobank_materials[]{material_code, material_name}`
- **Ubrukt:** `status_name`, `type_name`, `coordinating_institution.institution`, `coordinating_institution.unit.unit_name` og `.url`, `associated_project.url`, `approvals[].approved_by_name`, og alle `coordinator`-felter utenom `cristin_person_id`.

---

## 9. Mistenkelige funn

Disse er ikke verifisert med tester.

| Alvorlighet | Funn |
|---|---|
| MEDIUM | **Biobank-cachen kan gi feil svar.** `GET /biobank/{id}` sender `status`, `user`, `page`, `per_page` og `sort` upstream, men cache-nøkkelen er bare sti-id-en (verifisert i swagger, `cacheKeyParameters`). Svar for ulike query-parametere kan derfor serveres fra samme cache-oppføring i 300 s. Parameterne er ikke dokumentert i swagger, så de brukes trolig sjelden. |
| MEDIUM | **PATCH av prosjekt med null-verdier.** Når `keywords` eller `projectCategories` er JSON-null, kaller `CristinProjectPatchJsonCreator` `putNull` og caster deretter til `ArrayNode`. Det gir trolig en exception. I tillegg skrives null-verdien under NVA-navnet `projectCategories` i stedet for `project_categories`. |
| MEDIUM | **DELETE av ansettelse (U15)** sender ikke bot-filter-headeren. |
| MEDIUM | **`GET /person/orcid/`** bygger `orcid` med person-URL-malen, så verdien blir `.../person/{orcid}` i stedet for en ORCID-URI. |
| MEDIUM | **Biobank feiler med 500.** En `type` utenfor `GENERALBIOBANK`/`SPECIFICBIOBANK`, eller `coordinator` = null, kaster en exception som blir 500. |
| MEDIUM | **Prosjektstatus `not started`** med mellomrom passerer regexen, men gir 400 i `ProjectStatus.valueOf`. |
| MEDIUM | **nva-identity-service: affiliations kan forsvinne stille.** Begynner `institution.url` å redirecte, hoppes affiliationen over og det logges bare en warning (se 10.7). |
| LOW | **`related_projects`** sendes som fulle URI-er ved POST, men som rene id-er ved PATCH. |
| LOW | **Facet-parametere i v1-prosjektsøk** (`sectorFacet` med flere) tas imot, men sendes aldri upstream. |
| LOW | **Ugyldig `verified` i personsøk** droppes uten melding. Ugyldig `page`/`results` i keyword-søk erstattes med standardverdien. |
| LOW | **Id-validering** godtar `+5` og `007` (`Integer.parseInt`), og den rå strengen sendes upstream. |
| LOW | **ORCID-regexen** godtar en bokstavelig `\|` som siste tegn. |
| LOW | **Standard `results` for org-søk** er 5 i koden, men 10 i swagger. |
| LOW | **Facets-URI-en** bygges om etter dekoding, så `+`, `&` og `=` i verdier kan bli feil (se 2.8). |
| LOW | **Ubrukt validering:** `QueryCristinOrganizationProjectHandler.validateQueryParameterKeys` blir aldri kalt. |
| LOW | **Dobbel `parent_unit_id`** kan gi 500 ved `organization=<URL>` sammen med sti-id på `/organization/{id}/projects`. |
| LOW | **Swagger-mønstre og -enums håndheves ikke** av API Gateway (ingen request validator). Bare valideringen i handlerne gjelder. |
| LOW | **nva-identity-service: avhengig av feiltekst.** «Finnes allerede» ved opprettelse gjenkjennes delvis ved å lete etter teksten «already exists» i en 400-body. |
| LOW | **nva-identity-service: sekvensielle kall uten cache.** I4 gjøres sekvensielt per aktiv affiliation, med 4 s timeout per kall. |

---

## 10. nva-identity-service

Kilde: `nva-identity-service/user-creation/src/main/java/no/unit/nva/useraccessservice/usercreation/person/cristin/CristinPersonRegistry.java` og modellene i `.../person/cristin/model/`. Ingen andre klasser på `main` bygger Cristin-requests. Innholdet er hentet fra `nva-identity-service/cristin-upstream-contract.md` og er ikke verifisert på nytt her.

### 10.1 Felles oppførsel

| Egenskap | nva-identity-service | Til sammenligning: nva-cristin-service |
|---|---|---|
| Base-URL | `CRISTIN_API_URL` (samme verdier) | samme |
| Auth | `Authorization: Basic <username:password>` fra secret `CristinClientBasicAuth`, sendt **preemptivt** på alle kall | Ingen auth på de fleste lesekall. Challenge-basert (A) på skrivekall, preemptiv (B) bare for enkelte admin-kall (se 2.2). |
| Bot-filter | `BOT_FILTER_BYPASS_HEADER_NAME` / `BOT_FILTER_BYPASS_HEADER_VALUE` | `CRISTIN_BOT_FILTER_BYPASS_HEADER_NAME` / `..._VALUE` (andre navn på miljøvariablene) |
| Content-Type | `application/json` (bare POST) | `application/json; charset=utf-8` på POST |
| `lang` | sendes **ikke** | `lang=en,nb,nn` på alle JSON-GET |
| `Accept`, `Cristin-Representing-Institution` | sendes ikke | `Accept` sendes ikke, institusjonsheaderen sendes på enkelte person-kall |
| HTTP-versjon | HTTP/1.1 (pinnet siden NP-44746) | HTTP/1.1 |
| Redirects | **Følges ikke** | `Redirect.ALWAYS` |
| Timeouts | 2 s connect, 4 s per request | 15 s connect, ingen per request |
| Retry | ikke beskrevet | resilience4j, 3 forsøk |

### 10.2 Statuskoder

| Upstream | Resultat |
|---|---|
| 2xx | OK, body parses |
| 3xx | `IdentityServiceRedirectException` |
| 404 | `IdentityServiceNotFoundException` |
| 409, eller 400 med «already exists» i body | `IdentityServiceAlreadyExistsException` |
| 5xx | `IdentityServiceUpstreamInternalServerErrorException` |
| annet | `IdentityServiceException` (generisk) |
| timeout / IO-feil | `IdentityServiceUnavailableException` |
| body som ikke kan parses | `IdentityServiceUpstreamBodyParsingException` |

### 10.3 Endepunkter

| # | Kall | Path-param | Query-param | Body | Respons | Spesialhåndtering |
|---|---|---|---|---|---|---|
| I1 | `GET /persons/resolve` | – | `national_id` (alltid, eneste) | – | `CristinPerson` | 404 gir tom `Optional` (personen finnes ikke). Fødselsnummeret maskeres i logger. |
| I2 | `GET /persons/{id}` | `cristinPersonId` | – | – | `CristinPerson` | Alle feil gir tom `Optional`. |
| I3 | `POST /persons` | – | – | `CristinPerson`, fast form (10.4) | `CristinPerson` | 409, eller 400 med «already exists», gir `IdentityServiceAlreadyExistsException`. |
| I4 | `GET {affiliations[].institution.url}` | Hele URL-en kommer fra Cristin | Det Cristin gir; ingenting legges til | – | `CristinInstitution` | Redirect gjør at affiliationen hoppes over (logges som warning). |

Det finnes **4 request-former** totalt, uten paginering, språk- eller feltfiltrering og uten valgfrie query-parametere.

Merk forskjellen fra nva-cristin-service: identity-service slår opp person på fødselsnummer med `/persons/resolve?national_id=`, mens nva-cristin-service bruker søk med `/persons?national_id=` fulgt av `/persons/{id}` (4.3).

### 10.4 Request-body for `POST /persons`

```json
{
  "cristin_person_id": null,
  "first_name": "<fornavn>",
  "surname": "<etternavn>",
  "affiliations": [],
  "norwegian_national_id": "<fnr>"
}
```

`affiliations` er alltid tom og `cristin_person_id` alltid `null`. `norwegian_national_id` utelates hvis den er null.

### 10.5 Responsfelter

**Person (I1–I3):**

| Felt | Brukes | Merknad |
|---|---|---|
| `cristin_person_id` | ja | Påkrevd. Blank verdi gir `IdentityServiceMissingRequiredFieldsException`. |
| `first_name` | ja | påkrevd |
| `surname` | ja | påkrevd |
| `affiliations[]` | ja | Valgfri. Null blir tom liste. |
| `affiliations[].active` | ja | Bare aktive brukes |
| `affiliations[].institution.url` | ja | Brukes som URL for I4 |
| `affiliations[].unit.cristin_unit_id` | ja | Blir enhets-id |
| `affiliations[].institution.cristin_institution_id` | nei | mappet, men aldri lest |
| `affiliations[].unit.url` | nei | mappet, men aldri lest |
| `norwegian_national_id` | nei | mappet, men aldri lest fra respons |

**Institusjon (I4):**

| Felt | Brukes | Merknad |
|---|---|---|
| `corresponding_unit.cristin_unit_id` | ja | Blir institusjons-id |
| `corresponding_unit.url` | nei | mappet, men aldri lest |

**Mapping til intern `Person`:**

| Intern verdi | Kilde |
|---|---|
| `id` | `https://{API_DOMAIN}/cristin/person/{cristin_person_id}` |
| `identifier` | `cristin_person_id` |
| fornavn / etternavn | `first_name` / `surname` |
| affiliation-institusjon | `https://{API_DOMAIN}/cristin/organization/{corresponding_unit.cristin_unit_id}` |
| affiliation-enheter | `https://{API_DOMAIN}/cristin/organization/{unit.cristin_unit_id}`, gruppert per institusjon |

### 10.6 Kallmønster per innlogging

| Scenario | Kall |
|---|---|
| Eksisterende person | I1 + N × I4 (N = antall aktive affiliations) |
| Ny person | I1 (404) + I3 + N × I4 |
| Oppslag på id | I2 + N × I4 |

I4 caches ikke og gjøres sekvensielt, én gang per aktiv affiliation, med 4 s timeout per kall.

### 10.7 Hva som kan brekke

Den reelle kontrakten er smal: 4 felter på personen, 3 per affiliation og 1 på institusjonen.

- Endret navn eller type på et av feltene som leses (10.5).
- At `institution.url` slutter å peke på et endepunkt som returnerer `corresponding_unit`.
- At `institution.url` begynner å redirecte, som gjør at affiliations forsvinner uten feil.
- Endret feiltekst («already exists») på 400 ved opprettelse av en person som finnes fra før.
- Nye felter i responsen bryter ikke, forutsatt at `JsonUtils.dtoObjectMapper` ignorerer ukjente felter (ikke verifisert i identity-service).
