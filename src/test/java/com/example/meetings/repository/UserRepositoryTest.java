package com.example.meetings.repository;

import com.example.meetings.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Database integration tests for UserRepository - Assignment point 4.
 *
 * @DataJpaTest loads only the JPA layer (repositories, entities, DataSource) and runs against the
 * test H2 database from application-test.properties.
 * It is @Transactional, so each test rolls back. The TestEntityManager inserts data directly.
 * Covered behaviour:
 *  - existsByUsername, findByUsername and findByIcalToken.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    /** existsByUsername is true only for a username that was saved. */
    @Test
    void existsByUsername_isTrueOnlyForSavedUsernames() {
        entityManager.persistAndFlush(new User("alice", "alice@example.pt", "hash"));

        assertThat(userRepository.existsByUsername("alice")).isTrue();
        assertThat(userRepository.existsByUsername("bob")).isFalse();
    }

    /** findByUsername returns the matching user, or empty when unknown. */
    @Test
    void findByUsername_returnsUserOrEmpty() {
        entityManager.persistAndFlush(new User("alice", "alice@example.pt", "hash"));

        User found = userRepository.findByUsername("alice").orElseThrow();
        assertThat(found.getEmail()).isEqualTo("alice@example.pt");
        assertThat(userRepository.findByUsername("nobody")).isEmpty();
    }

    /** findByIcalToken looks a user up by their feed token. */
    @Test
    void findByIcalToken_returnsUserForTheirToken() {
        User alice = entityManager.persistAndFlush(new User("alice", "alice@example.pt", "hash"));

        User found = userRepository.findByIcalToken(alice.getIcalToken()).orElseThrow();
        assertThat(found.getUsername()).isEqualTo("alice");
        assertThat(userRepository.findByIcalToken("not-a-token")).isEmpty();
    }
}
