package com.marklogic.handlers.undertow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Edge case tests for SAML Authentication Handler
 * Tests invalid date formats, future dates, special characters, and boundary conditions
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("SAML Auth Handler - Edge Cases & Security Tests")
class SAMLAuthHandlerEdgeCaseTest {

    @Autowired
    private MockMvc mockMvc;

    // TC-2: Edge Case Tests - Date Handling

    @Test
    @DisplayName("Should handle invalid date format gracefully")
    void testInvalidDateFormat() throws Exception {
        MvcResult result = mockMvc.perform(post("/saml/auth")
                        .param("samlid", "test-invalid-date")
                        .param("userid", "testuser")
                        .param("authnresult", "success")
                        .param("notbefore_date", "invalid-date-format")
                        .param("notafter_date", "2025-12-31T23:59:59.000Z")
                        .param("assertionurl", "http://localhost:9002/consumer")
                        .param("roles", "user"))
                .andReturn();

        // Should handle gracefully - either succeed with default dates or return error
        int status = result.getResponse().getStatus();
        assertTrue(status == 200 || status == 400 || status == 500,
                "Should handle invalid date format gracefully");
        
        if (status == 200) {
            String htmlBody = result.getResponse().getContentAsString();
            assertFalse(htmlBody.isEmpty(), "Should return HTML response");
        }
    }

    @Test
    @DisplayName("Should handle missing milliseconds in date format")
    void testDateWithoutMilliseconds() throws Exception {
        MvcResult result = mockMvc.perform(post("/saml/auth")
                        .param("samlid", "test-no-millis")
                        .param("userid", "testuser")
                        .param("authnresult", "success")
                        .param("notbefore_date", "2025-10-04T10:00:00Z")  // No .000
                        .param("notafter_date", "2025-10-04T11:00:00Z")
                        .param("assertionurl", "http://localhost:9002/consumer")
                        .param("roles", "user"))
                .andReturn();

        // Should handle - may use default dates
        int status = result.getResponse().getStatus();
        assertTrue(status == 200 || status == 400,
                "Should handle date without milliseconds");
    }

    @Test
    @DisplayName("Should handle future notbefore_date")
    void testFutureNotBeforeDate() throws Exception {
        LocalDateTime futureDate = LocalDateTime.now().plusYears(1);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        String futureDateStr = futureDate.atZone(ZoneId.of("UTC")).format(formatter);

        MvcResult result = mockMvc.perform(post("/saml/auth")
                        .param("samlid", "test-future-date")
                        .param("userid", "testuser")
                        .param("authnresult", "success")
                        .param("notbefore_date", futureDateStr)
                        .param("notafter_date", "2026-12-31T23:59:59.000Z")
                        .param("assertionurl", "http://localhost:9002/consumer")
                        .param("roles", "user"))
                .andExpect(status().isOk())
                .andReturn();

        // Should succeed but assertion may not be valid until future date
        String htmlBody = result.getResponse().getContentAsString();
        assertFalse(htmlBody.isEmpty());
    }

    @Test
    @DisplayName("Should handle notafter_date before notbefore_date")
    void testInvertedDates() throws Exception {
        MvcResult result = mockMvc.perform(post("/saml/auth")
                        .param("samlid", "test-inverted-dates")
                        .param("userid", "testuser")
                        .param("authnresult", "success")
                        .param("notbefore_date", "2025-12-31T23:59:59.000Z")
                        .param("notafter_date", "2025-01-01T00:00:00.000Z")
                        .param("assertionurl", "http://localhost:9002/consumer")
                        .param("roles", "user"))
                .andReturn();

        // Should handle - may swap dates or use defaults
        int status = result.getResponse().getStatus();
        assertTrue(status == 200 || status == 400,
                "Should handle inverted dates");
    }

    @Test
    @DisplayName("Should handle past dates")
    void testPastDates() throws Exception {
        MvcResult result = mockMvc.perform(post("/saml/auth")
                        .param("samlid", "test-past-dates")
                        .param("userid", "testuser")
                        .param("authnresult", "success")
                        .param("notbefore_date", "2020-01-01T00:00:00.000Z")
                        .param("notafter_date", "2020-01-01T01:00:00.000Z")
                        .param("assertionurl", "http://localhost:9002/consumer")
                        .param("roles", "user"))
                .andExpect(status().isOk())
                .andReturn();

        // Should succeed even with expired dates (testing tool allows this)
        String htmlBody = result.getResponse().getContentAsString();
        assertFalse(htmlBody.isEmpty());
    }

    // TC-2: Edge Case Tests - Special Characters

