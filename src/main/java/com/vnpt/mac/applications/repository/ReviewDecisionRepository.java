package com.vnpt.mac.applications.repository;
import com.vnpt.mac.applications.entity.ReviewDecisionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ReviewDecisionRepository extends JpaRepository<ReviewDecisionEntity, UUID> {
    Optional<ReviewDecisionEntity> findBySubmissionId(UUID submissionId);
}
