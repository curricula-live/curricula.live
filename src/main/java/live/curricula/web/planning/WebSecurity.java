package live.curricula.web.planning;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class WebSecurity {
    @Bean
    SecurityFilterChain webFilter(HttpSecurity http,
                                  @Value("${CURRICULA_PLANNING_ENABLED:false}") boolean enabled,
                                  ObjectProvider<ClientRegistrationRepository> registrations) throws Exception {
        if (enabled) {
            var repository = registrations.getIfAvailable();
            var registration = repository == null ? null : repository.findByRegistrationId("planning");
            if (registration == null || !registration.getScopes().containsAll(java.util.List.of("openid", "curricula.planning"))) {
                throw new IllegalStateException("Configure the planning OIDC registration with openid and curricula.planning scopes");
            }
            requireHttps(registration.getProviderDetails().getIssuerUri());
            requireHttps(registration.getRedirectUri());
            var resolver = new DefaultOAuth2AuthorizationRequestResolver(repository, "/oauth2/authorization");
            resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
            http.authorizeHttpRequests(auth -> auth.requestMatchers("/teach", "/teach/**").authenticated()
                            .anyRequest().permitAll())
                    .oauth2Login(login -> login.authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver)))
                    .logout(logout -> logout.logoutSuccessUrl("/"));
        } else {
            http.authorizeHttpRequests(auth -> auth.requestMatchers("/teach", "/teach/**", "/oauth2/**", "/login/**", "/login")
                            .denyAll().anyRequest().permitAll())
                    .logout(logout -> logout.disable());
        }
        return http.build();
    }

    private static void requireHttps(String value) {
        if (value == null) throw new IllegalArgumentException("Planning requires an HTTPS issuer and callback");
        var uri = java.net.URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Planning requires an HTTPS issuer and callback");
        }
    }
}
