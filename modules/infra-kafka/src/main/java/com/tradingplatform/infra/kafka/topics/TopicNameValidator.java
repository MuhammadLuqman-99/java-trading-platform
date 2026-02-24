package com.tradingplatform.infra.kafka.topics;

import java.util.regex.Pattern;

public final class TopicNameValidator {
    private static final Pattern TOPIC_PATTERN =
            Pattern.compile("^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+\\.v[1-9][0-9]*$");

    private TopicNameValidator() {
    }

    public static void assertValid(String topicName) {
        if (!isValid(topicName)) {
            throw new IllegalArgumentException("Invalid topic name: " + topicName);
        }
    }

    public static boolean isValid(String topicName) {
        return topicName != null && TOPIC_PATTERN.matcher(topicName).matches();
    }
}
