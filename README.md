# curricula.live Java web application

New Java 25 / Spring Boot 4.1.2 application using Spring MVC, Thymeleaf, HTMX 2.0.11 and CSS. The legacy Next.js website continues to run independently.

Local development: run sibling API at port 8081, then execute ./mvnw spring-boot:run here. Open http://localhost:8080.

Override API hostname using CURRICULA_API_URL. The current routes are /, /about, /search, /search/results (HTMX fragment), /concepts/{slug}. The prototype shows unavailable state instead of fabricated ontology concepts.

This is NOT feature parity with the existing web repo. Preserve production routes, graph rendering and teacher workflows through regression testing before a cutover. Do not switch Vercel/DNS yet.

## RDF-backed knowledge pages

When the independent Java API has a verified RDF release, the search and concept pages display canonical concepts, scientific relationships, taxonomy, mappings and provenance from the API. An initial read-only directed graph preview is served at /concepts/{slug}/graph, with an internal JSON projection at /concepts/{slug}/graph/data?depth=2. The preview does not infer scientific relationships.

See docs/deployment.md for the proposed Oracle ARM64 Docker Compose topology and Cloudflare reverse-proxy role. Live Oracle deployment, authentication, production cutover and the existing rich WebGL graph parity remain unimplemented.
