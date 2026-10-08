package dev.axonia.communication.internal;

import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * The one Thymeleaf bean this module owns, deliberately not the {@code spring-boot-starter-thymeleaf} starter: that
 * starter wires Thymeleaf into Spring MVC view resolution, which an API-only backend never uses. A plain
 * {@link TemplateEngine} over {@code templates/mail/} is all {@link MailRenderer} needs, and it is never handed a
 * Spring {@code Model} or a {@code RequestContext} - see the pom.xml comment on why {@code thymeleaf-spring6} is not a
 * dependency either.
 *
 * <p>
 * The resolver sets no template mode: {@link ClassLoaderTemplateResolver} infers it from the file extension, so
 * {@code <name>.html} renders as markup and {@code <name>.txt} renders in Thymeleaf's TEXT mode, in the same engine,
 * with no per-call configuration.
 */
@Configuration
class MailTemplateConfig {

    @Bean
    TemplateEngine mailTemplateEngine() {

        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/mail/");
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        var engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
