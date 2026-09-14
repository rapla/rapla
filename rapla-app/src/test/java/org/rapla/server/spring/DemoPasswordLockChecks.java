package org.rapla.server.spring;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.rapla.entities.User;
import org.rapla.facade.RaplaFacade;
import org.rapla.server.spring.web.IsolatedDefaultDatasetTest;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.impl.server.LocalAbstractCachableOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * PRD 118 D8-2 — under {@code demo} no user can change a password: the {@code /change-password}
 * page is gone, the capability says so, the empty-password nag is skipped, and no allowlisted
 * endpoint changes a password. {@link DemoPasswordLockTest} runs these checks under {@code demo},
 * {@link DemoPasswordLockDefaultTest} under the default profile.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
@Tag("e2e")
abstract class DemoPasswordLockChecks extends IsolatedDefaultDatasetTest
{
    @Autowired MockMvc mockMvc;
    @Autowired RaplaFacade facade;
    @Autowired CachableStorageOperator operator;

    abstract boolean demo();

    @BeforeEach
    void passwordlessStudent() throws Exception
    {
        LocalAbstractCachableOperator op = (LocalAbstractCachableOperator) operator;
        if (op.getUser("student") == null)
        {
            User student = facade.newUser();
            student.setUsername("student");
            facade.store(student);
        }
        boolean locked = op.isLockPasswords();
        op.setLockPasswords(false);
        try
        {
            op.changePassword(op.getUser("student"), new char[0], new char[0]);
        }
        finally
        {
            op.setLockPasswords(locked);
        }
    }

    private MockHttpServletResponse login(String username, String password) throws Exception
    {
        return mockMvc.perform(post("/login").with(csrf()).param("username", username).param("password", password))
                .andReturn().getResponse();
    }

    private Cookie cookie(String username) throws Exception
    {
        Cookie access = login(username, "").getCookie("access_token");
        assertNotNull(access, username + " must log in with the empty password");
        return new Cookie("access_token", access.getValue());
    }

    @Test
    void changePasswordPageIsGone() throws Exception
    {
        Cookie student = cookie("student");
        int page = mockMvc.perform(get("/change-password").cookie(student)).andReturn().getResponse().getStatus();
        int submit = mockMvc.perform(post("/change-password").cookie(student).with(csrf())
                .param("newPassword", "x").param("confirmPassword", "x")).andReturn().getResponse().getStatus();
        if (demo())
        {
            assertEquals(404, page);
            assertEquals(404, submit);
        }
        else
        {
            assertEquals(200, page);
            assertEquals(302, submit);
        }
    }

    @Test
    void capabilityReportsTheLock() throws Exception
    {
        for (String username : new String[] { "admin", "student" })
        {
            String body = mockMvc.perform(get("/api/storage/profile/capabilities").cookie(cookie(username)))
                    .andReturn().getResponse().getContentAsString();
            assertTrue(body.contains("\"canChangePassword\":" + !demo()), username + " " + body);
        }
    }

    @Test
    void emptyPasswordLoginIsNotNagged() throws Exception
    {
        String expected = demo() ? "/app/" : "/change-password";
        assertEquals(expected, login("admin", "").getRedirectedUrl());
        assertEquals(expected, login("student", "").getRedirectedUrl());
    }

    @Test
    void noOpenEndpointChangesAPassword() throws Exception
    {
        if (!demo())
        {
            return;
        }
        Cookie student = cookie("student");
        String body = "{\"username\":\"student\",\"oldPassword\":\"\",\"newPassword\":\"walked\",\"password\":\"walked\"}";
        for (String mapping : DemoApiAllowlistTest.OPEN)
        {
            String[] parts = mapping.split(" ", 2);
            String path = parts[1].replaceAll("\\{[^}]+}", "student");
            MockHttpServletRequestBuilder request = request(HttpMethod.valueOf(parts[0]), path).cookie(student);
            if (!parts[0].equals("GET"))
            {
                request = request.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
            }
            mockMvc.perform(request);
        }
        mockMvc.perform(post("/api/graphql").cookie(student).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"mutation { changePassword(username: \\\"student\\\", newPassword: \\\"walked\\\") }\"}"));
        mockMvc.perform(post("/change-password").cookie(student).with(csrf()).param("newPassword", "walked").param("confirmPassword", "walked"));

        assertNotNull(login("student", "").getCookie("access_token"), "empty password must still log in");
        assertNull(login("student", "walked").getCookie("access_token"), "no walked endpoint may have set a password");
    }
}
