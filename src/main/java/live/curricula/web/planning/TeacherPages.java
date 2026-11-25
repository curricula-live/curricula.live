package live.curricula.web.planning;

import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Controller
@ConditionalOnProperty(name = "CURRICULA_PLANNING_ENABLED", havingValue = "true")
public final class TeacherPages {
    private final PlanningClient client;

    public TeacherPages(PlanningClient client) { this.client = client; }

    @GetMapping("/teach")
    public String plans(@RegisteredOAuth2AuthorizedClient("planning") OAuth2AuthorizedClient account,
                        @RequestParam(defaultValue = "0") int offset, Model model) {
        var plans = client.plans(token(account), offset);
        model.addAttribute("plans", plans == null ? List.of() : plans);
        model.addAttribute("next", plans != null && plans.size() == 100 && offset < 10000 ? offset + 100 : null);
        return "teacher-plans";
    }

    @GetMapping("/teach/{id}")
    public String edit(@RegisteredOAuth2AuthorizedClient("planning") OAuth2AuthorizedClient account,
                       @PathVariable UUID id, Model model) {
        model.addAttribute("plan", client.plan(token(account), id));
        return "teacher-plan";
    }

    @PostMapping("/teach")
    public String create(@RegisteredOAuth2AuthorizedClient("planning") OAuth2AuthorizedClient account,
                         @RequestParam String title, @RequestParam String conceptIri) {
        client.create(token(account), title, conceptIri);
        return "redirect:/teach";
    }

    @PostMapping("/teach/{id}")
    public String update(@RegisteredOAuth2AuthorizedClient("planning") OAuth2AuthorizedClient account,
                         @PathVariable UUID id, @RequestParam String title, @RequestParam String conceptIri,
                         @RequestParam long revision) {
        client.update(token(account), id, title, conceptIri, revision);
        return "redirect:/teach";
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(RestClientException.class)
    public String unavailable(RestClientException failure, Model model, jakarta.servlet.http.HttpServletResponse response) {
        if (failure instanceof HttpClientErrorException clientError) {
            var status = clientError.getStatusCode();
            response.setStatus(status.value());
            model.addAttribute("message", switch (status.value()) {
                case 400 -> "Enter a title and an active canonical concept IRI.";
                case 404 -> "Plan unavailable or changed. Reopen your plans before editing.";
                case 401, 403 -> "The identity provider did not grant planning access.";
                default -> "The planning request could not be completed.";
            });
        } else {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            model.addAttribute("message", "Planning is temporarily unavailable.");
        }
        return "teacher-error";
    }

    private static String token(OAuth2AuthorizedClient account) {
        if (account == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return account.getAccessToken().getTokenValue();
    }
}
