package com.billing.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String contactName;

    @Column(nullable = false, length = 15)
    private String mobile;

    private String email;

    private String address;

    @Column(nullable = false)
    private boolean enabled = true;

    // Features this tenant may use (set by the admin)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tenant_modules", joinColumns = @JoinColumn(name = "tenant_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "module_name", nullable = false)
    private Set<ModuleType> modules = new HashSet<>();

    // Invoice header image (PNG/JPG, max 1 MB)
    @Lob
    @Column(name = "banner")
    private byte[] banner;

    private String bannerContentType;

    // GST profile (used when the GST feature is on)
    @Column(length = 15)
    private String gstin;

    @Column(length = 2)
    private String stateCode;

    @Enumerated(EnumType.STRING)
    private FilingFrequency filingFrequency = FilingFrequency.MONTHLY;

    // Short code in front of invoice numbers, such as IJ in IJ/26-27/0001 (unique per tenant)
    @Column(length = 4, unique = true)
    private String invoicePrefix;

    // Bank details (invoice footer)
    private String accountHolder;
    private String bankName;
    private String accountNumber;
    private String ifsc;
    private String branch;
    private String upiId;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
