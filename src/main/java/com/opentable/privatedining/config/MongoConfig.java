package com.opentable.privatedining.config;

import org.bson.UuidRepresentation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.util.Arrays;
import java.util.UUID;

/**
 * MongoDB configuration for UUID conversion.
 * Handles conversion between MongoDB Binary UUID and Java UUID types.
 */
@Configuration
public class MongoConfig {

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(Arrays.asList(
                new BinaryToUuidConverter(),
                new UuidToBinaryConverter()
        ));
    }

    /**
     * Converter to read Binary UUID from MongoDB into Java UUID.
     */
    @ReadingConverter
    public static class BinaryToUuidConverter implements Converter<org.bson.types.Binary, UUID> {
        @Override
        public UUID convert(org.bson.types.Binary source) {
            if (source == null) {
                return null;
            }
            // MongoDB stores UUID as Binary with subtype 4
            byte[] bytes = source.getData();
            if (bytes.length != 16) {
                throw new IllegalArgumentException("Invalid UUID binary length: " + bytes.length);
            }

            long mostSigBits = 0;
            long leastSigBits = 0;
            for (int i = 0; i < 8; i++) {
                mostSigBits = (mostSigBits << 8) | (bytes[i] & 0xff);
            }
            for (int i = 8; i < 16; i++) {
                leastSigBits = (leastSigBits << 8) | (bytes[i] & 0xff);
            }
            return new UUID(mostSigBits, leastSigBits);
        }
    }

    /**
     * Converter to write Java UUID to MongoDB Binary UUID.
     */
    @WritingConverter
    public static class UuidToBinaryConverter implements Converter<UUID, org.bson.types.Binary> {
        @Override
        public org.bson.types.Binary convert(UUID source) {
            if (source == null) {
                return null;
            }
            byte[] bytes = new byte[16];
            long mostSigBits = source.getMostSignificantBits();
            long leastSigBits = source.getLeastSignificantBits();

            for (int i = 7; i >= 0; i--) {
                bytes[i] = (byte) (mostSigBits & 0xff);
                mostSigBits >>= 8;
            }
            for (int i = 15; i >= 8; i--) {
                bytes[i] = (byte) (leastSigBits & 0xff);
                leastSigBits >>= 8;
            }
            // Subtype 4 is the standard UUID representation
            return new org.bson.types.Binary((byte) 4, bytes);
        }
    }
}
