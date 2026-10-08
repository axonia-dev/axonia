package dev.axonia.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import ch.qos.logback.core.Appender;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Holds {@code logback-spring.xml} to what Joran would do with it, without booting an application.
 *
 * <p>
 * Declaring that file replaces Boot's entire default logging setup, and the two ways it can go wrong are both invisible
 * until something starts: a class named in it that is not on the classpath, and a nested element with no matching
 * setter on the class it configures. Either one fails logging initialization at boot rather than at build time, and the
 * OpenTelemetry appender is the exposed one - it comes from an {@code -alpha} artifact outside Spring Boot's BOM, so an
 * upgrade renaming {@code captureMdcAttributes} would otherwise be found in production.
 *
 * <p>
 * The profile split is asserted here too, because it is a decision rather than an accident: PROD is the only profile
 * that exports, and every other profile keeps the plain human console a developer reads. What the PROD profile's own
 * properties must supply is {@link LogbackProductionProfileTest}'s.
 */
class LogbackConfigurationTest {

    private static final String DEFAULT_ENCODER = "ch.qos.logback.classic.encoder.PatternLayoutEncoder";

    private static final List<String> CONFIGURED_ELEMENTS = List.of("filter", "encoder");

    private final Element configuration = parseLogbackSpringXml();

    @Test
    void everyClassNamedInTheConfigurationIsOnTheClasspathAndEveryNestedElementHasASetter() {

        var appenders = descendants(configuration, "appender");
        assertThat(appenders).isNotEmpty();
        appenders.forEach(appender -> {
            assertThat(load(appender.getAttribute("class"))).isAssignableTo(Appender.class);
            assertConfigurable(appender, appender.getAttribute("class"));
            children(appender).stream().filter(child -> CONFIGURED_ELEMENTS.contains(child.getTagName()))
                    .forEach(child -> assertConfigurable(child, encoderOrFilterClass(child)));
        });
    }

    @Test
    void prodIsTheOnlyProfileThatShipsLogsToTheCollector() {

        assertThat(rootAppenderRefsOf("PROD")).containsExactly("CONSOLE", "OTEL");
        assertThat(rootAppenderRefsOf("!PROD")).containsExactly("CONSOLE");
    }

    /**
     * The appender's MDC capture is an allowlist: a key not named here never leaves the process. The tenant rides on
     * every log record through exactly these keys, so widening this list is a decision about what reaches a third-party
     * backend, never a formatting detail.
     */
    @Test
    void onlyTheAllowlistedMdcKeysAreEverCaptured() {

        var otel = descendants(configuration, "appender").stream()
                .filter(appender -> "OTEL".equals(appender.getAttribute("name"))).findFirst().orElseThrow();

        assertThat(textOfChild(otel, "captureMdcAttributes")).isEqualTo("organization_id,user_id");
        assertThat(textOfChild(otel, "captureExperimentalAttributes")).isEqualTo("false");
    }

    private List<String> rootAppenderRefsOf(String profile) {

        var branch = descendants(configuration, "springProfile").stream()
                .filter(element -> profile.equals(element.getAttribute("name"))).findFirst().orElseThrow();

        return descendants(branch, "appender-ref").stream().map(element -> element.getAttribute("ref")).toList();
    }

    private static void assertConfigurable(Element element, String className) {

        var type = load(className);
        var setters = Arrays.stream(type.getMethods()).filter(method -> method.getParameterCount() == 1)
                .map(java.lang.reflect.Method::getName).toList();

        children(element).stream().map(Element::getTagName).filter(tag -> !CONFIGURED_ELEMENTS.contains(tag))
                .filter(tag -> !"appender-ref".equals(tag))
                .forEach(tag -> assertThat(setters).as("%s configures <%s>", className, tag)
                        .contains("set" + Character.toUpperCase(tag.charAt(0)) + tag.substring(1)));
    }

    private static String encoderOrFilterClass(Element element) {

        var declared = element.getAttribute("class");
        return declared.isEmpty() ? DEFAULT_ENCODER : declared;
    }

    private static Class<?> load(String className) {

        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new AssertionError(className + " is named in logback-spring.xml but is not on the classpath", e);
        }
    }

    private static String textOfChild(Element parent, String tag) {

        return children(parent).stream().filter(child -> tag.equals(child.getTagName())).findFirst().orElseThrow()
                .getTextContent();
    }

    private static List<Element> children(Element parent) {

        return elements(parent.getChildNodes());
    }

    private static List<Element> descendants(Element parent, String tag) {

        return elements(parent.getElementsByTagName(tag));
    }

    private static List<Element> elements(NodeList nodes) {

        var elements = new ArrayList<Element>();
        for (var i = 0; i < nodes.getLength(); i++) {
            var node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                elements.add((Element) node);
            }
        }
        return elements;
    }

    private static Element parseLogbackSpringXml() {

        try (var stream = new ClassPathResource("logback-spring.xml").getInputStream()) {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document document = factory.newDocumentBuilder().parse(stream);
            return document.getDocumentElement();
        } catch (Exception e) {
            throw new AssertionError("logback-spring.xml could not be parsed", e);
        }
    }
}
