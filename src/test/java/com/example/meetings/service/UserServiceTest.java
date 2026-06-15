package com.example.meetings.service;

import com.example.meetings.model.User;
import com.example.meetings.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for UserService - Assignment point 1 (business-logic unit tests).
 *
 * UserService depends on UserRepository and PasswordEncoder, which we replace with Mockito mocks
 * so the logic is tested in isolation. Covered behaviour:
 *  - register() rejects an already-taken username and never saves;
 *  - register() stores the encoded password, never the raw one;
 *  - requireByUsername() returns the user, or throws when it does not exist.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    /** A duplicate username must abort registration before anything is persisted. */
    @Test
    void register_rejectsDuplicateUsernameAndDoesNotSave() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> userService.register("alice", "alice@example.pt", "secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already taken");

        verify(userRepository, never()).save(any());
    }

    /** The raw password must be hashed by the encoder; the stored user carries the hash, not the raw text. */
    @Test
    void register_encodesPasswordBeforeSaving() {
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("ENCODED");
        // save() echoes its argument back so we can assert on what would be persisted.
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = userService.register("bob", "bob@example.pt", "secret");

        verify(passwordEncoder).encode("secret");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("ENCODED");
        assertThat(saved.getPasswordHash()).isNotEqualTo("secret");
    }

    /** A known username resolves to its user. */
    @Test
    void requireByUsername_returnsUserWhenFound() {
        User alice = new User("alice", "alice@example.pt", "hash");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        assertThat(userService.requireByUsername("alice")).isSameAs(alice);
    }

    /** An unknown username must raise, not return null. */
    @Test
    void requireByUsername_throwsWhenUnknown() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.requireByUsername("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown user");
    }
}
