package com.vnpt.mac.applications.repository;

import com.vnpt.mac.applications.entity.AppCategoryEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppCategoryRepository extends JpaRepository<AppCategoryEntity, UUID> {
    List<AppCategoryEntity> findByCodeIn(Collection<String> codes);
}
