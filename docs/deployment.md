# Oracle ARM64 and Cloudflare deployment preparation

This is a proposed single-VM Docker Compose layout; it has not yet been executed on an actual Oracle ARM64 instance. Both Java applications are independently buildable and have passed local JVM tests and HTTP integration smoke checks on tower.

## Topology

- Spring Boot API (port 8081, private Docker network): Apache Jena TDB2 persistent RDF store.
- Spring Boot web (port 8080, bound to loopback on the VM): Spring MVC, Thymeleaf, HTMX and a read-only browser graph viewer.
- Caddy or Nginx on the host: proxy the public origin to loopback port 8080.
- Cloudflare: DNS, HTTPS reverse proxy, caching for static assets, origin protection. Thymeleaf's dynamic pages **are not deployed to Cloudflare Pages**.
- Source ontologies and the previous Supabase/Vercel deployment are unaffected.

The sample compose file is under deploy/compose.yaml. Keep both new repositories as sibling directories so relative build contexts resolve.

## Start prerequisites

Obtain a separately reviewed immutable producer RDF release (not the moving PR feature branch). Verify its artifact hashes independently, and set:
- RDF_PACKAGE_DIR: absolute path containing manifest.json and the five published artifacts
- RDF_MANIFEST_SHA256: SHA-256 of the reviewed manifest
- RDF_COMMIT: producer's 40-character immutable Git commit
All three belong in a deployment environment or secret store, **never in committed credentials**.

Execute: docker compose -f deploy/compose.yaml up --build -d

Do not expose API port 8081 directly. Do not mount read/write ontology source directories. TDB2 data persists in the named Docker volume rdf-data. Back up this volume and the immutable publication package outside Oracle.

## Current limitations

- No live Oracle ARM64 deployment or image manifest validation has yet been performed.
- Embedded TDB2 is selected initially to avoid another long-lived JVM on OCI Always Free. The API abstraction permits a future switch to a private Fuseki service.
- Authentication, administrative editor, traffic cutover, object storage, full graph-WebGL parity, teacher-planning workflow, and email/SSO are future migration tasks.
- UI currently implements a bounded read-only SVG relationship preview; the existing Sigma.js graph explorer still needs a parity-focused port.
- The Java API is independent of curricula-live/data and does not assume the legacy PostgreSQL UUID model.
