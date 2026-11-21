# Phase 3 parity progress

This update follows the [Phase 2 inventory](phase-2-parity.md) and its pinned Django, Next.js and Sigma.js sources.

| Legacy surface | Phase 3 implementation | Compatibility boundary |
| --- | --- | --- |
| Django discovery and health | `/` and `/health/` return the legacy service and process-health payloads. | `/api/v1/status` reports knowledge readiness. |
| Django v1 concept list, detail and search | `/v1/concepts/`, `/v1/concepts/{slug}/` and `/v1/search/?category=concepts` preserve the slug-based wire shapes. Concept search uses the legacy slug variants and exact-match ordering. | The results cover the active formal-sciences publication. A shared spelling does not crosswalk a legacy concept or relate it to a legacy UUID. Legacy connection categories return 501. The RDF-native endpoints and web search support real connections with predicate and canonical endpoint IRIs. |
| Next.js concept and archive search | Live search ranks exact canonical labels and slugs ahead of prefix and partial results. It folds accents and separator variants. The pinned provisional archive appears as a separate group with Romanian, English, and alias matching. | Archive results open their original release-scoped IDs at the legacy graph origin. The Java web application reads a checksummed local archive and makes no request-time archive downloads. |
| Graph navigation | `/graph` is a concept picker. Existing canonical slug bookmarks open the interactive graph. Archived concept and overlay IRIs are forwarded to the legacy graph without rewriting their identifiers. | The full archive overview and raw `graph.json` remain on the legacy graph origin. |
| Scholarly browsing | `/evidence` lists sources from the active release. Source and concept pages expose reified citations with typed object terms. Pagination pins the release manifest SHA-256 and restarts on a change. | Legacy ACM CCS, CS2023, and CSTA taxonomy and hierarchy require independently approved, versioned artifacts. The legacy repository reports no installed scholarly release. |
| Concept documentation and educational tabs | Overview and pedagogy display recorded RDF definitions and link to citations. Curriculum and pedagogy bookmarks show an explicit data state. | Official curriculum mappings, pedagogical notes and prerequisites need reviewed data. Scientific `uses` edges carry their recorded meaning. |
| About, Privacy and Contact | The footer links to standalone, bookmarkable pages. Privacy and contact content preserve the legacy text and contact categories. Bug reports link to the Java web repository. | Links open an email client or issue page. |
| Accounts and planning | The API has an isolated PostgreSQL migration and owner-scoped storage. PostgreSQL integration tests cover issuer isolation and concurrent edits. | Sign-in, provisioning, sessions, account erasure and plans remain disabled in the public application until there is a verified identity provider and reviewed lifecycle policy. |
| Django relation UUIDs and v2 | Canonical relationships, cited statements, and graph layers remain available through the RDF-native read paths. | UUID-facing v1 relations and v2 contracts need a reviewed crosswalk and regression fixtures before cutover. |

## Enable archived search locally

```sh
bash scripts/fetch-graph-archive.sh
CURRICULA_ARCHIVE_PATH="$PWD/target/graph-archive.json" ./mvnw spring-boot:run
```

The archive is pinned to the public projection at commit `6da9d1066eabe36194593be422a1253f30062885` with SHA-256 `c36057ff0fefe52150eb09f44213baec3443e36f3a38f185e13db93300086d70`. For Compose, set `ARCHIVE_PACKAGE_PATH` to the verified file on the host and add `-f deploy/compose.archive.yaml` to the staging command. The override mounts only the archive file and sets the web process path. The deployment can omit archived search and retain direct links to the original graph. [Published archive](https://github.com/curricula-live/graph/blob/6da9d1066eabe36194593be422a1253f30062885/public/graph.json)
