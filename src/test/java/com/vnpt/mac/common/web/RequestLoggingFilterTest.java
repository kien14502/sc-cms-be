package com.vnpt.mac.common.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for the access-log filter that records every incoming API call (method, path,
 * status, duration). Uses a Logback ListAppender attached directly to the filter's logger —
 * there is no existing precedent for filter-level tests in this codebase (BearerAuthenticationFilter
 * has none), so this establishes the pattern for asserting on log output rather than behavior.
 */
class RequestLoggingFilterTest {
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger("com.vnpt.mac.http");
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void logsMethodPathAndStatusForASuccessfulRequest() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/applications");
        var response = new MockHttpServletResponse();
        response.setStatus(200);
        FilterChain chain = (req, res) -> {};

        new RequestLoggingFilter().doFilter(request, response, chain);

        assertThat(appender.list).hasSize(1);
        var message = appender.list.get(0).getFormattedMessage();
        assertThat(message).contains("GET").contains("/api/v1/applications").contains("200");
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    void logsAtWarnLevelForA4xxResponse() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/applications/x");
        var response = new MockHttpServletResponse();
        response.setStatus(404);
        FilterChain chain = (req, res) -> {};

        new RequestLoggingFilter().doFilter(request, response, chain);

        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    void logsAtErrorLevelForA5xxResponse() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/applications");
        var response = new MockHttpServletResponse();
        response.setStatus(500);
        FilterChain chain = (req, res) -> {};

        new RequestLoggingFilter().doFilter(request, response, chain);

        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.ERROR);
    }

    @Test
    void stillLogsBeforeRethrowingWhenTheChainThrows() {
        var request = new MockHttpServletRequest("POST", "/api/v1/applications");
        var response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { throw new RuntimeException("boom"); };

        assertThrows(RuntimeException.class, () -> new RequestLoggingFilter().doFilter(request, response, chain));

        assertThat(appender.list).hasSize(1);
    }

    @Test
    void includesTheQueryStringWhenPresent() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/applications");
        request.setQueryString("status=DRAFT&page=0");
        var response = new MockHttpServletResponse();
        response.setStatus(200);
        FilterChain chain = (req, res) -> {};

        new RequestLoggingFilter().doFilter(request, response, chain);

        assertThat(appender.list.get(0).getFormattedMessage()).contains("status=DRAFT&page=0");
    }
}
