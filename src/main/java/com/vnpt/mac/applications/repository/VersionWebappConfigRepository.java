package com.vnpt.mac.applications.repository;
import com.vnpt.mac.applications.entity.VersionWebappConfigEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface VersionWebappConfigRepository extends JpaRepository<VersionWebappConfigEntity, UUID> {
}
