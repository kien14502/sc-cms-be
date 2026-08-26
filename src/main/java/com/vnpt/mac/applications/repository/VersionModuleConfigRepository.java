package com.vnpt.mac.applications.repository;
import com.vnpt.mac.applications.entity.VersionModuleConfigEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface VersionModuleConfigRepository extends JpaRepository<VersionModuleConfigEntity, UUID> {
}
