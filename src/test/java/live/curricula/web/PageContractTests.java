package live.curricula.web;
import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "curricula.api.base-url=http://127.0.0.1:1")
class PageContractTests {
 @LocalServerPort int port;
 private final HttpClient client=HttpClient.newHttpClient();
 private HttpResponse<String> get(String path) throws Exception {
  return client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).GET().build(),HttpResponse.BodyHandlers.ofString());
 }
 @Test void homepage() throws Exception {var r=get("/");assertEquals(200,r.statusCode());assertTrue(r.body().contains("Search knowledge."));assertTrue(r.body().contains("Review concepts, plan lessons."));assertTrue(r.body().contains("brand-mark.svg"));assertTrue(r.body().contains("binary numbers"));}
 @Test void about() throws Exception {assertEquals(200,get("/about").statusCode());}
 @Test void searchPage() throws Exception {var r=get("/search");assertEquals(200,r.statusCode());assertTrue(r.body().contains("hx-get="));}
 @Test void htmxErrorIsShownWithoutFabricatedConcepts() throws Exception {
   var r=get("/search/results?q=binary");
   assertEquals(200,r.statusCode());
   assertTrue(r.body().contains("Concept search is temporarily unavailable"));
 }
 @Test void conceptWithoutBackendReturnsUnavailable() throws Exception {
   var r=get("/concepts/binary-search");
   assertEquals(503,r.statusCode());
   assertTrue(r.body().contains("currently unavailable"));
 }
}
