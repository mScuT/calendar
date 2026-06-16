package com.example.meetings.service;

import com.example.meetings.model.User;
import com.example.meetings.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AppUserDetailsService - Assignment point 1 (business-logic unit tests).
 *
 * AppUserDetailsService is the bridge Spring Security uses at login: it loads a user by name and
 * turns it into a Spring UserDetails. UserRepository is a Mockito mock. Covered behaviour:
 *  - a known user becomes a UserDetails with the right username, password hash and ROLE_USER;
 *  - an unknown user throws UsernameNotFoundException.
 */
@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AppUserDetailsService appUserDetailsService;

    /** A known user becomes a Spring UserDetails with username, password hash and ROLE_USER. */
    @Test
    void loadUserByUsername_returnsUserDetailsWhenFound() {
        User alice = new User("alice", "alice@example.pt", "hashed-pw");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        UserDetails details = appUserDetailsService.loadUserByUsername("alice");

        assertThat(details.getUsername()).isEqualTo("alice");
        assertThat(details.getPassword()).isEqualTo("hashed-pw"); // the stored hash, never the raw password
        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    /** An unknown username throws UsernameNotFoundException (this is what Spring Security expects). */
    @Test
    void loadUserByUsername_throwsWhenUnknown() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appUserDetailsService.loadUserByUsername("unknown"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("Unknown user");
    }
}
