package com.billing.security;

import com.billing.model.AppUser;
import com.billing.model.ModuleType;
import com.billing.repository.AppUserRepository;
import com.billing.repository.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * For every authenticated /api request:
 *  - signs the user out if their tenant has been disabled,
 *  - blocks everything except /me, /change-password and /logout while the user
 *    still has to change a default password, and
 *  - blocks features (modules) the admin has not enabled for the tenant.
 */
public class PasswordChangeFilter extends OncePerRequestFilter {

    private final AppUserRepository users;
    private final TenantRepository tenants;

    public PasswordChangeFilter(AppUserRepository users, TenantRepository tenants) {
        this.users = users;
        this.tenants = tenants;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String path = req.getRequestURI();

        boolean authenticated = auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);

        if (authenticated && path.startsWith("/api/") && !path.equals("/api/auth/logout")) {
            AppUser user = users.findByUsername(auth.getName()).orElse(null);

            boolean tenantDisabled = user != null && user.getTenantId() != null
                    && !tenants.findEnabledById(user.getTenantId()).orElse(false);

            if (user == null || tenantDisabled) {
                HttpSession session = req.getSession(false);
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
                write(res, 401, "UNAUTHENTICATED", "Your account is disabled or no longer exists");
                return;
            }

            boolean allowedWhileChanging = path.equals("/api/auth/me") || path.equals("/api/auth/change-password");
            if (user.isMustChangePassword() && !allowedWhileChanging) {
                write(res, 403, "PASSWORD_CHANGE_REQUIRED", "Please change your password to continue");
                return;
            }

            if (user.getTenantId() != null) {
                List<ModuleType> required = requiredModules(path);
                if (!required.isEmpty()) {
                    List<ModuleType> enabled = tenants.findModulesById(user.getTenantId());
                    if (required.stream().noneMatch(enabled::contains)) {
                        write(res, 403, "MODULE_NOT_ENABLED", "This feature is not enabled for your account");
                        return;
                    }
                }
            }
        }
        chain.doFilter(req, res);
    }

    /** Any one of the listed modules is enough to use the URL. */
    private List<ModuleType> requiredModules(String path) {
        if (path.startsWith("/api/customers") || path.startsWith("/api/invoices")) {
            return List.of(ModuleType.INVOICING);
        }
        if (path.startsWith("/api/products")) {
            return List.of(ModuleType.INVOICING, ModuleType.INVENTORY);
        }
        if (path.startsWith("/api/inventory")) {
            return List.of(ModuleType.INVENTORY);
        }
        if (path.startsWith("/api/gst")) {
            return List.of(ModuleType.GST);
        }
        return List.of();
    }

    private void write(HttpServletResponse res, int status, String code, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
