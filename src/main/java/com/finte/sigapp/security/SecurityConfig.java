package com.finte.sigapp.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final JwtAuthenticationEntryPoint jwtEntryPoint;

    @org.springframework.beans.factory.annotation.Value("${security.developer-mode}")
    private boolean developerMode;

    private static final String[] PUBLIC_ENDPOINTS = {
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/v3/api-docs.yaml",
            "/api/v1/auth/**",
            "/api/v1/health/**",

            // Probes de Kubernetes: el kubelet no envia token JWT. Sin estas rutas
            // abiertas las probes reciben 401 y el pod nunca llega a Ready.
            "/actuator/health",
            "/actuator/health/liveness",
            "/actuator/health/readiness"
    };

    /**
     * Rutas reservadas al login administrativo. hasRole("ADMIN") compara contra la
     * autoridad ROLE_ADMIN, que JwtAuthenticationFilter construye a partir del
     * claim "rol" del token.
     */
    private static final String ROL_ADMIN = "ADMIN";

    private static final String[] ADMIN_ENDPOINTS = {
            // Se declaran las dos formas: la raiz sin barra final (POST y GET de la
            // bandeja) y el resto del arbol.
            "/api/v1/traspasos",
            "/api/v1/traspasos/**"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable) // Deshabilitado para APIs REST
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (developerMode) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        } else {
            http.authorizeHttpRequests(auth -> auth
                    .requestMatchers(PUBLIC_ENDPOINTS)
                    .permitAll()
                    // Traspasos es modulo administrativo: solo acepta el token de
                    // /api/v1/auth/login, que lleva el claim rol=ADMIN. El token de
                    // /api/v1/auth/login/contador lleva rol=CONTADOR y aqui recibe 403.
                    .requestMatchers(ADMIN_ENDPOINTS)
                    .hasRole(ROL_ADMIN)
                    .anyRequest().authenticated());
            // Sin entry point propio Spring devuelve 403 vacio para todo rechazo.
            http.exceptionHandling(ex -> ex.authenticationEntryPoint(jwtEntryPoint));
            http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }

    @Configuration
    public class WebConfig implements WebMvcConfigurer {

        @Override
        public void addCorsMappings(CorsRegistry registry) {
            registry.addMapping("/**") // Todas las rutas
                    .allowedOrigins("http://localhost:8082", "http://localhost:4200", "http://localhost:5173") // Local
                                                                                                               // dev
                                                                                                               // allowlist
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("Authorization", "Content-Type", "Accept")
                    .allowCredentials(false);
        }
    }
}