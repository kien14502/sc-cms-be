package com.vnpt.mac.applications.service;

import com.vnpt.mac.applications.entity.FindingSeverity;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.stereotype.Service;

@Service
public class ManifestValidationService {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Finding(String ruleCode, FindingSeverity severity, String message) {}

    public record ValidationOutcome(boolean passed, List<Finding> findings) {
        static ValidationOutcome of(List<Finding> findings) {
            boolean passed = findings.stream().noneMatch(f -> f.severity() == FindingSeverity.ERROR);
            return new ValidationOutcome(passed, findings);
        }
    }

    public ValidationOutcome validateMiniApp(byte[] zipBytes, long maxBytes) {
        var findings = new ArrayList<Finding>();
        if (zipBytes.length > maxBytes) {
            findings.add(new Finding("ARTIFACT_TOO_LARGE", FindingSeverity.ERROR,
                    "Kích thước " + zipBytes.length + " vượt giới hạn " + maxBytes + " bytes"));
        }
        boolean hasManifest = false;
        boolean hasIndexHtml = false;
        boolean manifestValidJson = false;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals("manifest.json")) {
                    hasManifest = true;
                    byte[] manifestBytes = zip.readAllBytes();
                    try {
                        var node = MAPPER.readTree(manifestBytes);
                        manifestValidJson = node != null && node.isObject();
                    } catch (Exception e) {
                        manifestValidJson = false;
                    }
                } else if (entry.getName().equals("index.html")) {
                    hasIndexHtml = true;
                }
            }
        } catch (IOException e) {
            findings.add(new Finding("ARTIFACT_NOT_A_ZIP", FindingSeverity.ERROR, "File không phải ZIP hợp lệ"));
            return ValidationOutcome.of(findings);
        }
        if (!hasManifest) findings.add(new Finding("MANIFEST_MISSING", FindingSeverity.ERROR, "Thiếu manifest.json ở gốc ZIP"));
        else if (!manifestValidJson) findings.add(new Finding("MANIFEST_INVALID_JSON", FindingSeverity.ERROR, "manifest.json không phải JSON object hợp lệ"));
        if (!hasIndexHtml) findings.add(new Finding("INDEX_HTML_MISSING", FindingSeverity.ERROR, "Thiếu index.html ở gốc ZIP"));
        if (findings.isEmpty()) findings.add(new Finding("MINIAPP_VALIDATION_OK", FindingSeverity.INFO, "Manifest và index.html hợp lệ"));
        return ValidationOutcome.of(findings);
    }

    public ValidationOutcome validateFeatureApp(String filename, long sizeBytes, long maxBytes) {
        var findings = new ArrayList<Finding>();
        String lower = filename == null ? "" : filename.toLowerCase();
        if (!lower.endsWith(".apk") && !lower.endsWith(".aab"))
            findings.add(new Finding("INVALID_EXTENSION", FindingSeverity.ERROR, "Yêu cầu file .apk hoặc .aab"));
        if (sizeBytes > maxBytes)
            findings.add(new Finding("ARTIFACT_TOO_LARGE", FindingSeverity.ERROR, "Kích thước vượt giới hạn " + maxBytes + " bytes"));
        findings.add(new Finding("SIGNATURE_CHECK_DEFERRED", FindingSeverity.INFO,
                "Xác minh chữ ký số chưa được triển khai ở milestone này"));
        return ValidationOutcome.of(findings);
    }

    public ValidationOutcome validateWebapp(String destinationUrl) {
        var findings = new ArrayList<Finding>();
        boolean valid = destinationUrl != null && destinationUrl.matches("^https://[\\w.-]+(:\\d+)?(/.*)?$");
        if (!valid) findings.add(new Finding("INVALID_DESTINATION_URL", FindingSeverity.ERROR,
                "destinationUrl phải bắt đầu bằng https:// và đúng định dạng URL"));
        else findings.add(new Finding("WEBAPP_URL_OK", FindingSeverity.INFO, "URL hợp lệ (chưa kiểm tra SSL/health thực tế)"));
        return ValidationOutcome.of(findings);
    }

    public ValidationOutcome validateModule(String moduleNamespace) {
        var findings = new ArrayList<Finding>();
        if (moduleNamespace == null || moduleNamespace.isBlank())
            findings.add(new Finding("MODULE_NAMESPACE_REQUIRED", FindingSeverity.ERROR, "moduleNamespace không được để trống"));
        else findings.add(new Finding("MODULE_NAMESPACE_OK", FindingSeverity.INFO, "moduleNamespace hợp lệ"));
        return ValidationOutcome.of(findings);
    }
}
