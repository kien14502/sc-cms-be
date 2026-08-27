package com.vnpt.mac.applications.repository;
import com.vnpt.mac.applications.entity.ValidationFindingEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ValidationFindingRepository extends JpaRepository<ValidationFindingEntity, UUID> {
    List<ValidationFindingEntity> findByRunId(UUID runId);
}
