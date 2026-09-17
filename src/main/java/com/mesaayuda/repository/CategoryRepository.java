package com.mesaayuda.repository;

import com.mesaayuda.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long>, JpaSpecificationExecutor<Category> {
    Optional<Category> findByNameCategory(String nameCategory);
    boolean existsByNameCategory(String nameCategory);
    List<Category> findByDeletedCategoryFalse();
    List<Category> findByTeamCategoryIdTeamAndDeletedCategoryFalse(Long teamId);
    Optional<Category> findFirstByDeletedCategoryFalseOrderByIdCategoryAsc();
}