package com.vnpt.mac.applications.entity;

import com.vnpt.mac.common.exception.BusinessException;
import com.vnpt.mac.common.exception.ErrorCode;
import com.vnpt.mac.common.persistence.BaseAuditEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "app_versions")
public class AppVersionEntity extends BaseAuditEntity {
    @Id
    private UUID id;

    @Column(name = "app_id", nullable = false)
    private UUID appId;

    @Column(name = "partner_id")
    private UUID partnerId;

    @Column(name = "version_code", nullable = false)
    private int versionCode;

    @Column(name = "version_name", nullable = false, length = 50)
    private String versionName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VersionStatus status;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "package_name", nullable = false)
    private String packageName;

    @Column(name = "description_short", length = 500)
    private String descriptionShort;

    @Column(name = "description_long")
    private String descriptionLong;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "supported_languages", nullable = false, columnDefinition = "jsonb")
    private List<String> supportedLanguages = List.of();

    @Column(name = "review_round", nullable = false)
    private int reviewRound;

    @ManyToMany
    @JoinTable(name = "app_version_categories",
            joinColumns = @JoinColumn(name = "version_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    private Set<AppCategoryEntity> categories = new HashSet<>();

    protected AppVersionEntity() {}

    public static AppVersionEntity create(UUID appId, UUID partnerId, int versionCode, String versionName,
                                          String displayName, String packageName, String descriptionShort,
                                          String descriptionLong, List<String> supportedLanguages) {
        var entity = new AppVersionEntity();
        entity.id = UUID.randomUUID();
        entity.appId = appId;
        entity.partnerId = partnerId;
        entity.versionCode = versionCode;
        entity.versionName = versionName.trim();
        entity.status = VersionStatus.DRAFT;
        entity.displayName = displayName.trim();
        entity.packageName = packageName.trim();
        entity.descriptionShort = descriptionShort;
        entity.descriptionLong = descriptionLong;
        entity.supportedLanguages = supportedLanguages == null ? List.of() : supportedLanguages;
        entity.reviewRound = 0;
        return entity;
    }

    public void updateMetadata(String displayName, String descriptionShort, String descriptionLong, List<String> supportedLanguages) {
        this.displayName = displayName.trim();
        this.descriptionShort = descriptionShort;
        this.descriptionLong = descriptionLong;
        this.supportedLanguages = supportedLanguages == null ? List.of() : supportedLanguages;
    }

    public void replaceCategories(Set<AppCategoryEntity> newCategories) {
        categories.clear();
        categories.addAll(newCategories);
    }

    public void assertEditable() {
        if (status != VersionStatus.DRAFT && status != VersionStatus.CHANGES_REQUESTED)
            throw new BusinessException(ErrorCode.VERSION_NOT_EDITABLE, "Version ở trạng thái " + status + " không thể chỉnh sửa");
    }

    public void submit() {
        assertEditable();
        status = VersionStatus.IN_REVIEW;
        reviewRound += 1;
    }

    public void approve() {
        requireStatus(VersionStatus.IN_REVIEW);
        status = VersionStatus.APPROVED;
    }

    public void reject() {
        requireStatus(VersionStatus.IN_REVIEW);
        status = VersionStatus.REJECTED;
    }

    public void requestChanges() {
        requireStatus(VersionStatus.IN_REVIEW);
        status = VersionStatus.CHANGES_REQUESTED;
    }

    private void requireStatus(VersionStatus expected) {
        if (status != expected) throw new BusinessException(ErrorCode.VERSION_STATUS_INVALID,
                "Không thể chuyển Version từ " + status);
    }

    public UUID getId() { return id; }
    public UUID getAppId() { return appId; }
    public UUID getPartnerId() { return partnerId; }
    public int getVersionCode() { return versionCode; }
    public String getVersionName() { return versionName; }
    public VersionStatus getStatus() { return status; }
    public String getDisplayName() { return displayName; }
    public String getPackageName() { return packageName; }
    public String getDescriptionShort() { return descriptionShort; }
    public String getDescriptionLong() { return descriptionLong; }
    public List<String> getSupportedLanguages() { return supportedLanguages; }
    public int getReviewRound() { return reviewRound; }
    public Set<AppCategoryEntity> getCategories() { return Set.copyOf(categories); }
}
