package io.mailagent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    UserDetailsService users(@Value("${app.admin-username}") String username,
                             @Value("${app.admin-password}") String password) {
        String effectiveUsername = username.isBlank() ? "admin" : username;
        String effectivePassword = password.isEmpty() ? "admin" : password;
        var encoder = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        return new InMemoryUserDetailsManager(User.withUsername(effectiveUsername)
                .password("{pbkdf2@SpringSecurity_v5_8}" + encoder.encode(effectivePassword)).roles("ADMIN").build());
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(a -> a.requestMatchers("/login", "/app.css", "/mail-content.css", "/error", "/s/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(f -> f.loginPage("/login").defaultSuccessUrl("/", true).permitAll())
                .logout(l -> l.logoutSuccessUrl("/login?logout"))
                .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives(
                        "default-src 'self'; style-src 'self'; img-src 'self' data:; frame-ancestors 'none'; form-action 'self'")))
                .build();
    }
}
