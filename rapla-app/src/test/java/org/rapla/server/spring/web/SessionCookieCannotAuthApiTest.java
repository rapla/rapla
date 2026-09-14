package org.rapla.server.spring.web;

import org.junit.jupiter.api.Test;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD 102 OQ1 — a browser form-login {@code JSESSIONID}, on its own (no
 * {@code access_token} cookie, no {@code Authorization: Bearer}), MUST NOT be
 * able to read data from {@code /api/**}.
 *
 * <p><b>Why this matters.</b> The credential-hardening model (PRD 102 D1–D3)
 * assumes an untrusted same-origin page cannot reach {@code /api} data merely by
 * riding the ambient session cookie the browser attaches automatically. OQ1
 * asked to <em>verify</em> that — the original wording guessed the guarantee came
 * from a {@code SessionCreationPolicy.STATELESS} chain.
 *
 * <p><b>What this test actually establishes.</b> The chain is <em>not</em>
 * stateless — a {@code JSESSIONID} <em>does</em> satisfy Spring's
 * {@code .authenticated()} gate (the explorer pages {@code /graphiql} +
 * {@code /swagger-ui} deliberately rely on exactly that, see
 * {@link ExplorerAuthGateTest}). The protection is a <em>second</em> layer:
 * {@code SpringSecurityRemoteSession.checkAndGetUser} derives the rapla
 * {@link org.rapla.entities.User} <b>only</b> from a {@code JwtAuthenticationToken}
 * (Bearer header or {@code access_token} cookie promoted by
 * {@code CookieToBearerFilter}). A form-login session stores a
 * {@code UsernamePasswordAuthenticationToken} instead — so every {@code /api}
 * data endpoint resolves <em>no</em> rapla user and answers
 * {@code RaplaSecurityException} → <b>401</b>.
 *
 * <p>The two tests together pin the real mechanism so a future refactor can't
 * silently open the hole: {@link #sessionAuthenticatesTheChain} proves the
 * session is a live authentication (not inert), and
 * {@link #sessionCannotReadApiData} proves that live authentication still can't
 * read {@code /api} — i.e. the guard is the JWT-identity requirement, not
 * statelessness.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class SessionCookieCannotAuthApiTest extends IsolatedDefaultDatasetTest
{
    @Autowired
    MockMvc mockMvc;

    /**
     * Build the {@link MockHttpSession} a browser form-login persists: a
     * {@code UsernamePasswordAuthenticationToken} stored in the HttpSession under
     * the Spring Security context key. Replaying a request with this session is
     * indistinguishable — server-side — from a browser sending only its
     * {@code JSESSIONID} cookie.
     */
    private static MockHttpSession formLoginSession()
    {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    /**
     * Control: the session IS a valid chain authentication. Without this, the
     * 401 in {@link #sessionCannotReadApiData} could be a false pass from an
     * inert/ignored session. {@code /graphiql} is gated purely by Spring's
     * {@code .authenticated()} rule (no rapla-token check), so a session that
     * satisfies the gate loads the page.
     */
    @Test
    void sessionAuthenticatesTheChain() throws Exception
    {
        mockMvc.perform(get("/graphiql/index.html").session(formLoginSession()))
                .andExpect(status().isOk());
    }

    /**
     * OQ1 core: the same live session cannot read {@code /api} data. {@code /api/users}
     * is gated {@code .authenticated()} AND calls {@code session.checkAndGetUser(request)};
     * the session carries no {@code JwtAuthenticationToken}, so no rapla user resolves and
     * the request is rejected 401 — never a 200 leaking the user list.
     */
    @Test
    void sessionCannotReadApiData() throws Exception
    {
        mockMvc.perform(get("/api/users").session(formLoginSession()))
                .andExpect(status().isUnauthorized());
    }
}
