package com.nossoteto.dto;

import com.nossoteto.model.Grocery;

public class GroceryResponse {

    private String id;
    private String houseId;
    private String itemName;
    private Integer quantity;
    private String assigneeId;
    private Boolean isPurchased;

    public GroceryResponse() {}

    public static GroceryResponse fromEntity(Grocery grocery) {
        GroceryResponse response = new GroceryResponse();
        response.setId(grocery.getId());
        response.setHouseId(grocery.getHouseId());
        response.setItemName(grocery.getItemName());
        response.setQuantity(grocery.getQuantity());
        response.setAssigneeId(grocery.getAssigneeId());
        response.setIsPurchased(grocery.getIsPurchased());
        return response;
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
}
