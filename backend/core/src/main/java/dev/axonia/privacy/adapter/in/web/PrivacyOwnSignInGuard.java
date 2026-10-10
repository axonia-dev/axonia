package dev.axonia.privacy.adapter.in.web;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import dev.axonia.shared.api.error.OwnSignInRequiredException;
import dev.axonia.shared.api.security.CurrentToken;

/**
 * Refuses every current and future handler in this module to a delegated session, before its body ever runs.
 *
 * <p>
 * Matched on the handler bean's module, {@code dev.axonia.privacy} and every package under it, rather than on a class
 * list or a URL pattern, so a controller this module gains later is covered with no list to maintain and no call to
 * remember: this module's whole surface is one thing, a person's own data-subject rights, and
 * {@link OrganizationExportController}'s whole-organization export is the most sensitive of them.
 *
 * <p>
 * Registered as its own {@link WebMvcConfigurer} rather than added to the shared {@code WebConfig}, so it stays next to
 * the controllers it guards.
 */
@Component
class PrivacyOwnSignInGuard implements HandlerInterceptor, WebMvcConfigurer {

    private static final String MODULE_PACKAGE = moduleOf(PrivacyOwnSignInGuard.class.getPackageName());

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(this);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        if (handler instanceof HandlerMethod handlerMethod && isModuleController(handlerMethod)
                && CurrentToken.isDelegated()) {
            throw new OwnSignInRequiredException();
        }
        return true;
    }

    private static String moduleOf(String packageName) {

        return String.join(".", List.of(packageName.split("\\.")).subList(0, 3));
    }

    private static boolean isModuleController(HandlerMethod handlerMethod) {

        var packageName = handlerMethod.getBeanType().getPackageName();
        return packageName.equals(MODULE_PACKAGE) || packageName.startsWith(MODULE_PACKAGE + ".");
    }
}
