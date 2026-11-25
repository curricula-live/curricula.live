package live.curricula.web.pages;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
final class SiteModel {
    @ModelAttribute("planningEnabled")
    boolean planningEnabled(@Value("${curricula.planning-enabled:false}") boolean enabled) { return enabled; }
}
