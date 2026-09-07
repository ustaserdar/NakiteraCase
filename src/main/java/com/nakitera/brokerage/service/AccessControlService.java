package com.nakitera.brokerage.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AccessControlService {

    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    public void assertCanAccessCustomer(String customerId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required");
        }
        if (isAdmin(authentication)) {
            return;
        }
        if (!authentication.getName().equals(customerId)) {
            throw new AccessDeniedException("Customers may only access their own data");
        }
    }

    public void assertCanAccessOrder(String orderCustomerId) {
        assertCanAccessCustomer(orderCustomerId);
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLE_ADMIN::equals);
    }
}
