package com.opentable.privatedining.config;

import de.flapdoodle.embed.mongo.spring.autoconfigure.EmbeddedMongoAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
@ImportAutoConfiguration(EmbeddedMongoAutoConfiguration.class)
public class EmbeddedMongoConfig {
}