package live.curricula.web;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SearchRenderingTests {
    private static final String BASE = "https://curricula.live/id/formal-sciences/";
    private static final HttpServer API = api();
    private final HttpClient http = HttpClient.newHttpClient();
    @LocalServerPort int port;

    @DynamicPropertySource
    static void apiOrigin(DynamicPropertyRegistry properties) {
        properties.add("curricula.api.base-url", () -> "http://127.0.0.1:" + API.getAddress().getPort());
    }

    @AfterAll
    static void stopApi() {
        API.stop(0);
    }

    @Test
    void legacyCategoryUrlRendersOnlyRecordedConnections() throws Exception {
        var result = get("/search?q=dpll&category=connections");
        assertEquals(200, result.statusCode());
        assertTrue(result.body().contains("value=\"connections\" selected=\"selected\""));
        assertTrue(result.body().contains("title=\"" + BASE + "vocab/solves\">solves"));
        assertTrue(result.body().contains("href=\"/concepts/dpll-algorithm\">DPLL algorithm"));
        assertTrue(result.body().contains("href=\"/concepts/boolean-satisfiability\">Boolean satisfiability"));
        assertFalse(result.body().contains("concept-results-title"));
    }

    @Test
    void htmxKeepsCategoryAndEscapesTheBookmarkQuery() throws Exception {
        var result = get("/search/results?q=" + URLEncoder.encode("dpll & sat + id", StandardCharsets.UTF_8)
                + "&category=all");
        assertEquals(200, result.statusCode());
        assertEquals("/search?q=dpll%20%26%20sat%20%2B%20id&category=all",
                result.headers().firstValue("HX-Push-Url").orElseThrow());
        assertTrue(result.body().contains("concept-results-title"));
        assertTrue(result.body().contains("connection-results-title"));
        assertTrue(result.body().contains("DPLL concept result"));
        assertTrue(result.body().contains("Boolean satisfiability"));
    }

    @Test
    void oneSearchOutageDoesNotHideTheOtherCategory() throws Exception {
        var result = get("/search?q=connection-outage");
        assertEquals(200, result.statusCode());
        assertTrue(result.body().contains("DPLL concept result"));
        assertTrue(result.body().contains("Connection search is temporarily unavailable"));
        assertFalse(result.body().contains("No matching connections"));
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpServer api() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/v1/search", exchange -> {
                String q = decodeQuery(exchange.getRequestURI());
                if (!q.equals("dpll & sat + id") && !q.equals("connection-outage")) {
                    exchange.sendResponseHeaders(400, -1);
                } else {
                    byte[] body = """
                            {"concepts":[{"iri":"%sconcept/dpll-algorithm","slug":"dpll-algorithm","label":"DPLL concept result"}]}
                            """.formatted(BASE).getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                }
                exchange.close();
            });
            server.createContext("/api/v1/relationships/search", exchange -> {
                String q = decodeQuery(exchange.getRequestURI());
                if (q.equals("connection-outage")) {
                    exchange.sendResponseHeaders(503, -1);
                } else if (!q.equals("dpll & sat + id") && !q.equals("dpll")) {
                    exchange.sendResponseHeaders(400, -1);
                } else {
                    byte[] body = """
                            {"connections":[{"subject":"%1$sconcept/dpll-algorithm","predicate":"%1$svocab/solves",
                            "object":"%1$sconcept/boolean-satisfiability","graph":"asserted",
                            "subjectSlug":"dpll-algorithm","subjectLabel":"DPLL algorithm",
                            "objectSlug":"boolean-satisfiability","objectLabel":"Boolean satisfiability"}]}
                            """.formatted(BASE).getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                }
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot start search API test server", exception);
        }
    }

    private static String decodeQuery(URI uri) {
        return URLDecoder.decode(uri.getRawQuery().substring(2), StandardCharsets.UTF_8);
    }
}
