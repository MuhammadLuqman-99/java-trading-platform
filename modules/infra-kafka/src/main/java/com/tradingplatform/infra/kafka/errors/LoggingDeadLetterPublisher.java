package com.tradingplatform.infra.kafka.errors;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingDeadLetterPublisher implements DeadLetterPublisher {
    private static final Logger log = LoggerFactory.getLogger(LoggingDeadLetterPublisher.class);

    @Override
    public void publish(String sourceTopic, ConsumerRecord<String, String> failedRecord, Exception exception) {
        log.warn(
                "Dead-lettering event from topic={} partition={} offset={} key={} error={}",
                sourceTopic,
                failedRecord.partition(),
                failedRecord.offset(),
                failedRecord.key(),
                exception.getMessage()
        );
    }
}
