package com.telecom.platform.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http.csrf(c -> c.disable()) // stateless bearer API; the cookie endpoints demand a custom header and use SameSite=Strict
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/**", "/api/signup/**", "/api/public/**", "/api/payments/webhook", "/api/inbound/**", "/actuator/health").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(o -> o.jwt(j -> j.decoder(jwtService.accessDecoder()).jwtAuthenticationConverter(converter())))
            .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'")));
        return http.build();
    }

    /** role -> ROLE_x, pool -> POOL_x, customer permissions -> PERM_x */
    static JwtAuthenticationConverter converter() {
        Converter<Jwt, Collection<GrantedAuthority>> authorities = jwt -> {
            List<GrantedAuthority> out = new ArrayList<>();
            String role = jwt.getClaimAsString("role");
            String pool = jwt.getClaimAsString("pool");
            if (role != null) out.add(new SimpleGrantedAuthority("ROLE_" + role));
            if (pool != null) out.add(new SimpleGrantedAuthority("POOL_" + pool));
            List<String> perms = jwt.getClaimAsStringList("perms");
            if (perms != null) perms.forEach(p -> out.add(new SimpleGrantedAuthority("PERM_" + p)));
            return out;
        };
        JwtAuthenticationConverter c = new JwtAuthenticationConverter();
        c.setJwtGrantedAuthoritiesConverter(authorities);
        return c;
    }
}
