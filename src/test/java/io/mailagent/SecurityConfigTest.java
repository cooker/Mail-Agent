package io.mailagent;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {
    private final SecurityConfig config = new SecurityConfig();

    @Test
    void blankConfigurationFallsBackToAdmin() {
        var users = config.users("", "");
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(PasswordEncoderFactories.createDelegatingPasswordEncoder());

        var result = provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("admin", "admin"));

        assertThat(result.isAuthenticated()).isTrue();
    }

    @Test
    void passwordLengthIsNotValidated() {
        String password = "长密码".repeat(100);
        var users = config.users("operator", password);
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(PasswordEncoderFactories.createDelegatingPasswordEncoder());

        var result = provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("operator", password));

        assertThat(result.isAuthenticated()).isTrue();
    }
}
