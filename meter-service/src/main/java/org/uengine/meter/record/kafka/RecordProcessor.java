package org.uengine.meter.record.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.uengine.meter.billing.BillingController;
import org.uengine.meter.billing.BillingService;
import org.uengine.meter.record.Record;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Service
public class RecordProcessor {

    private final Log logger = LogFactory.getLog(getClass());

    @Autowired
    private BillingService billingService;

    @Autowired
    private RecordConfig billingConfig;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BillingController billingController;

    @Autowired
    private InfluxDB influxDBTemplate;

    private RecordStreams recordStreams;

    public RecordProcessor(RecordStreams recordStreams) {
        this.recordStreams = recordStreams;
    }


    public void sendRecordMessage(final RecordMessage recordMessage) {
        try {

            final String message = objectMapper.writeValueAsString(recordMessage);

            String decode = URLDecoder.decode(message, "utf-8");
            logger.info("Sending recordMessage : " + decode);

            MessageChannel messageChannel = recordStreams.producer();
            messageChannel.send(MessageBuilder
                    .withPayload(decode)
                    .setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
                    .build());
        } catch (Exception ex) {

        }
    }

    @StreamListener
    public void receiveRecordMessage(@Input(RecordStreams.INPUT) Flux<String> inbound) {
        inbound
                .log()
                .subscribeOn(Schedulers.parallel())
                .subscribe(value -> {
                    try {
                        logger.info("receive recordMessage : " + value);
                        final RecordMessage recordMessage = objectMapper.readValue(value, RecordMessage.class);

                        //if log type
                        if (RecordMessage.RecordMessageType.LOG.equals(recordMessage.getType())) {
                            final String[] split = recordMessage.getMessage().split("\n");
                            Arrays.stream(split)
                                    .map(line -> new Record(line))
                                    .filter(record -> !record.empty())
                                    .map(record -> {
                                        Point point = Point.measurement("record")
                                                .time(record.getTime(), TimeUnit.MILLISECONDS)
                                                .addField("amount", record.getAmount())
                                                .addField("user", record.getUser())
                                                .addField("unit", record.getUnit())
                                                .build();

                                        influxDBTemplate.write(point);
                                        return point;
                                    });
                        }

                    } catch (Exception ex) {
                        logger.error("update UserSubscriptions failed");
                    } finally {
                        try {
                            Map map = new ObjectMapper().readValue(value, Map.class);
                            billingController.emitterSend(map);
                        } catch (Exception ex) {
                            //Nothing, just notification
                        }
                    }
                }, error -> System.err.println("CAUGHT " + error));
    }
}

