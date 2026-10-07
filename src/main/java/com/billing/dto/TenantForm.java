package com.billing.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class TenantForm {

    @NotBlank(message = "Tenant name is required")
    @Size(max = 150, message = "Tenant name is too long")
    private String name;

    @NotBlank(message = "Contact person name is required")
    @Size(max = 100, message = "Contact name is too long")
    private String contactName;

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Mobile must be exactly 10 digits")
    private String mobile;

    @Email(message = "Email should be valid")
    private String email;

    private String address;

    // Bank details (invoice footer)
    private String accountHolder;
    private String bankName;
    private String accountNumber;
    private String ifsc;
    private String branch;
    private String upiId;

    // Login username: required when creating, ignored when editing
    @Pattern(regexp = "^$|^[A-Za-z0-9._-]{4,30}$",
            message = "Username must be 4-30 characters: letters, digits, dot, dash or underscore")
    private String username;

    // Invoice header image
    private MultipartFile banner;
    private boolean removeBanner;
}
