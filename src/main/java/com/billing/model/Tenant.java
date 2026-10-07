package com.billing.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

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

    // Invoice header image (PNG/JPG, max 1 MB)
    @Lob
    @Column(name = "banner")
    private byte[] banner;

    private String bannerContentType;

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
