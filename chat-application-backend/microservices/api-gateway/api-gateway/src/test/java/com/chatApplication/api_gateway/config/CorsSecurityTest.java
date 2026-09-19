package com.chatApplication.api_gateway.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security tests for CORS origin restriction (KAN-15).
 * <p>
 * Tests the CORS configuration in application.yml directly by parsing
 * the YAML file. This avoids Spring context issues with downstream
 * services not running (Spring Cloud Gateway CORS only applies to
 * matched routes).
 * <p>
 * Spring Cloud Gateway applies CORS declaratively via the globalcors
 * section in application.yml — there is no CorsConfigurationSource bean
 * to autowire. The CORS filter is created internally from this YAML config.
 */
@DisplayName("CORS Security (KAN-15)")
class CorsSecurityTest {

    private static Map<String, Object> globalcorsConfig;
    private static Map<String, Object> corsConfigurations;
    private static Map<String, Object> apiAuthCors;
    private static List<String> allowedOrigins;
    private static List<String> allowedOriginPatterns;

    private static final String APPROVED_ORIGIN_5173 = "http://localhost:5173";
    private static final String APPROVED_ORIGIN_3000 = "http://localhost:3000";
    private static final String UNAPPROVED_ORIGIN = "https://malicious.example.com";

    @BeforeAll
    static void loadYamlConfig() throws Exception {
        Yaml yaml = new Yaml();
        try (InputStream is = CorsSecurityTest.class.getClassLoader().getResourceAsStream("application.yml")) {
            assertThat(is).as("application.yml must exist on test classpath").isNotNull();
            Map<String, Object> root = yaml.load(is);

            Map<String, Object> spring = (Map<String, Object>) root.get("spring");
            assertThat(spring).isNotNull();
            Map<String, Object> cloud = (Map<String, Object>) spring.get("cloud");
            assertThat(cloud).isNotNull();
            Map<String, Object> gateway = (Map<String, Object>) cloud.get("gateway");
            assertThat(gateway).isNotNull();

            globalcorsConfig = (Map<String, Object>) gateway.get("globalcors");
            assertThat(globalcorsConfig).as("globalcors section must exist").isNotNull();

            corsConfigurations = (Map<String, Object>) globalcorsConfig.get("cors-configurations");
            assertThat(corsConfigurations).as("cors-configurations must exist").isNotNull();

            apiAuthCors = (Map<String, Object>) corsConfigurations.get("[/**]");
            assertThat(apiAuthCors).as("CORS config for [/**] must exist").isNotNull();

            allowedOrigins = (List<String>) apiAuthCors.get("allowedOrigins");
            allowedOriginPatterns = (List<String>) apiAuthCors.get("allowedOriginPatterns");
        }
    }

    @Nested
    @DisplayName("TEST 1 — Approved origins are configured")
    class ApprovedOrigins {

        @Test
        @DisplayName("should contain http://localhost:5173 in allowedOriginPatterns")
        void shouldContainLocalhost5173() {
            assertThat(allowedOriginPatterns)
                    .as("CORS must allow localhost:5173")
                    .contains(APPROVED_ORIGIN_5173);
        }

        @Test
        @DisplayName("should contain http://localhost:3000 in allowedOriginPatterns")
        void shouldContainLocalhost3000() {
            assertThat(allowedOriginPatterns)
                    .as("CORS must allow localhost:3000")
                    .contains(APPROVED_ORIGIN_3000);
        }
    }

    @Nested
    @DisplayName("TEST 2 — Wildcard origin is forbidden")
    class NoWildcard {

        @Test
        @DisplayName("should NOT contain wildcard * in allowedOriginPatterns")
        void shouldNotHaveWildcardPattern() {
            assertThat(allowedOriginPatterns)
                    .as("CORS wildcard pattern * is forbidden")
                    .noneMatch(pattern -> pattern.equals("*"));
        }

        @Test
        @DisplayName("should NOT contain wildcard * in allowedOrigins")
        void shouldNotHaveWildcardOrigin() {
            if (allowedOrigins != null) {
                assertThat(allowedOrigins)
                        .as("CORS wildcard origin * is forbidden")
                        .noneMatch(origin -> origin.equals("*"));
            }
        }

        @Test
        @DisplayName("allowedOriginPatterns should not be null or empty")
        void shouldHaveNonEmptyPatterns() {
            assertThat(allowedOriginPatterns)
                    .as("allowedOriginPatterns must be explicitly configured")
                    .isNotNull()
                    .isNotEmpty();
        }
    }

    @Nested
    @DisplayName("TEST 3 — Unapproved origins are rejected")
    class UnapprovedOriginRejected {

