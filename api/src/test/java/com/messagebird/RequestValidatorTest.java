package com.messagebird;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.Charset;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Resources;

import com.auth0.jwt.interfaces.Clock;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messagebird.exceptions.RequestValidationException;

import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RequestValidatorTest {

    /**
     * WebhookSignatureTestCase
     */
    public static class WebhookSignatureTestCase {
        public String name;
        public String method;
        public String secret;
        public String url;
        public String payload;
        public String timestamp;
        public String token;
        public Boolean valid;
        public String reason;
    }

    /**
     * Error Map that maps test data expected outcome to actual error message.
     */
    private static final Map<String, String> ERROR_MAP = new HashMap<String, String>() {
        {
            put("invalid jwt: claim nbf is in the future", "The Token can't be used before");
            put("invalid jwt: claim exp is in the past", "The Token has expired on");
            put("invalid jwt: claim url_hash is invalid", "The Claim 'url_hash' value doesn't match the required one.");
            put("invalid jwt: claim payload_hash is invalid",
                    "The Claim 'payload_hash' value doesn't match the required one.");
            put("invalid jwt: signature is invalid", "Signature is invalid.");
            put("invalid jwt: claim payload_hash is set but actual payload is missing",
                    "The Claim 'payload_hash' is set but actual payload is missing.");
            put("invalid jwt: claim payload_hash is not set but payload is present",
                    "The Claim 'payload_hash' is not set but payload is present.");
            put("invalid jwt: signing method none is invalid", "The signing method is invalid.");

        }
    };

    private final WebhookSignatureTestCase testCase;

    public RequestValidatorTest(String testName, WebhookSignatureTestCase testCase) {
        this.testCase = testCase;
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() throws JsonParseException, JsonMappingException, IOException {
        List<WebhookSignatureTestCase> testCases = new ObjectMapper().readValue(
                Resources.class.getResourceAsStream("/webhook_test_data.json"),
                new TypeReference<List<WebhookSignatureTestCase>>() {
                });

        return testCases.stream()
                .map(tc -> new Object[]{ tc.name, tc })
                .collect(Collectors.toList());
    }

    @Test
    public void testWebhookSignature() throws Throwable {
        RequestValidator validator = new RequestValidator(testCase.secret);

        Clock clock = mock(Clock.class);
        Date clockDate = spy(Date.from(OffsetDateTime.parse(testCase.timestamp).toInstant()));
        when(clock.getToday()).thenReturn(clockDate);

        ThrowingRunnable runnable = () -> validator.validateSignature(clock, testCase.token, testCase.url,
                (testCase.payload == null) ? null : testCase.payload.getBytes(Charset.forName("UTF-8")));

        if (testCase.valid) {
            runnable.run();
            return;
        }

        assertTrue(String.format("Expected error message mapping for '%s' but it was not found.", testCase.reason),
                ERROR_MAP.containsKey(testCase.reason));

        String expectedError = ERROR_MAP.get(testCase.reason);

        RequestValidationException err = assertThrows(RequestValidationException.class, runnable);
        assertTrue(String.format("Expected error message containing: %s (originally %s) but was: %s", expectedError,
                testCase.reason, err.getMessage()), err.getMessage().contains(expectedError));
    }
}
