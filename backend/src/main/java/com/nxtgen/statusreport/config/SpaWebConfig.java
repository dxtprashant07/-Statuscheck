package com.nxtgen.statusreport.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves the bundled single-page app from {@code classpath:/static/} (where the
 * combined Docker build copies the built React bundle) and falls back to
 * {@code index.html} for client-side routes, so deep links / hard refreshes
 * work. REST controllers under {@code /api/**} and the actuator endpoints take
 * precedence over this resource handler, and are explicitly never rewritten to
 * the SPA shell.
 *
 * <p>Harmless when no SPA is bundled (e.g. running the backend alone in dev):
 * the resolver simply finds nothing and the request 404s as before.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // Never hijack the API or actuator — let them resolve / 404 normally.
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("actuator/")) {
                            return null;
                        }
                        Resource index = new ClassPathResource("/static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
