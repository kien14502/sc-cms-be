package com.vnpt.mac.applications.repository;
import com.vnpt.mac.applications.entity.ReviewSubmissionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ReviewSubmissionRepository extends JpaRepository<ReviewSubmissionEntity, UUID> {
    Optional<ReviewSubmissionEntity> findTopByVersionIdOrderBySubmittedAtDesc(UUID versionId);
    List<ReviewSubmissionEntity> findByVersionIdOrderBySubmittedAtAsc(UUID versionId);
}