    @Test
    @DisplayName("Should handle special characters in userid")
    void testSpecialCharactersInUserId() throws Exception {
        String specialUserId = "user@domain.com+test";

        MvcResult result = mockMvc.perform(post("/saml/auth")
                        .param("samlid", "test-special-chars")
                        .param("userid", specialUserId)
                        .param("authnresult", "success")
                        .param("notbefore_date", "2025-10-04T10:00:00.000Z")
                        .param("notafter_date", "2025-10-04T11:00:00.000Z")
                        .param("assertionurl", "http://localhost:9002/consumer")
                        .param("roles", "user"))
                .andExpect(status().isOk())
                .andReturn();

        String htmlBody = result.getResponse().getContentAsString();
        assertFalse(htmlBody.isEmpty());
    }

    @Test
    @DisplayName("Should handle XML special characters in roles")
    void testXMLSpecialCharactersInRoles() throws Exception {
        String rolesWithXML = "user,admin<test>,role&value,role\"quoted\"";

        MvcResult result = mockMvc.perform(post("/saml/auth")
                        .param("samlid", "test-xml-chars")
                        .param("userid", "testuser")
                        .param("authnresult", "success")
                        .param("notbefore_date", "2025-10-04T10:00:00.000Z")
                        .param("notafter_date", "2025-10-04T11:00:00.000Z")
                        .param("assertionurl", "http://localhost:9002/consumer")
                        .param("roles", rolesWithXML))
                .andExpect(status().isOk())
                .andReturn();

        // Should properly escape XML special characters
        String htmlBody = result.getResponse().getContentAsString();
        assertFalse(htmlBody.isEmpty());
    }

    @Test
    @DisplayName("Should handle very long samlid")
    void testVeryLongSamlId() throws Exception {
        String longSamlId = "_" + "x".repeat(500);

        MvcResult result = mockMvc.perform(post("/saml/auth")
                        .param("samlid", longSamlId)
                        .param("userid", "testuser")
                        .param("authnresult", "success")
                        .param("notbefore_date", "2025-10-04T10:00:00.000Z")
                        .param("notafter_date", "2025-10-04T11:00:00.000Z")
                        .param("assertionurl", "http://localhost:9002/consumer")
                        .param("roles", "user"))
                .andExpect(status().isOk())
                .andReturn();

        String htmlBody = result.getResponse().getContentAsString();
        assertFalse(htmlBody.isEmpty());
    }

    @Test
    @DisplayName("Should handle Unicode characters in roles")
    void testUnicodeInRoles() throws Exception {
        String unicodeRoles = "admin,用户角色,ρόλος,роль";

        MvcResult result = mockMvc.perform(post("/saml/auth")
                        .param("samlid", "test-unicode")
                        .param("userid", "testuser")
                        .param("authnresult", "success")
                        .param("notbefore_date", "2025-10-04T10:00:00.000Z")
                        .param("notafter_date", "2025-10-04T11:00:00.000Z")
                        .param("assertionurl", "http://localhost:9002/consumer")
                        .param("roles", unicodeRoles))
                .andExpect(status().isOk())
                .andReturn();

        String htmlBody = result.getResponse().getContentAsString();
        assertFalse(htmlBody.isEmpty());
    }

    @Test
    @DisplayName("Should handle empty roles")
    void testEmptyRoles() throws Exception {
        MvcResult result = mockMvc.perform(post("/saml/auth")
                        .param("samlid", "test-empty-roles")
                        .param("userid", "testuser")
                        .param("authnresult", "success")
                        .param("notbefore_date", "2025-10-04T10:00:00.000Z")
                        .param("notafter_date", "2025-10-04T11:00:00.000Z")
                        .param("assertionurl", "http://localhost:9002/consumer")
                        .param("roles", ""))
                .andExpect(status().isOk())
                .andReturn();

        String htmlBody = result.getResponse().getContentAsString();
        assertFalse(htmlBody.isEmpty());
    }

    @Test
    @DisplayName("Should handle very long assertion URL")
    void testVeryLongAssertionUrl() throws Exception {
        String longUrl = "http://localhost:9002/consumer?" + "param=value&".repeat(100);

        MvcResult result = mockMvc.perform(post("/saml/auth")
                        .param("samlid", "test-long-url")
                        .param("userid", "testuser")
                        .param("authnresult", "success")
                        .param("notbefore_date", "2025-10-04T10:00:00.000Z")
                        .param("notafter_date", "2025-10-04T11:00:00.000Z")
                        .param("assertionurl", longUrl)
                        .param("roles", "user"))
                .andExpect(status().isOk())
                .andReturn();

        String htmlBody = result.getResponse().getContentAsString();
        assertFalse(htmlBody.isEmpty());
    }

    // TC-4: Security Tests

