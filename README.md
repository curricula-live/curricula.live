# curricula.live Java web application

New Java 25 / Spring Boot 4.1.2 application using Spring MVC, Thymeleaf, HTMX 2.0.11 and CSS. The legacy Next.js website continues to run independently.

Local development: run sibling API at port 8081, then execute ./mvnw spring-boot:run here. Open http://localhost:8080.

Override API hostname using CURRICULA_API_URL. The current routes are /, /about, /search, /search/results (HTMX fragment), /concepts/{slug}. The prototype shows unavailable state instead of fabricated ontology concepts.

This is NOT feature parity with the existing web repo. Preserve production routes, graph rendering and teacher workflows through regression testing before a cutover. Do not switch Vercel/DNS yet.
