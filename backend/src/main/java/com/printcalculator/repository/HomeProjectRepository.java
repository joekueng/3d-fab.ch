package com.printcalculator.repository;

import com.printcalculator.entity.HomeProject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HomeProjectRepository extends JpaRepository<HomeProject, UUID> {
    List<HomeProject> findAllByOrderBySortOrderAscCreatedAtAsc();

    List<HomeProject> findByIsActiveTrueOrderBySortOrderAscCreatedAtAsc();

    Optional<HomeProject> findBySlugIgnoreCase(String slug);
}