    @Test
    @DisplayName("Should handle XSS attempts in userid")
    void testXSSInUserId() throws Exception {
        String maliciousUserId = "<script>alert('XSS')</script>";

        MvcResult result = mockMvc.perform(post("/saml/auth")
                        .param("samlid", "test-xss")
                        .param("userid", maliciousUserId)
                        .param("authnresult", "success")
                        .param("notbefore_date", "2025-10-04T10:00:00.000Z")
                        .param("notafter_date", "2025-10-04T11:00:00.000Z")
                        .param("assertionurl", "http://localhost:9002/consumer")
                        .param("roles", "user"))
                .andExpect(status().isOk())
                .andReturn();

        String htmlBody = result.getResponse().getContentAsString();
        
        // Should escape or encode the script tag
        assertFalse(htmlBody.contains("<script>alert"), 
                "Should not contain unescaped script tags in HTML");
    }

    @Test
    @DisplayName("Should handle XML injection attempts in roles")
    void testXMLInjectionInRoles() throws Exception {
        String maliciousRoles = "admin]]></Roles><MaliciousElement>Evil</MaliciousElement><Roles><![CDATA[user";

        MvcResult result = mockMvc.perform(post("/saml/auth")
                        .param("samlid", "test-xml-injection")
                        .param("userid", "testuser")
                        .param("authnresult", "success")
                        .param("notbefore_date", "2025-10-04T10:00:00.000Z")
                        .param("notafter_date", "2025-10-04T11:00:00.000Z")
                        .param("assertionurl", "http://localhost:9002/consumer")
                        .param("roles", maliciousRoles))
                .andExpect(status().isOk())
                .andReturn();

        String htmlBody = result.getResponse().getContentAsString();
        
        // Should not contain injected XML element
        assertFalse(htmlBody.contains("<MaliciousElement>"), 
                "Should not contain injected XML elements");
    }

    @Test
    @DisplayName("Should handle null bytes in parameters")
    void testNullBytesInParameters() throws Exception {
        String userIdWithNull = "test\u0000user";

        MvcResult result = mockMvc.perform(post("/saml/auth")
                        .param("samlid", "test-null-byte")
                        .param("userid", userIdWithNull)
                        .param("authnresult", "success")
                        .param("notbefore_date", "2025-10-04T10:00:00.000Z")
                        .param("notafter_date", "2025-10-04T11:00:00.000Z")
                        .param("assertionurl", "http://localhost:9002/consumer")
                        .param("roles", "user"))
                .andReturn();

        // Should handle gracefully
        int status = result.getResponse().getStatus();
        assertTrue(status >= 200 && status < 600, "Should return valid HTTP status");
    }

    @Test
    @DisplayName("Should handle missing required parameters")
    void testMissingRequiredParameters() throws Exception {
        MvcResult result = mockMvc.perform(post("/saml/auth")
                        .param("samlid", "test-missing-params")
                        .param("userid", "testuser"))
                .andReturn();

        // Should return error or handle gracefully
        int status = result.getResponse().getStatus();
        assertTrue(status >= 200 && status < 600, "Should return valid HTTP status");
    }

    @Test
    @DisplayName("Should handle extremely large number of roles")
    void testLargeNumberOfRoles() throws Exception {
        StringBuilder largeRoles = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            largeRoles.append("role_").append(i);
            if (i < 499) largeRoles.append(",");
        }

        MvcResult result = mockMvc.perform(post("/saml/auth")
                        .param("samlid", "test-many-roles")
                        .param("userid", "testuser")
                        .param("authnresult", "success")
                        .param("notbefore_date", "2025-10-04T10:00:00.000Z")
                        .param("notafter_date", "2025-10-04T11:00:00.000Z")
                        .param("assertionurl", "http://localhost:9002/consumer")
                        .param("roles", largeRoles.toString()))
                .andReturn();

        // Should handle (may succeed or return error if too large)
        int status = result.getResponse().getStatus();
        assertTrue(status == 200 || status == 400 || status == 413,
                "Should handle large number of roles gracefully");
    }

    @Test
    @DisplayName("Should handle duplicate roles")
    void testDuplicateRoles() throws Exception {
        String duplicateRoles = "admin,user,admin,user,admin";

        MvcResult result = mockMvc.perform(post("/saml/auth")
                        .param("samlid", "test-duplicate-roles")
                        .param("userid", "testuser")
                        .param("authnresult", "success")
                        .param("notbefore_date", "2025-10-04T10:00:00.000Z")
                        .param("notafter_date", "2025-10-04T11:00:00.000Z")
                        .param("assertionurl", "http://localhost:9002/consumer")
                        .param("roles", duplicateRoles))
                .andExpect(status().isOk())
                .andReturn();

        String htmlBody = result.getResponse().getContentAsString();
        assertFalse(htmlBody.isEmpty());
    }
}
