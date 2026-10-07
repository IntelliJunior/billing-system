package com.billing.service;

import com.billing.dto.TenantCredentials;
import com.billing.dto.TenantForm;
import com.billing.dto.TenantResponse;
import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.model.AppUser;
import com.billing.model.Role;
import com.billing.model.Tenant;
import com.billing.repository.AppUserRepository;
import com.billing.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TenantService {

    private static final long MAX_BANNER_BYTES = 1_048_576L; // 1 MB

    private final TenantRepository tenantRepository;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public record BannerData(byte[] bytes, String contentType) {
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> list() {
        Map<Long, String> usernames = userRepository.findByRole(Role.TENANT).stream()
                .filter(u -> u.getTenantId() != null)
                .collect(Collectors.toMap(AppUser::getTenantId, AppUser::getUsername, (a, b) -> a));
        return tenantRepository.findAll(Sort.by("name")).stream()
                .map(t -> toResponse(t, usernames.get(t.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public TenantResponse get(Long id) {
        Tenant t = find(id);
        return toResponse(t, usernameOf(id));
    }

    @Transactional
    public TenantCredentials create(TenantForm f) {
        String username = clean(f.getUsername());
        if (username == null) {
            throw new BadRequestException("Username is required");
        }
        username = username.toLowerCase();
        if (userRepository.existsByUsername(username)) {
            throw new BadRequestException("This username is already taken");
        }
        if (tenantRepository.existsByNameIgnoreCase(f.getName().trim())) {
            throw new BadRequestException("A tenant with this name already exists");
        }

        Tenant tenant = new Tenant();
        apply(tenant, f);
        tenantRepository.save(tenant);

        String defaultPassword = defaultPassword(tenant);
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(defaultPassword));
        user.setRole(Role.TENANT);
        user.setTenantId(tenant.getId());
        user.setMustChangePassword(true);
        userRepository.save(user);

        return new TenantCredentials(toResponse(tenant, username), username, defaultPassword);
    }

    @Transactional
    public TenantResponse update(Long id, TenantForm f) {
        Tenant tenant = find(id);
        if (tenantRepository.existsByNameIgnoreCaseAndIdNot(f.getName().trim(), id)) {
            throw new BadRequestException("A tenant with this name already exists");
        }
        apply(tenant, f);
        tenantRepository.save(tenant);
        return toResponse(tenant, usernameOf(id));
    }

    @Transactional
    public TenantResponse setEnabled(Long id, boolean enabled) {
        Tenant tenant = find(id);
        tenant.setEnabled(enabled);
        tenantRepository.save(tenant);
        return toResponse(tenant, usernameOf(id));
    }

    @Transactional
    public TenantCredentials resetPassword(Long id) {
        Tenant tenant = find(id);
        AppUser user = userRepository.findFirstByTenantId(id)
                .orElseThrow(() -> new ResourceNotFoundException("No login found for this tenant"));
        String defaultPassword = defaultPassword(tenant);
        user.setPasswordHash(passwordEncoder.encode(defaultPassword));
        user.setMustChangePassword(true);
        userRepository.save(user);
        return new TenantCredentials(toResponse(tenant, user.getUsername()), user.getUsername(), defaultPassword);
    }

    @Transactional(readOnly = true)
    public BannerData getBanner(Long id) {
        Tenant tenant = find(id);
        if (tenant.getBanner() == null) {
            throw new ResourceNotFoundException("This tenant has no banner");
        }
        return new BannerData(tenant.getBanner(), tenant.getBannerContentType());
    }

    // ---------------------------------------------------------------- helpers

    private Tenant find(Long id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with id: " + id));
    }

    private String usernameOf(Long tenantId) {
        return userRepository.findFirstByTenantId(tenantId).map(AppUser::getUsername).orElse(null);
    }

    private void apply(Tenant t, TenantForm f) {
        t.setName(f.getName().trim());
        t.setContactName(f.getContactName().trim());
        t.setMobile(f.getMobile().trim());
        t.setEmail(clean(f.getEmail()));
        t.setAddress(clean(f.getAddress()));
        t.setAccountHolder(clean(f.getAccountHolder()));
        t.setBankName(clean(f.getBankName()));
        t.setAccountNumber(clean(f.getAccountNumber()));
        t.setIfsc(clean(f.getIfsc()) == null ? null : clean(f.getIfsc()).toUpperCase());
        t.setBranch(clean(f.getBranch()));
        t.setUpiId(clean(f.getUpiId()));

        if (f.isRemoveBanner()) {
            t.setBanner(null);
            t.setBannerContentType(null);
        }
        MultipartFile file = f.getBanner();
        if (file != null && !file.isEmpty()) {
            setBanner(t, file);
        }
    }

    private void setBanner(Tenant t, MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length > MAX_BANNER_BYTES) {
                throw new BadRequestException("Banner must be 1 MB or smaller");
            }
            String type = file.getContentType();
            if (!"image/png".equals(type) && !"image/jpeg".equals(type)) {
                throw new BadRequestException("Banner must be a PNG or JPG image");
            }
            if (ImageIO.read(new ByteArrayInputStream(bytes)) == null) {
                throw new BadRequestException("The banner file is not a valid image");
            }
            t.setBanner(bytes);
            t.setBannerContentType(type);
        } catch (IOException e) {
            throw new BadRequestException("Could not read the banner image");
        }
    }

    /** Default password = first name of the contact person + last 5 digits of the mobile. */
    private String defaultPassword(Tenant t) {
        String firstName = t.getContactName().trim().split("\\s+")[0];
        String mobile = t.getMobile();
        String last5 = mobile.substring(Math.max(0, mobile.length() - 5));
        return firstName + last5;
    }

    private String clean(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private TenantResponse toResponse(Tenant t, String username) {
        return new TenantResponse(
                t.getId(), t.getName(), t.getContactName(), t.getMobile(), t.getEmail(), t.getAddress(),
                t.isEnabled(), t.getBanner() != null,
                t.getAccountHolder(), t.getBankName(), t.getAccountNumber(), t.getIfsc(), t.getBranch(), t.getUpiId(),
                username, t.getCreatedAt(), t.getUpdatedAt());
    }
}
