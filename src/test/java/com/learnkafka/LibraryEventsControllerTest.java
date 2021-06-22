package com.learnkafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnkafka.domain.Book;
import com.learnkafka.domain.LibraryEvent;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(topics = {"library-events"},partitions = 3)
@TestPropertySource(properties = {"spring.kafka.producer.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.admin.properties.bootstrap.servers=${spring.embedded.kafka.brokers}"})
public class LibraryEventsControllerTest {

    @Autowired
    TestRestTemplate testRestTemplate;

    @Autowired
    EmbeddedKafkaBroker embeddedKafkaBroker;

    private Consumer<Integer,String> consumer;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp(){
        Map<String,Object> configs = new HashMap<>(KafkaTestUtils.consumerProps("group1","true",embeddedKafkaBroker));
        consumer = new DefaultKafkaConsumerFactory<>(configs,new IntegerDeserializer(),new StringDeserializer()).createConsumer();
        embeddedKafkaBroker.consumeFromAllEmbeddedTopics(consumer);
    }

    @AfterEach
    void tearDown(){
        consumer.close();
    }

    @Test
    @Timeout(5)
    void postLibraryEvent() throws InterruptedException, JsonProcessingException {
        //Given
        Book book = Book.builder().bookId(123)
                .bookAuthor("Dilip")
                .bookName("Kafka using spring boot")
                .build();
        LibraryEvent libraryEvent = LibraryEvent.builder().book(book).build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<LibraryEvent> request = new HttpEntity<>(libraryEvent, headers);
        //When
        ResponseEntity<LibraryEvent> responseEntity = testRestTemplate.exchange("/v1/libraryEvent", HttpMethod.POST,
                request, LibraryEvent.class);
        //Then
        Assertions.assertEquals(HttpStatus.CREATED, responseEntity.getStatusCode());
        ConsumerRecord<Integer,String> consumerRecord = KafkaTestUtils.getSingleRecord(consumer,"library-events");
        //Thread.sleep(3000);
        String expectedRecord = "Kafka using spring boot";
        String value = consumerRecord.value();
        LibraryEvent libraryEventResult = objectMapper.readValue(value,LibraryEvent.class);
        Assertions.assertEquals(expectedRecord,libraryEventResult.getBook().getBookName());
    }
}
