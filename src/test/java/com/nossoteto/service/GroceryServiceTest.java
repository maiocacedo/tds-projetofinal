package com.nossoteto.service;

import com.nossoteto.dto.CreateGroceryRequest;
import com.nossoteto.dto.GroceryResponse;
import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.exception.ResourceNotFoundException;
import com.nossoteto.model.Grocery;
import com.nossoteto.repository.GroceryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroceryServiceTest {

    @Mock
    private GroceryRepository groceryRepository;

    @Mock
    private HouseAccessValidator houseAccessValidator;

    private GroceryService groceryService;

    @BeforeEach
    void setUp() {
        groceryService = new GroceryService(groceryRepository, houseAccessValidator);
    }

    @Test
    void createGrocery_SavesWithDefaultQuantityAndIsPurchasedFalse() {
        when(groceryRepository.save(any(Grocery.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateGroceryRequest request = new CreateGroceryRequest();
        request.setItemName("Detergente");

        GroceryResponse response = groceryService.createGrocery("user-1", "house-1", request);

        assertNotNull(response.getId());
        assertEquals("house-1", response.getHouseId());
        assertEquals("Detergente", response.getItemName());
        assertEquals(1, response.getQuantity());
        assertNull(response.getAssigneeId());
        assertFalse(response.getIsPurchased());
    }

    @Test
    void createGrocery_ThrowsWhenQuantityInvalid() {
        CreateGroceryRequest request = new CreateGroceryRequest();
        request.setItemName("Café");
        request.setQuantity(0);

        assertThrows(BusinessRuleException.class, () ->
                groceryService.createGrocery("user-1", "house-1", request));
    }

    @Test
    void assignGrocery_UpdatesAssigneeId() {
        Grocery grocery = new Grocery();
        grocery.setId("item-1");
        grocery.setHouseId("house-1");
        grocery.setItemName("Arroz");
        grocery.setQuantity(2);
        grocery.setIsPurchased(false);

        when(groceryRepository.findById("item-1")).thenReturn(Optional.of(grocery));
        when(groceryRepository.save(any(Grocery.class))).thenAnswer(inv -> inv.getArgument(0));

        GroceryResponse response = groceryService.assignGrocery("user-2", "house-1", "item-1");

        assertEquals("user-2", response.getAssigneeId());
        assertFalse(response.getIsPurchased());
    }

    @Test
    void markAsPurchased_SetsIsPurchasedTrueAndDefaultAssigneeWhenNull() {
        Grocery grocery = new Grocery();
        grocery.setId("item-1");
        grocery.setHouseId("house-1");
        grocery.setItemName("Leite");
        grocery.setQuantity(3);
        grocery.setIsPurchased(false);

        when(groceryRepository.findById("item-1")).thenReturn(Optional.of(grocery));
        when(groceryRepository.save(any(Grocery.class))).thenAnswer(inv -> inv.getArgument(0));

        GroceryResponse response = groceryService.markAsPurchased("user-1", "house-1", "item-1");

        assertTrue(response.getIsPurchased());
        assertEquals("user-1", response.getAssigneeId());
    }

    @Test
    void assignGrocery_ThrowsWhenItemNotInHouse() {
        Grocery grocery = new Grocery();
        grocery.setId("item-1");
        grocery.setHouseId("other-house");

        when(groceryRepository.findById("item-1")).thenReturn(Optional.of(grocery));

        assertThrows(ResourceNotFoundException.class, () ->
                groceryService.assignGrocery("user-1", "house-1", "item-1"));
    }
}
