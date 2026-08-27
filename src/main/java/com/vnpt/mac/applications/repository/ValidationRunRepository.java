package com.vnpt.mac.applications.repository;
import com.vnpt.mac.applications.entity.ValidationRunEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ValidationRunRepository extends JpaRepository<ValidationRunEntity, UUID> {
    Optional<ValidationRunEntity> findTopByVersionIdOrderByStartedAtDesc(UUID versionId);
}
