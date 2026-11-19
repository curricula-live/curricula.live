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
 @Test void homepage() throws Exception {var r=get("/");assertEquals(200,r.statusCode());assertTrue(r.body().contains("Explore the structure of learning"));}
 @Test void about() throws Exception {assertEquals(200,get("/about").statusCode());}
 @Test void searchPage() throws Exception {var r=get("/search");assertEquals(200,r.statusCode());assertTrue(r.body().contains("hx-get="));}
 @Test void htmxErrorIsShownWithoutFabricatedConcepts() throws Exception {
   var r=get("/search/results?q=binary");
   assertEquals(200,r.statusCode());
   assertTrue(r.body().contains("not connected yet"));
 }
 @Test void conceptWithoutBackendReturnsUnavailable() throws Exception {
   var r=get("/concepts/binary-search");
   assertEquals(503,r.statusCode());
   assertTrue(r.body().contains("not connected yet"));
 }
}
