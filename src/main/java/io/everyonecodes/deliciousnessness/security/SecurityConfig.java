package io.everyonecodes.deliciousnessness.security;

import io.everyonecodes.deliciousnessness.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Scripts only from this site. Styles allow inline because the pages use style attributes.
    // Recipe images can come from any web address, so img-src is open to http and https.
    private static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
            "font-src https://fonts.gstatic.com",
            "img-src 'self' https: http: data:",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    private final String rememberMeKey;
    private final boolean secureCookies;

    public SecurityConfig(@Value("${app.remember-me-key}") String rememberMeKey,
                          @Value("${app.secure-cookies:false}") boolean secureCookies) {
        this.rememberMeKey = rememberMeKey;
        this.secureCookies = secureCookies;
    }

    // The filter is added to the security chain below. This stops Spring Boot adding it a second time.
    @Bean
    public FilterRegistrationBean<LoginThrottle> loginThrottleRegistration(LoginThrottle throttle) {
        FilterRegistrationBean<LoginThrottle> registration = new FilterRegistrationBean<>(throttle);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, LoginThrottle loginThrottle) throws Exception {
        http
                .addFilterBefore(loginThrottle, UsernamePasswordAuthenticationFilter.class)
                // Pages and forms keep CSRF protection. A request that carries its own Basic credentials is
                // not a browser riding on a login session (the 401 sends no challenge, so browsers never
                // store Basic credentials), so it needs no token.
                .csrf(csrf -> csrf.ignoringRequestMatchers(request -> {
                    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
                    return header != null && header.regionMatches(true, 0, "Basic ", 0, 6);
                }))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        // Keeps the site out of search engines.
                        .addHeaderWriter(new StaticHeadersWriter("X-Robots-Tag", "noindex, nofollow"))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.SAME_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(60L * 60 * 24 * 365)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/login", "/signup/*", "/private", "/error",
                                "/robots.txt").permitAll()
                        // Guests can look at recipes, search and filter. Nothing else.
                        .requestMatchers(HttpMethod.GET,
                                "/recipes", "/recipes/fragment", "/recipes/{id:\\d+}",
                                "/api/recipes", "/api/recipes/{id:\\d+}",
                                "/api/categories", "/api/ingredients/autocomplete")
                        .hasAnyRole("USER", "GUEST")
                        // Everyone with an account looks after their own profile and may comment.
                        .requestMatchers("/profile", "/profile/*").hasAnyRole("USER", "GUEST")
                        .requestMatchers(HttpMethod.POST, "/recipes/{id:\\d+}/comments", "/comments/{id:\\d+}/delete")
                        .hasAnyRole("USER", "GUEST")
                        .anyRequest().hasRole("USER"))
                // Someone not logged in sees the "private website" page, not the login form or the site.
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(new PrivateSiteEntryPoint()))
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/recipes", true))
                .rememberMe(remember -> remember
                        .key(rememberMeKey)
                        .useSecureCookie(secureCookies)
                        .alwaysRemember(true)
                        .tokenValiditySeconds(60 * 60 * 24 * 30))
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout"))
                .httpBasic(basic -> basic.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(AppUserRepository users) {
        return username -> users.findByUsername(username)
                .map(u -> User.withUsername(u.getUsername())
                        .password(u.getPasswordHash())
                        .roles(u.effectiveRole().name())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }

}