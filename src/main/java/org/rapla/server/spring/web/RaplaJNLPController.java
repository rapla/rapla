package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.rapla.server.servletpages.RaplaJNLPPageGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Preserves the JNLP launch URL: /rapla/raplaclient and /rapla/raplaclient.jnlp.
 * (HARD CONSTRAINT — see PRD URL Path Preservation.)
 */
@RestController
@ConditionalOnBean(RaplaJNLPPageGenerator.class)
public class RaplaJNLPController
{
    private final RaplaJNLPPageGenerator generator;

    public RaplaJNLPController(RaplaJNLPPageGenerator generator)
    {
        this.generator = generator;
    }

    @GetMapping("/raplaclient")
    public void raplaclient(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        generator.generatePage(request, response, "");
    }

    @GetMapping("/raplaclient.jnlp")
    public void raplaclientJnlp(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        generator.generatePage(request, response, ".jnlp");
    }
}
