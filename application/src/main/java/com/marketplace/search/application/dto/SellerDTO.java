package com.marketplace.search.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO para vendedor
 */
public class SellerDTO {
    
    @NotNull
    @NotBlank
    @JsonProperty("id")
    private String id;
    
    @NotNull
    @NotBlank
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("type")
    private String type;
    
    @JsonProperty("reputation")
    private SellerReputationDTO reputation;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("member_since")
    private String memberSince;

    // Constructors
    public SellerDTO() {}

    public SellerDTO(String id, String name, String type, String status) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.status = status;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public SellerReputationDTO getReputation() { return reputation; }
    public void setReputation(SellerReputationDTO reputation) { this.reputation = reputation; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMemberSince() { return memberSince; }
    public void setMemberSince(String memberSince) { this.memberSince = memberSince; }

    @Override
    public String toString() {
        return "SellerDTO{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}