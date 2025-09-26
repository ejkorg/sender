package com.example.reloader.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

@SpringBootTest(properties = "reloader.jwt.secret=0123456789abcdef0123456789abcdef")
public class SenderServiceDuplicateHandlingTest {

    @Autowired
    private SenderService senderService;

    @Test
    public void testDuplicateEnqueueIsSkipped() {
        List<String> payloads = List.of("P1", "P2", "P3");

        SenderService.EnqueueResultHolder first = senderService.enqueuePayloadsWithResult(99, payloads, "test_source");
        assertThat(first.enqueuedCount).isEqualTo(3);
        assertThat(first.skippedPayloads).isEmpty();

        SenderService.EnqueueResultHolder second = senderService.enqueuePayloadsWithResult(99, payloads, "test_source");
        // second call should find none to insert and should list skipped payloads
        assertThat(second.enqueuedCount).isEqualTo(0);
        assertThat(second.skippedPayloads).containsExactlyInAnyOrderElementsOf(payloads);
    }
}
