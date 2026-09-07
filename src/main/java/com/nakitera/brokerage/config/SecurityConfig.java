package com.nakitera.brokerage.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(SecurityProperties properties, PasswordEncoder passwordEncoder) {
        InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
        manager.createUser(User.builder()
                .username(properties.getAdmin().getUsername())
                .password(passwordEncoder.encode(properties.getAdmin().getPassword()))
                .roles("ADMIN")
                .build());
        properties.getCustomers().forEach(customer -> manager.createUser(User.builder()
                .username(customer.getUsername())
                .password(passwordEncoder.encode(customer.getPassword()))
                .roles("CUSTOMER")
                .build()));
        return manager;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProblemHandlers problemHandlers) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                .httpBasic(Customizer.withDefaults())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problemHandlers)
                        .accessDeniedHandler(problemHandlers)
                );
        return http.build();
    }
}
