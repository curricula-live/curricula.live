package live.curricula.web.pages;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public final class UtilityPagesController {
    @GetMapping("/privacy")
    public String privacy() { return "privacy"; }

    @GetMapping("/contact")
    public String contact() { return "contact"; }
}
