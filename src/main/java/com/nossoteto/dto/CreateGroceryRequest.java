package com.nossoteto.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public class CreateGroceryRequest {

    @JsonAlias("nomeItem")
    private String itemName;

    @JsonAlias("quantidade")
    private Integer quantity;

    public CreateGroceryRequest() {}

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}
