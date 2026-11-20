package com.posrestaurante.backend.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/api/auth/login/**", "/api/auth/staff").permitAll()
                        .requestMatchers("/api/usuarios/**").hasRole("DUENO")
                        .requestMatchers(HttpMethod.GET, "/api/mesas/**").authenticated()
                        .requestMatchers("/api/mesas/**").hasRole("DUENO")
                        .requestMatchers(HttpMethod.GET, "/api/categorias/**", "/api/modificadores/**").authenticated()
                        .requestMatchers("/api/categorias/**", "/api/modificadores/**").hasRole("DUENO")
                        .requestMatchers(HttpMethod.GET, "/api/platillos/**").authenticated()
                        .requestMatchers("/api/platillos/**").hasRole("DUENO")
                        .requestMatchers(HttpMethod.GET, "/api/ordenes/**").authenticated()
                        .requestMatchers("/api/ordenes/items/**").hasRole("CHEF")
                        .requestMatchers("/api/ordenes/**").hasRole("MESERO")
                        .requestMatchers(HttpMethod.GET, "/api/tickets/propios").hasRole("CHEF")
                        .requestMatchers(HttpMethod.GET, "/api/tickets/**").authenticated()
                        .requestMatchers("/api/caja/**").hasRole("CAJERO")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        (req, res, ex) -> res.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

}
