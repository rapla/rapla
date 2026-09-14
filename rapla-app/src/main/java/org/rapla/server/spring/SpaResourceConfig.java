package org.rapla.server.spring;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

@Configuration
public class SpaResourceConfig implements WebMvcConfigurer
{
    @Value("${rapla.spa.dev-dir:../rapla-angular/dist/rapla-angular/browser/}")
    private String devDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry)
    {
        registry.addResourceHandler("/app", "/app/", "/app/**")
                .addResourceLocations(
                        "file:" + devDir,
                        "classpath:/static/app/")
                .setCachePeriod(0)
                .resourceChain(false)
                .addResolver(new PathResourceResolver()
                {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException
                    {
                        if (resourcePath != null && !resourcePath.isEmpty() && !resourcePath.endsWith("/"))
                        {
                            Resource requested = location.createRelative(resourcePath);
                            if (requested.exists() && requested.isReadable())
                            {
                                return requested;
                            }
                        }
                        Resource indexHtml = location.createRelative("index.html");
                        return (indexHtml.exists() && indexHtml.isReadable()) ? indexHtml : null;
                    }
                });
    }
}
