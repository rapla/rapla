package org.rapla.server.spring;

import java.io.IOException;
import java.time.Duration;

import org.rapla.server.spring.web.PatchablePage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

@Configuration
public class SpaResourceConfig implements WebMvcConfigurer
{
    // PRD 125: the production build names js/css/fonts by content hash (outputHashing: all),
    // so they never change under a given name; index.html names them and must be revalidated.
    // Any root js/css name with a hyphen counts as hashed (chunk hashes are base64url: - and _).
    private static final CacheControl IMMUTABLE = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();
    private static final String HASHED_FILE = "{file:[\\w-]+-[\\w-]+\\.(?:js|css)}";

    private final String devDir;
    private final String patchDir;

    public SpaResourceConfig(@Value("${rapla.spa.dev-dir:../rapla-angular/dist/rapla-angular/browser/}") String devDir,
                             RaplaServerProperties serverProperties)
    {
        this.devDir = devDir;
        this.patchDir = serverProperties.getPatchDir();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry)
    {
        // PRD 133 D1: the patch directory's top level holds stored views and documents and is never
        // served; its namespace folders serve only these file types. rapla/ feeds the fixed URLs.
        String core = "file:" + patchDir + "/" + PatchablePage.CORE_NAMESPACE + "/";
        registry.addResourceHandler("/{file:favicon\\.ico}", "/app/{file:favicon\\.ico}", "/images/{file:favicon\\.ico}")
                .addResourceLocations(core, "classpath:/static/images/")
                .setCacheControl(CacheControl.noCache());
        registry.addResourceHandler("/app/{file:spa\\.css}")
                .addResourceLocations(core, "file:" + devDir, "classpath:/static/app/")
                .setCacheControl(CacheControl.noCache());
        registry.addResourceHandler("/patch/{ns:[a-z0-9_-]+}/{file:[\\w.-]+\\.(?:css|svg|png|jpg|ico|woff2)}")
                .addResourceLocations("file:" + patchDir + "/")
                .setCacheControl(CacheControl.noCache());
        registry.addResourceHandler("/app/" + HASHED_FILE)
                .addResourceLocations("file:" + devDir, "classpath:/static/app/")
                .setCacheControl(IMMUTABLE);
        registry.addResourceHandler("/app/media/**")
                .addResourceLocations("file:" + devDir + "media/", "classpath:/static/app/media/")
                .setCacheControl(IMMUTABLE);
        registry.addResourceHandler("/app", "/app/", "/app/**")
                .addResourceLocations(
                        "file:" + devDir,
                        "classpath:/static/app/")
                .setCacheControl(CacheControl.noCache())
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
