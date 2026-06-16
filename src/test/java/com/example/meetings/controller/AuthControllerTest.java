package com.example.meetings.controller;

import com.example.meetings.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * REST API integration tests for AuthController and the security rules - Assignment point 3.
 *
 * Boots the whole app with @SpringBootTest and drives it through MockMvc. Requests
 * go through Spring Security and the real services and test database. @Transactional rolls back the
 * data each test writes, so the tests stay isolated. Covered behaviour:
 *  - GET /login and GET /register return their views (and are public);
 *  - POST /register creates a user and redirects, and handles a duplicate username;
 *  - a private route without login redirects to /login.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    /** GET /login is public and returns the login view. */
    @Test
    void getLogin_returnsLoginView() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    /** GET /register is public and returns the register view. */
    @Test
    void getRegister_returnsRegisterView() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"));
    }

    /** POST /register creates the user and redirects to the login page. */
    @Test
    void postRegister_createsUserAndRedirectsToLogin() throws Exception {
        mockMvc.perform(post("/register")
                        .param("username", "alice")
                        .param("email", "alice@example.pt")
                        .param("password", "secret")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        // The user really reached the test database.
        assertThat(userService.requireByUsername("alice").getUsername()).isEqualTo("alice");
    }

    /** A duplicate username returns the register view again with an error message. */
    @Test
    void postRegister_duplicateUsername_returnsRegisterViewWithError() throws Exception {
        userService.register("alice", "alice@example.pt", "secret");

        mockMvc.perform(post("/register")
                        .param("username", "alice")
                        .param("email", "other@example.pt")
                        .param("password", "another")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("error"));
    }

    /** A private route accessed without login is redirected to /login by Spring Security. */
    @Test
    void privateRoute_withoutLogin_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/calendar"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }
}
