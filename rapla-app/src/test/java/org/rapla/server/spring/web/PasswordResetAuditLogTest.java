package org.rapla.server.spring.web;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.User;
import org.rapla.facade.RaplaFacade;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.storage.CachableStorageOperator;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Security audit PM2 / WP S7 — a password reset of another user by an admin or group admin stays possible but writes
 * one AUDIT line on the "rapla" logger (actor and target, never a password). A user changing their own password is not audited.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class PasswordResetAuditLogTest
{
    private static final String PREFIX = "AUDIT password-reset:";

    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws Exception
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = PasswordResetAuditLogTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in);
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    CachableStorageOperator operator;
    @Autowired
    RaplaFacade facade;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger raplaLogger = (Logger) LoggerFactory.getLogger("rapla");

    @BeforeEach
    void captureAuditLog() throws Exception
    {
        appender.start();
        raplaLogger.addAppender(appender);
        if (operator.getUser("smithers") == null)
        {
            User u = facade.newUser();
            u.setUsername("smithers");
            u.addGroup(operator.getSuperCategory().getCategory("user-groups").getCategory("powerplant"));
            facade.store(u);
            operator.changePassword(operator.getUser("smithers"), new char[0], "smithers-old".toCharArray());
        }
    }

    @AfterEach
    void detach()
    {
        raplaLogger.detachAppender(appender);
    }

    private int changePassword(String bearer, String username, String oldPassword, String newPassword) throws Exception
    {
        return mockMvc.perform(post("/api/storage/change/password")
                        .header("Authorization", "Bearer " + bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"oldPassword\":\"" + oldPassword
                                + "\",\"newPassword\":\"" + newPassword + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    private List<String> auditLines()
    {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .filter(m -> m.startsWith(PREFIX)).collect(Collectors.toList());
    }

    @Test
    void groupAdminResetOfMemberIsAudited() throws Exception
    {
        String monty = OAuthTestSupport.loginAs(mockMvc, "monty", "burns");
        assertEquals(200, changePassword(monty, "smithers", "", "reset-by-admin-7"), "the reset itself must succeed");

        List<String> lines = auditLines();
        assertEquals(1, lines.size(), () -> "expected exactly one audit line, got " + lines);
        assertTrue(lines.get(0).contains("actor=monty"), lines.get(0));
        assertTrue(lines.get(0).contains("target=smithers"), lines.get(0));
        assertTrue(appender.list.stream().noneMatch(e -> e.getFormattedMessage().contains("reset-by-admin-7")),
                "no log line may contain the new password");
    }

    @Test
    void selfChangeIsNotAudited() throws Exception
    {
        String monty = OAuthTestSupport.loginAs(mockMvc, "monty", "burns");
        assertEquals(200, changePassword(monty, "monty", "burns", "burns-self-9"), "the self change itself must succeed");
        assertEquals(List.of(), auditLines(), "a user changing their own password is not audited");
        assertFalse(appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains("burns-self-9")),
                "no log line may contain a password");
        // restore for the other test's login
        operator.changePassword(operator.getUser("monty"), "burns-self-9".toCharArray(), "burns".toCharArray());
    }
}
