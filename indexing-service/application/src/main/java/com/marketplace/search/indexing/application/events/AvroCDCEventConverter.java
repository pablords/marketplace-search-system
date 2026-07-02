package com.marketplace.search.indexing.application.events;

import org.apache.avro.generic.GenericRecord;
import org.apache.avro.Schema;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class AvroCDCEventConverter {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    public static DebeziumCDCEvent convert(GenericRecord record) {
        if (record == null) return null;
        try {
            Map<String, Object> map = genericRecordToMap(record);
            return objectMapper.convertValue(map, DebeziumCDCEvent.class);
        } catch (Exception e) {
            throw new RuntimeException("Error converting Avro GenericRecord to DebeziumCDCEvent: " + e.getMessage(), e);
        }
    }

    private static Map<String, Object> genericRecordToMap(GenericRecord record) {
        if (record == null) return null;
        Map<String, Object> map = new HashMap<>();
        for (Schema.Field field : record.getSchema().getFields()) {
            Object value = record.get(field.name());
            map.put(field.name(), convertAvroValue(value));
        }
        return map;
    }

    private static Object convertAvroValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof GenericRecord) {
            return genericRecordToMap((GenericRecord) value);
        }
        if (value instanceof org.apache.avro.util.Utf8) {
            return value.toString();
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            List<Object> newList = new ArrayList<>();
            for (Object item : list) {
                newList.add(convertAvroValue(item));
            }
            return newList;
        }
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            Map<String, Object> newMap = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                newMap.put(entry.getKey().toString(), convertAvroValue(entry.getValue()));
            }
            return newMap;
        }
        return value;
    }
}
