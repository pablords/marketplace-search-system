package com.marketplace.search.infrastructure.kafka.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO que representa a estrutura de um evento do Debezium CDC.
 * O Debezium envia eventos no formato:
 * {
 *   "before": {...},  // estado anterior (null em INSERT)
 *   "after": {...},   // estado atual (null em DELETE)
 *   "op": "c|u|d|r",  // operação: create, update, delete, read
 *   "ts_ms": 123456   // timestamp
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DebeziumEventDTO {
    
    @JsonProperty("before")
    private ProductPayloadDTO before;
    
    @JsonProperty("after")
    private ProductPayloadDTO after;
    
    @JsonProperty("op")
    private String operation;
    
    @JsonProperty("ts_ms")
    private Long timestamp;
    
    @JsonProperty("source")
    private SourceDTO source;
    
    // Getters e Setters
    public ProductPayloadDTO getBefore() {
        return before;
    }
    
    public void setBefore(ProductPayloadDTO before) {
        this.before = before;
    }
    
    public ProductPayloadDTO getAfter() {
        return after;
    }
    
    public void setAfter(ProductPayloadDTO after) {
        this.after = after;
    }
    
    public String getOperation() {
        return operation;
    }
    
    public void setOperation(String operation) {
        this.operation = operation;
    }
    
    public Long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
    
    public SourceDTO getSource() {
        return source;
    }
    
    public void setSource(SourceDTO source) {
        this.source = source;
    }
    
    /**
     * Verifica se é uma operação de criação
     */
    public boolean isCreate() {
        return "c".equals(operation) || "r".equals(operation);
    }
    
    /**
     * Verifica se é uma operação de atualização
     */
    public boolean isUpdate() {
        return "u".equals(operation);
    }
    
    /**
     * Verifica se é uma operação de deleção
     */
    public boolean isDelete() {
        return "d".equals(operation);
    }
    
    /**
     * Informações sobre a origem do evento
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SourceDTO {
        @JsonProperty("db")
        private String database;
        
        @JsonProperty("table")
        private String table;
        
        @JsonProperty("ts_ms")
        private Long timestamp;
        
        // Getters e Setters
        public String getDatabase() {
            return database;
        }
        
        public void setDatabase(String database) {
            this.database = database;
        }
        
        public String getTable() {
            return table;
        }
        
        public void setTable(String table) {
            this.table = table;
        }
        
        public Long getTimestamp() {
            return timestamp;
        }
        
        public void setTimestamp(Long timestamp) {
            this.timestamp = timestamp;
        }
    }
}
