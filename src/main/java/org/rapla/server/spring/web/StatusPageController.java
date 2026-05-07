package org.rapla.server.spring.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.rapla.server.servletpages.RaplaStatusPageGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@ConditionalOnBean(RaplaStatusPageGenerator.class)
public class StatusPageController
{
    private final RaplaStatusPageGenerator generator;

    public StatusPageController(RaplaStatusPageGenerator generator)
    {
        this.generator = generator;
    }

    @GetMapping("/server")
    public void server(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
    {
        generator.generatePage(request, response);
    }
}
