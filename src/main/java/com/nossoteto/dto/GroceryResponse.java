package com.nossoteto.dto;

import com.nossoteto.model.Grocery;

import java.time.LocalDateTime;
import java.util.Map;

public class GroceryResponse {

    private String id;
    private String houseId;
    private String itemName;
    private Integer quantity;
    private String assigneeId;
    private Boolean isPurchased;
    private String createdById;
    private String purchasedById;
    private UserRefDto createdBy;
    private UserRefDto assignee;
    private UserRefDto purchasedBy;
    private LocalDateTime purchasedAt;

    public GroceryResponse() {}

    public static GroceryResponse fromEntity(Grocery grocery) {
        return fromEntity(grocery, Map.of());
    }

    public static GroceryResponse fromEntity(Grocery grocery, Map<String, String> userNames) {
        Map<String, String> safeNames = userNames != null ? userNames : Map.of();
        GroceryResponse response = new GroceryResponse();
        response.setId(grocery.getId());
        response.setHouseId(grocery.getHouseId());
        response.setItemName(grocery.getItemName());
        response.setQuantity(grocery.getQuantity());
        response.setAssigneeId(grocery.getAssigneeId());
        response.setIsPurchased(grocery.getIsPurchased());
        response.setCreatedById(grocery.getCreatedById());
        response.setPurchasedById(grocery.getPurchasedById());
        response.setCreatedBy(toUserRef(grocery.getCreatedById(), safeNames));
        response.setAssignee(toUserRef(grocery.getAssigneeId(), safeNames));
        response.setPurchasedBy(toUserRef(grocery.getPurchasedById(), safeNames));
        response.setPurchasedAt(grocery.getPurchasedAt());
        return response;
    }

    private static UserRefDto toUserRef(String userId, Map<String, String> userNames) {
        return userId == null ? null : new UserRefDto(userId, userNames.get(userId));
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getHouseId() { return houseId; }
    public void setHouseId(String houseId) { this.houseId = houseId; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getAssigneeId() { return assigneeId; }
    public void setAssigneeId(String assigneeId) { this.assigneeId = assigneeId; }

    public Boolean getIsPurchased() { return isPurchased; }
    public void setIsPurchased(Boolean isPurchased) { this.isPurchased = isPurchased; }

    public String getCreatedById() { return createdById; }
    public void setCreatedById(String createdById) { this.createdById = createdById; }

    public String getPurchasedById() { return purchasedById; }
    public void setPurchasedById(String purchasedById) { this.purchasedById = purchasedById; }

    public UserRefDto getCreatedBy() { return createdBy; }
    public void setCreatedBy(UserRefDto createdBy) { this.createdBy = createdBy; }

    public UserRefDto getAssignee() { return assignee; }
    public void setAssignee(UserRefDto assignee) { this.assignee = assignee; }

    public UserRefDto getPurchasedBy() { return purchasedBy; }
    public void setPurchasedBy(UserRefDto purchasedBy) { this.purchasedBy = purchasedBy; }

    public LocalDateTime getPurchasedAt() { return purchasedAt; }
    public void setPurchasedAt(LocalDateTime purchasedAt) { this.purchasedAt = purchasedAt; }
}
