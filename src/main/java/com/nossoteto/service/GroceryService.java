package com.nossoteto.service;

import com.nossoteto.dto.CreateGroceryRequest;
import com.nossoteto.dto.GroceryResponse;
import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.exception.ResourceNotFoundException;
import com.nossoteto.model.Grocery;
import com.nossoteto.repository.GroceryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GroceryService {

    private final GroceryRepository groceryRepository;
    private final HouseAccessValidator houseAccessValidator;

    public GroceryService(
            GroceryRepository groceryRepository,
            HouseAccessValidator houseAccessValidator) {
        this.groceryRepository = groceryRepository;
        this.houseAccessValidator = houseAccessValidator;
    }

    @Transactional
    public GroceryResponse createGrocery(String userId, String houseId, CreateGroceryRequest request) {
        houseAccessValidator.requireHouseMember(userId, houseId);

        if (request == null || request.getItemName() == null || request.getItemName().isBlank()) {
            throw new BusinessRuleException("Grocery item name is required");
        }

        int quantity = (request.getQuantity() == null) ? 1 : request.getQuantity();
        if (quantity <= 0) {
            throw new BusinessRuleException("Grocery item quantity must be greater than zero");
        }

        Grocery grocery = new Grocery();
        grocery.setId(UUID.randomUUID().toString());
        grocery.setHouseId(houseId);
        grocery.setItemName(request.getItemName().trim());
        grocery.setQuantity(quantity);
        grocery.setIsPurchased(false);

        Grocery saved = groceryRepository.save(grocery);
        return GroceryResponse.fromEntity(saved);
    }

    public List<GroceryResponse> listGroceries(String userId, String houseId) {
        houseAccessValidator.requireHouseMember(userId, houseId);
        return groceryRepository.findByHouseId(houseId).stream()
                .map(GroceryResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public GroceryResponse assignGrocery(String userId, String houseId, String itemId) {
        houseAccessValidator.requireHouseMember(userId, houseId);
        Grocery grocery = findGroceryInHouse(houseId, itemId);

        grocery.setAssigneeId(userId);
        Grocery saved = groceryRepository.save(grocery);
        return GroceryResponse.fromEntity(saved);
    }

    @Transactional
    public GroceryResponse markAsPurchased(String userId, String houseId, String itemId) {
        houseAccessValidator.requireHouseMember(userId, houseId);
        Grocery grocery = findGroceryInHouse(houseId, itemId);

        grocery.setIsPurchased(true);
        if (grocery.getAssigneeId() == null) {
            grocery.setAssigneeId(userId);
        }

        Grocery saved = groceryRepository.save(grocery);
        return GroceryResponse.fromEntity(saved);
    }

    private Grocery findGroceryInHouse(String houseId, String itemId) {
        Grocery grocery = groceryRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Grocery item not found: " + itemId));
        if (!houseId.equals(grocery.getHouseId())) {
            throw new ResourceNotFoundException("Grocery item not found in this house");
        }
        return grocery;
    }
}
