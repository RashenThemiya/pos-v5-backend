package com.pos.system.service;

import com.pos.system.dto.authority.AuthorityGroupResponse;
import com.pos.system.dto.authority.AuthorityResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthorityService {

    private static final String CONTROLLER_PACKAGE = "com.pos.system.controller";
    private static final String ALL_PRIVILEGES = "ALL_PRIVILEGES";
    private static final Pattern AUTHORITY_PATTERN = Pattern.compile("has(?:Any)?Authority\\s*\\(([^)]*)\\)");
    private static final Pattern QUOTED_VALUE_PATTERN = Pattern.compile("['\"]([^'\"]+)['\"]");

    private final ApplicationContext applicationContext;

    public List<AuthorityGroupResponse> getAllAuthorities() {
        Set<String> authorityCodes = new TreeSet<>();
        authorityCodes.add(ALL_PRIVILEGES);

        Map<String, Object> controllerBeans = applicationContext.getBeansWithAnnotation(RestController.class);

        for (Object controllerBean : controllerBeans.values()) {
            Class<?> controllerClass = AopUtils.getTargetClass(controllerBean);

            if (!controllerClass.getPackageName().startsWith(CONTROLLER_PACKAGE)) {
                continue;
            }

            collectAuthorities(controllerClass, authorityCodes);
        }

        return groupAuthorities(authorityCodes);
    }

    private void collectAuthorities(Class<?> controllerClass, Set<String> authorityCodes) {
        PreAuthorize classPreAuthorize = AnnotatedElementUtils.findMergedAnnotation(controllerClass, PreAuthorize.class);
        if (classPreAuthorize != null) {
            extractAuthorities(classPreAuthorize.value(), authorityCodes);
        }

        for (Method method : controllerClass.getDeclaredMethods()) {
            PreAuthorize methodPreAuthorize = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
            if (methodPreAuthorize != null) {
                extractAuthorities(methodPreAuthorize.value(), authorityCodes);
            }
        }
    }

    private void extractAuthorities(String expression, Set<String> authorityCodes) {
        Matcher authorityMatcher = AUTHORITY_PATTERN.matcher(expression);

        while (authorityMatcher.find()) {
            String arguments = authorityMatcher.group(1);
            Matcher valueMatcher = QUOTED_VALUE_PATTERN.matcher(arguments);

            while (valueMatcher.find()) {
                authorityCodes.add(valueMatcher.group(1));
            }
        }
    }

    private List<AuthorityGroupResponse> groupAuthorities(Set<String> authorityCodes) {
        Map<String, List<AuthorityResponse>> grouped = new TreeMap<>();

        for (String code : authorityCodes) {
            String module = getModule(code);
            grouped.computeIfAbsent(module, key -> new ArrayList<>())
                    .add(new AuthorityResponse(code, toLabel(code)));
        }

        List<AuthorityGroupResponse> response = new ArrayList<>();

        if (grouped.containsKey("SYSTEM")) {
            response.add(new AuthorityGroupResponse("SYSTEM", sortAuthorities(grouped.remove("SYSTEM"))));
        }

        grouped.forEach((module, authorities) ->
                response.add(new AuthorityGroupResponse(module, sortAuthorities(authorities)))
        );

        return response;
    }

    private List<AuthorityResponse> sortAuthorities(List<AuthorityResponse> authorities) {
        return authorities.stream()
                .sorted(Comparator.comparing(AuthorityResponse::getCode))
                .toList();
    }

    private String getModule(String code) {
        if (ALL_PRIVILEGES.equals(code)) {
            return "SYSTEM";
        }

        int separatorIndex = code.indexOf('_');
        if (separatorIndex <= 0) {
            return "OTHER";
        }

        return code.substring(0, separatorIndex);
    }

    private String toLabel(String code) {
        String[] parts = code.toLowerCase().split("_");
        List<String> labelParts = new ArrayList<>();

        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }

            labelParts.add(part.substring(0, 1).toUpperCase() + part.substring(1));
        }

        return String.join(" ", labelParts);
    }
}
