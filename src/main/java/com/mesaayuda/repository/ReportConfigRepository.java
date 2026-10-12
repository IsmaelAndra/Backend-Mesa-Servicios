package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.ReportConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportConfigRepository extends JpaRepository<ReportConfig, UUID> {

    List<ReportConfig> findByDeletedReportFalseOrderByTitleReportAsc();

    List<ReportConfig> findByDeletedReportFalseAndActiveReportTrueOrderByTitleReportAsc();

    Optional<ReportConfig> findTopByDeletedReportFalseOrderByIdReportAsc();

    boolean existsByNameReport(String nameReport);

    boolean existsByNameReportAndIdReportNot(String nameReport, UUID idReport);
}