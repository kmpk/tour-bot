package ar.vicria.subte.microservice.kafka.consumer;

import ar.vicria.subte.dto.DistanceDto;
import ar.vicria.subte.dto.RouteDto;
import ar.vicria.subte.microservice.kafka.producer.TelegramTopicKafkaProducer;
import ar.vicria.subte.microservice.services.DistanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Потребитель kafka топика cluster_layer_media_event_topic
 * Событие “К слою кластера добавлен медиа материал”.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubteRoadTopicKafkaConsumer {

    private final DistanceService service;
    private final TelegramTopicKafkaProducer producer;

    @KafkaListener(topics = {"subte_road_topic"})
    public void consume(DistanceDto distanceDto) {
        log.debug("=> consuming {}", distanceDto);

        //todo validate(distanceDto);

        RouteDto count = service.count(distanceDto);
        count.setChatId(distanceDto.getChatId());
        count.setMsgId(distanceDto.getMsgId());
        log.debug("=> consumed {}", distanceDto);
        producer.sendAnswerWithRoadCounting(count);
    }
}
