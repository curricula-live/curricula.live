package live.curricula.web.pages;
import jakarta.servlet.http.HttpServletResponse;
import live.curricula.web.catalog.CatalogClient;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
@Controller
public class PagesController {
 private final CatalogClient client;
 public PagesController(CatalogClient client){this.client=client;}
 @GetMapping("/") public String home(){return "home";}
 @GetMapping("/about") public String about(){return "about";}
 @GetMapping("/search") public String search(@RequestParam(defaultValue="") String q,Model model){
   populate(q,model);return "search";
 }
 @GetMapping("/search/results") public String searchResults(@RequestParam(defaultValue="") String q,Model model){
   populate(q,model);return "fragments/results :: results";
 }
 private void populate(String q,Model model){
   model.addAttribute("query",q);
   model.addAttribute("searched",!q.isBlank());
   model.addAttribute("unavailable",false);
   model.addAttribute("results",List.of());
   if(q.isBlank())return;
   try{
      var response=client.search(q.trim());
      model.addAttribute("results",response==null||response.concepts()==null?List.of():response.concepts());
   }catch(RestClientException e){model.addAttribute("unavailable",true);}
 }
 @GetMapping("/concepts/{slug}") public String concept(@PathVariable String slug, Model model, HttpServletResponse response){
   model.addAttribute("slug",slug);model.addAttribute("concept",null);model.addAttribute("unavailable",false);
   try{model.addAttribute("concept",client.concept(slug));}
   catch(HttpClientErrorException.NotFound e){response.setStatus(404);}
   catch(RestClientException e){model.addAttribute("unavailable",true);response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());}
   return "concept";
 }
}
