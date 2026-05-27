package de.ptb.dcc.configurations;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Attivo solo con il profilo "auth-debug".
 * Inietta nel SecurityContext un utente fittizio con ruolo ROLE_app-admin
 * e un Jwt stub con subject "debug-admin-user", così DccService.getCurrentUserId()
 * e DccService.isAdmin() funzionano senza un token Keycloak reale.
 */
@Component
@Profile("auth-debug")
public class DebugAuthFilter extends OncePerRequestFilter {

    private static final String DEBUG_USER_ID = "debug-admin-user";
    private static final String DEBUG_USER_NAME = "Debug";
    private static final String DEBUG_USER_SURNAME = "Admin";
    private static final String DEBUG_USER_EMAIL = "debug@localhost";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Costruisce un Jwt stub con i claim minimi usati da DccService
        Jwt jwt = Jwt.withTokenValue("debug-token")
                .header("alg", "none")
                .subject(DEBUG_USER_ID)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("given_name", DEBUG_USER_NAME)
                .claim("family_name", DEBUG_USER_SURNAME)
                .claim("email", DEBUG_USER_EMAIL)
                // ruolo admin nello stesso formato usato da CustomJwtGrantedAuthoritiesConverter
                .claim("resource_access", Map.of(
                        "iam1client", Map.of("roles", List.of("app-admin"))
                ))
                .build();

        var auth = new UsernamePasswordAuthenticationToken(
                jwt,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_app-admin"))
        );

        SecurityContextHolder.getContext().setAuthentication(auth);
        filterChain.doFilter(request, response);
    }
}