        @Test
        @DisplayName("malicious external origin should NOT be in allowedOriginPatterns")
        void shouldNotContainMaliciousOrigin() {
            assertThat(allowedOriginPatterns)
                    .doesNotContain(UNAPPROVED_ORIGIN);
        }

        @Test
        @DisplayName("no external domain should be in allowedOriginPatterns")
        void shouldNotContainAnyExternalDomains() {
            assertThat(allowedOriginPatterns)
                    .allMatch(origin -> origin.startsWith("http://localhost:"));
        }

        @Test
        @DisplayName("exact 2 approved origins only")
        void shouldHaveExactlyTwoOrigins() {
            assertThat(allowedOriginPatterns)
                    .as("Must have exactly 2 approved origins")
                    .hasSize(2);
        }
    }

    @Nested
    @DisplayName("TEST 4 — Substring / scheme / port attack prevention")
    class AttackPrevention {

        @Test
        @DisplayName("should not allow evil.com as substring attack")
        void shouldNotAllowSubstringAttack() {
            assertThat(allowedOriginPatterns)
                    .noneMatch(p -> p.contains("evil"));
        }

        @Test
        @DisplayName("should not contain https variant of approved origin")
        void shouldNotAllowSchemeVariation() {
            assertThat(allowedOriginPatterns)
                    .noneMatch(p -> p.startsWith("https://localhost:"));
        }

        @Test
        @DisplayName("should not contain non-standard ports")
        void shouldNotAllowNonStandardPorts() {
            assertThat(allowedOriginPatterns)
                    .allMatch(p -> p.endsWith(":5173") || p.endsWith(":3000"));
        }
    }

    @Nested
    @DisplayName("TEST 5 — CORS methods configuration")
    class MethodsConfig {

        @Test
        @DisplayName("should allow GET method")
        void shouldAllowGet() {
            List<String> methods = (List<String>) apiAuthCors.get("allowedMethods");
            assertThat(methods).contains("GET");
        }

        @Test
        @DisplayName("should allow POST method")
        void shouldAllowPost() {
            List<String> methods = (List<String>) apiAuthCors.get("allowedMethods");
            assertThat(methods).contains("POST");
        }

        @Test
        @DisplayName("should allow PUT method")
        void shouldAllowPut() {
            List<String> methods = (List<String>) apiAuthCors.get("allowedMethods");
            assertThat(methods).contains("PUT");
        }

        @Test
        @DisplayName("should allow DELETE method")
        void shouldAllowDelete() {
            List<String> methods = (List<String>) apiAuthCors.get("allowedMethods");
            assertThat(methods).contains("DELETE");
        }
    }

    @Nested
    @DisplayName("TEST 6 — Credentials configuration")
    class CredentialsConfig {

        @Test
        @DisplayName("should have allowCredentials set to true")
        void shouldAllowCredentials() {
            Object allowCredentials = apiAuthCors.get("allowCredentials");
            assertThat(allowCredentials).isEqualTo(true);
        }
    }

    @Nested
    @DisplayName("TEST 7 — Allowed headers configuration")
    class HeadersConfig {

        @Test
        @DisplayName("should allow all headers or at least Authorization")
        void shouldAllowHeaders() {
            Object allowedHeaders = apiAuthCors.get("allowedHeaders");
            assertThat(allowedHeaders).isNotNull();
        }

        @Test
        @DisplayName("should expose Authorization header")
        void shouldExposeAuthorization() {
            assertThat(apiAuthCors).containsKey("exposedHeaders");
            Object exposedHeadersObj = apiAuthCors.get("exposedHeaders");
            assertThat(exposedHeadersObj.toString())
                    .as("Authorization must be exposed for client JWT access")
                    .contains("Authorization");
        }
    }

    @Nested
    @DisplayName("TEST 8 — main application.yml content regression")
    class MainYamlRegression {

        @Test
        @DisplayName("application.yml should contain localhost:5173 and localhost:3000")
        void shouldContainApprovedOriginsInFile() throws Exception {
            try (InputStream is = CorsSecurityTest.class.getClassLoader().getResourceAsStream("application.yml")) {
                assertThat(is).isNotNull();
                String content = new String(is.readAllBytes());
                assertThat(content).contains("localhost:5173");
                assertThat(content).contains("localhost:3000");
            }
        }

        @Test
        @DisplayName("application.yml should NOT contain wildcard origin pattern")
        void shouldNotContainWildcardInFile() throws Exception {
            try (InputStream is = CorsSecurityTest.class.getClassLoader().getResourceAsStream("application.yml")) {
                assertThat(is).isNotNull();
                String content = new String(is.readAllBytes());
                assertThat(content)
                        .as("Wildcard * must not appear as an allowedOriginPattern value")
                        .doesNotContainPattern("-\\s+\"\\*\"");
            }
        }
    }
}
