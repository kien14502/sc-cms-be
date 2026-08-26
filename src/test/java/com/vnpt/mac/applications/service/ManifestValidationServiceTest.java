package com.vnpt.mac.applications.service;

import com.vnpt.mac.applications.entity.FindingSeverity;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.*;

class ManifestValidationServiceTest {
    private final ManifestValidationService service = new ManifestValidationService();

    private byte[] zipOf(String... nameAndContentPairs) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (int i = 0; i < nameAndContentPairs.length; i += 2) {
                zip.putNextEntry(new ZipEntry(nameAndContentPairs[i]));
                zip.write(nameAndContentPairs[i + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    @Test void validMiniAppZipPasses() throws IOException {
        var zip = zipOf("manifest.json", "{\"name\":\"demo\"}", "index.html", "<html></html>");
        var outcome = service.validateMiniApp(zip, 10_000_000);
        assertThat(outcome.passed()).isTrue();
    }

    @Test void missingManifestFailsWithError() throws IOException {
        var zip = zipOf("index.html", "<html></html>");
        var outcome = service.validateMiniApp(zip, 10_000_000);
        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.findings()).anyMatch(f -> f.ruleCode().equals("MANIFEST_MISSING") && f.severity() == FindingSeverity.ERROR);
    }

    @Test void missingIndexHtmlFailsWithError() throws IOException {
        var zip = zipOf("manifest.json", "{\"name\":\"demo\"}");
        var outcome = service.validateMiniApp(zip, 10_000_000);
        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.findings()).anyMatch(f -> f.ruleCode().equals("INDEX_HTML_MISSING"));
    }

    @Test void oversizeZipFailsWithError() throws IOException {
        var zip = zipOf("manifest.json", "{\"name\":\"demo\"}", "index.html", "<html></html>");
        var outcome = service.validateMiniApp(zip, 5);
        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.findings()).anyMatch(f -> f.ruleCode().equals("ARTIFACT_TOO_LARGE"));
    }

    @Test void webappRequiresHttpsUrl() {
        assertThat(service.validateWebapp("http://insecure.example.com").passed()).isFalse();
        assertThat(service.validateWebapp("https://secure.example.com").passed()).isTrue();
    }

    @Test void moduleRequiresNonBlankNamespace() {
        assertThat(service.validateModule("  ").passed()).isFalse();
        assertThat(service.validateModule("com.vnpt.module").passed()).isTrue();
    }

    @Test void featureAppRequiresApkOrAabExtension() {
        assertThat(service.validateFeatureApp("app.exe", 100, 10_000_000).passed()).isFalse();
        assertThat(service.validateFeatureApp("app.apk", 100, 10_000_000).passed()).isTrue();
    }
}
