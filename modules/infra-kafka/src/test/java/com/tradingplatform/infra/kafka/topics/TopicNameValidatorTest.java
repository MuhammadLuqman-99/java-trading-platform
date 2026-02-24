package com.tradingplatform.infra.kafka.topics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopicNameValidatorTest {
    @Test
    void shouldValidateAllKnownTopics() {
        for (String topic : TopicNames.all()) {
            assertDoesNotThrow(() -> TopicNameValidator.assertValid(topic));
            assertTrue(TopicNameValidator.isValid(topic));
        }
    }

    @Test
    void shouldRejectInvalidTopicNames() {
        assertFalse(TopicNameValidator.isValid("Orders.Submitted.v1"));
        assertFalse(TopicNameValidator.isValid("orders_submitted_v1"));
        assertFalse(TopicNameValidator.isValid("orders.submitted"));
        assertThrows(IllegalArgumentException.class, () -> TopicNameValidator.assertValid("orders.submitted"));
    }
}
