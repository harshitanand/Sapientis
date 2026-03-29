package com.moviebooking.event;

import com.moviebooking.model.Booking;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Publishes booking domain events to Kafka topics.
 *
 * Topic naming convention: booking.{event-type}
 * Partition key = bookingId — ensures all events for one booking are ordered.
 *
 * Downstream consumers: notification service, analytics pipeline, refund service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventPublisher {

    private static final String TOPIC_INITIATED  = "booking.initiated";
    private static final String TOPIC_CONFIRMED  = "booking.confirmed";
    private static final String TOPIC_CANCELLED  = "booking.cancelled";

    private final KafkaTemplate<String, BookingEvent> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    public void publishBookingInitiated(Booking booking) {
        publish(TOPIC_INITIATED, build(booking, BookingEvent.Type.INITIATED));
    }

    public void publishBookingConfirmed(Booking booking) {
        publish(TOPIC_CONFIRMED, build(booking, BookingEvent.Type.CONFIRMED));
    }

    public void publishBookingCancelled(Booking booking) {
        publish(TOPIC_CANCELLED, build(booking, BookingEvent.Type.CANCELLED));
    }

    private void publish(String topic, BookingEvent event) {
        kafkaTemplate.send(topic, event.getBookingId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} event for bookingRef={}",
                                event.getType(), event.getBookingRef(), ex);
                        meterRegistry.counter("booking.event.publish.failure",
                                "topic", topic, "eventType", event.getType().name()).increment();
                    } else {
                        log.debug("Published {} to topic={} partition={} offset={}",
                                event.getType(), topic,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    private BookingEvent build(Booking booking, BookingEvent.Type type) {
        return BookingEvent.builder()
                .type(type)
                .bookingId(booking.getId())
                .bookingRef(booking.getBookingRef())
                .customerId(booking.getCustomerId())
                .showId(booking.getShow().getId())
                .status(booking.getStatus())
                .finalAmount(booking.getFinalAmount())
                .occurredAt(Instant.now())
                .build();
    }
}
