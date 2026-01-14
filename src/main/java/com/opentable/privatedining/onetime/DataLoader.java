package com.opentable.privatedining.onetime;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.opentable.privatedining.model.Reservation;
import com.opentable.privatedining.model.Restaurant;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class DataLoader implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;

    public DataLoader(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        var yaml = new ClassPathResource("init-db.yml");

        if(mongoTemplate.collectionExists(Restaurant.class)
                || mongoTemplate.collectionExists(Reservation.class)) {
            return;
        }

        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
        JavaTimeModule module = new JavaTimeModule();
        module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(df));

        YAMLMapper yamlMapper = new YAMLMapper();
        yamlMapper.registerModule(module);
        try (var inputStream = yaml.getInputStream()) {
            var data = yamlMapper.readValue(inputStream, Data.class);

            mongoTemplate.dropCollection(Restaurant.class);
            mongoTemplate.dropCollection(Reservation.class);

            mongoTemplate.insertAll(data.getRestaurants());
            mongoTemplate.insertAll(data.getReservations());
        }
    }
}
