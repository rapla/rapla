package org.rapla.server.spring.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.rapla.server.servletpages.RaplaIndexPageGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@ConditionalOnBean(RaplaIndexPageGenerator.class)
public class IndexPageController
{
    private final RaplaIndexPageGenerator generator;

    public IndexPageController(RaplaIndexPageGenerator generator)
    {
        this.generator = generator;
    }

    @GetMapping({ "/", "/index" })
    public void index(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
    {
        generator.generatePage(request, response);
    }
}
