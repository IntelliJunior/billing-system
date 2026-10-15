package com.billing.repository;

import com.billing.model.GstRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GstRateRepository extends JpaRepository<GstRate, Long> {

    List<GstRate> findByActiveTrueOrderByRatePercentAsc();

    List<GstRate> findAllByOrderByRatePercentAscValidFromAsc();
}
