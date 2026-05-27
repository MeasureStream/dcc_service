package de.ptb.dcc.configurations;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Attivo solo con il profilo "auth-debug".
 * Sostituisce il SecurityFilterChain normale:
 *   - tutto è permitAll (nessun JWT richiesto)
 *   - il DebugAuthFilter inietta prima di ogni richiesta un utente admin fittizio
 *
 * Il SecurityConfig normale rimane ma viene ignorato perché questo ha @Order(1).
 */
@Configuration
@Profile("auth-debug")
@Order(1)
public class SecurityConfigDebug {

    private final DebugAuthFilter debugAuthFilter;

    public SecurityConfigDebug(DebugAuthFilter debugAuthFilter) {
        this.debugAuthFilter = debugAuthFilter;
    }

    @Bean
    public SecurityFilterChain debugFilterChain(HttpSecurity http) throws Exception {
        return http
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(c -> c.disable())
                .csrf(c -> c.disable())
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .addFilterBefore(debugAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
