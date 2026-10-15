package com.billing.repository;

import com.billing.model.GstSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GstSettingRepository extends JpaRepository<GstSetting, Long> {

    Optional<GstSetting> findBySettingKey(String settingKey);
}
