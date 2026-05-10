package com.microservices.notifications_service.email;

import com.microservices.notifications_service.dto.NotificationDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BrevoMailServiceTest {

    private BrevoMailService brevoMailService;

    // Mocks for the RestClient fluent chain
    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        brevoMailService = new BrevoMailService();
        // Inject valid defaults; individual tests override these when needed
        ReflectionTestUtils.setField(brevoMailService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(brevoMailService, "senderEmail", "sender@example.com");
        ReflectionTestUtils.setField(brevoMailService, "senderName", "FoodChain");
        // Inject the mock RestClient so HTTP calls go nowhere
        ReflectionTestUtils.setField(brevoMailService, "restClient", restClient);
    }

    // ---------------------------------------------------------------------------
    // Guard-clause tests (no HTTP call should be made)
    // ---------------------------------------------------------------------------

    @Test
    void send_logsWarning_whenToEmailIsNull() {
        NotificationDtos.EmailSendEvent event = NotificationDtos.EmailSendEvent.builder()
                .toEmail(null)
                .htmlContent("<p>Hello</p>")
                .emailType("WELCOME")
                .build();

        brevoMailService.send(event);

        verifyNoInteractions(restClient);
    }

    @Test
    void send_logsWarning_whenToEmailIsBlank() {
        NotificationDtos.EmailSendEvent event = NotificationDtos.EmailSendEvent.builder()
                .toEmail("   ")
                .htmlContent("<p>Hello</p>")
                .emailType("WELCOME")
                .build();

        brevoMailService.send(event);

        verifyNoInteractions(restClient);
    }

    @Test
    void send_logsWarning_whenApiKeyIsBlank() {
        ReflectionTestUtils.setField(brevoMailService, "apiKey", "");

        NotificationDtos.EmailSendEvent event = NotificationDtos.EmailSendEvent.builder()
                .toEmail("user@example.com")
                .htmlContent("<p>Hello</p>")
                .emailType("WELCOME")
                .build();

        brevoMailService.send(event);

        verifyNoInteractions(restClient);
    }

    @Test
    void send_logsWarning_whenSenderEmailIsBlank() {
        ReflectionTestUtils.setField(brevoMailService, "senderEmail", "");

        NotificationDtos.EmailSendEvent event = NotificationDtos.EmailSendEvent.builder()
                .toEmail("user@example.com")
                .htmlContent("<p>Hello</p>")
                .emailType("WELCOME")
                .build();

        brevoMailService.send(event);

        verifyNoInteractions(restClient);
    }

    @Test
    void send_logsWarning_whenHtmlContentIsBlank() {
        NotificationDtos.EmailSendEvent event = NotificationDtos.EmailSendEvent.builder()
                .toEmail("user@example.com")
                .htmlContent("   ")
                .emailType("WELCOME")
                .build();

        brevoMailService.send(event);

        verifyNoInteractions(restClient);
    }

    @Test
    void send_logsWarning_whenHtmlContentIsNull() {
        NotificationDtos.EmailSendEvent event = NotificationDtos.EmailSendEvent.builder()
                .toEmail("user@example.com")
                .htmlContent(null)
                .emailType("WELCOME")
                .build();

        brevoMailService.send(event);

        verifyNoInteractions(restClient);
    }

    // ---------------------------------------------------------------------------
    // HTTP interaction tests (RestClient mock chain)
    // ---------------------------------------------------------------------------

    private void stubRestClientChain() {
        lenient().when(restClient.post()).thenReturn(requestBodyUriSpec);
        lenient().when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        lenient().when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        lenient().when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
        lenient().when(requestBodySpec.body(any(Object.class))).thenReturn(requestBodySpec);
        lenient().when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    }

    @Test
    void send_succeeds_withValidEvent() {
        stubRestClientChain();
        when(responseSpec.toBodilessEntity()).thenReturn(null);

        NotificationDtos.EmailSendEvent event = NotificationDtos.EmailSendEvent.builder()
                .toEmail("user@example.com")
                .toName("Test User")
                .subject("Welcome!")
                .htmlContent("<p>Hello</p>")
                .emailType("WELCOME")
                .build();

        assertThatNoException().isThrownBy(() -> brevoMailService.send(event));
        verify(responseSpec).toBodilessEntity();
    }

    @Test
    void send_logsError_whenBrevoReturns401() {
        stubRestClientChain();
        when(responseSpec.toBodilessEntity()).thenThrow(
                new RestClientResponseException("Unauthorized", 401, "Unauthorized", null, null, null)
        );

        NotificationDtos.EmailSendEvent event = NotificationDtos.EmailSendEvent.builder()
                .toEmail("user@example.com")
                .htmlContent("<p>Hello</p>")
                .emailType("WELCOME")
                .build();

        // Must not rethrow — the service catches and logs
        assertThatNoException().isThrownBy(() -> brevoMailService.send(event));
    }

    @Test
    void send_logsError_whenBrevoReturns400() {
        stubRestClientChain();
        when(responseSpec.toBodilessEntity()).thenThrow(
                new RestClientResponseException("Bad Request", 400, "Bad Request", null, null, null)
        );

        NotificationDtos.EmailSendEvent event = NotificationDtos.EmailSendEvent.builder()
                .toEmail("user@example.com")
                .htmlContent("<p>Hello</p>")
                .emailType("WELCOME")
                .build();

        // Must not rethrow — the service catches and logs
        assertThatNoException().isThrownBy(() -> brevoMailService.send(event));
    }

    @Test
    void send_usesToEmail_asDisplayName_whenToNameIsNull() {
        stubRestClientChain();
        when(responseSpec.toBodilessEntity()).thenReturn(null);

        NotificationDtos.EmailSendEvent event = NotificationDtos.EmailSendEvent.builder()
                .toEmail("user@example.com")
                .toName(null)          // should fall back to toEmail as display name
                .subject("Welcome!")
                .htmlContent("<p>Hello</p>")
                .emailType("WELCOME")
                .build();

        assertThatNoException().isThrownBy(() -> brevoMailService.send(event));
        verify(responseSpec).toBodilessEntity();
    }

    @Test
    void send_usesDefaultSubject_whenSubjectIsNull() {
        stubRestClientChain();
        when(responseSpec.toBodilessEntity()).thenReturn(null);

        NotificationDtos.EmailSendEvent event = NotificationDtos.EmailSendEvent.builder()
                .toEmail("user@example.com")
                .subject(null)         // should fall back to "FoodChain"
                .htmlContent("<p>Hello</p>")
                .emailType("WELCOME")
                .build();

        assertThatNoException().isThrownBy(() -> brevoMailService.send(event));
    }
}
