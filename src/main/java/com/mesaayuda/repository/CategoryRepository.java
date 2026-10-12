package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID>, JpaSpecificationExecutor<Category> {
    Optional<Category> findByNameCategory(String nameCategory);
    boolean existsByNameCategory(String nameCategory);
    List<Category> findByDeletedCategoryFalse();
    List<Category> findByTeamCategoryIdTeamAndDeletedCategoryFalse(UUID teamId);
    Optional<Category> findFirstByDeletedCategoryFalseOrderByCreatedAtCategoryAsc();
}