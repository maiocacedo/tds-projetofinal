package com.nossoteto.service;

import com.nossoteto.dto.CreateGroceryRequest;
import com.nossoteto.dto.GroceryResponse;
import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.exception.ConflictException;
import com.nossoteto.exception.ForbiddenOperationException;
import com.nossoteto.exception.ResourceNotFoundException;
import com.nossoteto.model.Grocery;
import com.nossoteto.model.User;
import com.nossoteto.model.enums.PointReason;
import com.nossoteto.model.enums.PointSourceType;
import com.nossoteto.repository.GroceryRepository;
import com.nossoteto.repository.UserRepository;
import com.nossoteto.util.AppTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroceryServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-27T15:00:00Z");

    @Mock
    private GroceryRepository groceryRepository;

    @Mock
    private HouseAccessValidator houseAccessValidator;

    @Mock
    private PointService pointService;

    @Mock
    private UserRepository userRepository;

    private Clock clock;
    private GroceryService groceryService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        groceryService = new GroceryService(
                groceryRepository, houseAccessValidator, pointService, userRepository, clock);
        lenient().when(userRepository.findAllById(any())).thenReturn(List.of());
    }

    private User user(String id, String name, String houseId) {
        User u = new User();
        u.setId(id);
        u.setName(name);
        u.setHouseId(houseId);
        return u;
    }

    private Grocery grocery(String id, String houseId, String itemName, String createdById) {
        Grocery g = new Grocery();
        g.setId(id);
        g.setHouseId(houseId);
        g.setItemName(itemName);
        g.setQuantity(1);
        g.setIsPurchased(false);
        g.setCreatedById(createdById);
        return g;
    }

    @Test
    void createGrocery_SavesWithCreatedByDefaultQuantityAndIsPurchasedFalse() {
        when(groceryRepository.save(any(Grocery.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findAllById(any())).thenReturn(List.of(user("user-1", "Ana", "house-1")));

        CreateGroceryRequest request = new CreateGroceryRequest();
        request.setItemName("  Detergente  ");

        GroceryResponse response = groceryService.createGrocery("user-1", "house-1", request);

        assertNotNull(response.getId());
        assertEquals("house-1", response.getHouseId());
        assertEquals("Detergente", response.getItemName());
        assertEquals(1, response.getQuantity());
        assertEquals("user-1", response.getCreatedById());
        assertNotNull(response.getCreatedBy());
        assertEquals("user-1", response.getCreatedBy().id());
        assertEquals("Ana", response.getCreatedBy().name());
        assertNull(response.getAssigneeId());
        assertNull(response.getAssignee());
        assertNull(response.getPurchasedBy());
        assertNull(response.getPurchasedAt());
        assertFalse(response.getIsPurchased());
    }

    @Test
    void createGrocery_ThrowsWhenNameMissingOrQuantityInvalid() {
        CreateGroceryRequest blankName = new CreateGroceryRequest();
        blankName.setItemName("   ");
        assertThrows(BusinessRuleException.class, () ->
                groceryService.createGrocery("user-1", "house-1", blankName));

        CreateGroceryRequest badQty = new CreateGroceryRequest();
        badQty.setItemName("Café");
        badQty.setQuantity(0);
        assertThrows(BusinessRuleException.class, () ->
                groceryService.createGrocery("user-1", "house-1", badQty));
    }

    @Test
    void listGroceries_QueriesSevenDayWindowAndBatchResolvesNames() {
        LocalDateTime expectedCutoff = AppTime.nowUtc(clock).minusDays(7);
        Grocery item = grocery("item-1", "house-1", "Arroz", "user-1");
        item.setAssigneeId("user-2");
        item.setPurchasedById("user-2");
        item.setIsPurchased(true);
        item.setPurchasedAt(AppTime.nowUtc(clock).minusDays(2));

        when(groceryRepository.findVisibleForHouse("house-1", expectedCutoff)).thenReturn(List.of(item));
        when(userRepository.findAllById(any())).thenReturn(List.of(
                user("user-1", "Ana", "house-1"),
                user("user-2", "Bruno", "house-1")));

        List<GroceryResponse> result = groceryService.listGroceries("user-1", "house-1");

        assertEquals(1, result.size());
        GroceryResponse dto = result.get(0);
        assertEquals("Ana", dto.getCreatedBy().name());
        assertEquals("Bruno", dto.getAssignee().name());
        assertEquals("Bruno", dto.getPurchasedBy().name());
        assertEquals(item.getPurchasedAt(), dto.getPurchasedAt());
        verify(groceryRepository).findVisibleForHouse("house-1", expectedCutoff);
    }

    @Test
    void updateGrocery_UpdatesUnpurchasedItemAndRejectsPurchasedItem() {
        Grocery item = grocery("item-1", "house-1", "Arroz", "user-1");
        when(groceryRepository.findById("item-1")).thenReturn(Optional.of(item));
        when(groceryRepository.save(any(Grocery.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateGroceryRequest update = new CreateGroceryRequest();
        update.setItemName("  Arroz integral ");
        update.setQuantity(3);

        GroceryResponse response = groceryService.updateGrocery("user-2", "house-1", "item-1", update);
        assertEquals("Arroz integral", response.getItemName());
        assertEquals(3, response.getQuantity());

        item.setIsPurchased(true);
        ConflictException conflict = assertThrows(ConflictException.class, () ->
                groceryService.updateGrocery("user-2", "house-1", "item-1", update));
        assertEquals("item already purchased", conflict.getMessage());
    }

    @Test
    void deleteGrocery_DeletesUnpurchasedItemAndRejectsPurchasedItem() {
        Grocery item = grocery("item-1", "house-1", "Feijão", "user-1");
        when(groceryRepository.findById("item-1")).thenReturn(Optional.of(item));

        groceryService.deleteGrocery("user-2", "house-1", "item-1");
        verify(groceryRepository).delete(item);

        item.setIsPurchased(true);
        ConflictException conflict = assertThrows(ConflictException.class, () ->
                groceryService.deleteGrocery("user-2", "house-1", "item-1"));
        assertEquals("item already purchased", conflict.getMessage());
    }

    @Test
    void assignGrocery_UpdatesAssigneeWhenFreeAndRejectsAlreadyAssignedOrPurchased() {
        Grocery item = grocery("item-1", "house-1", "Arroz", "user-1");
        when(groceryRepository.findById("item-1")).thenReturn(Optional.of(item));
        when(groceryRepository.save(any(Grocery.class))).thenAnswer(inv -> inv.getArgument(0));

        GroceryResponse response = groceryService.assignGrocery("user-2", "house-1", "item-1");
        assertEquals("user-2", response.getAssigneeId());
        assertFalse(response.getIsPurchased());

        ConflictException alreadyAssigned = assertThrows(ConflictException.class, () ->
                groceryService.assignGrocery("user-3", "house-1", "item-1"));
        assertEquals("item already assigned", alreadyAssigned.getMessage());

        ConflictException sameUserAssigned = assertThrows(ConflictException.class, () ->
                groceryService.assignGrocery("user-2", "house-1", "item-1"));
        assertEquals("item already assigned", sameUserAssigned.getMessage());

        item.setIsPurchased(true);
        ConflictException purchased = assertThrows(ConflictException.class, () ->
                groceryService.assignGrocery("user-3", "house-1", "item-1"));
        assertEquals("item already purchased", purchased.getMessage());
    }

    @Test
    void unassignGrocery_AllowsAssigneeAndRejectsOthersOrUnassignedOrPurchased() {
        Grocery item = grocery("item-1", "house-1", "Arroz", "user-1");
        when(groceryRepository.findById("item-1")).thenReturn(Optional.of(item));
        when(groceryRepository.save(any(Grocery.class))).thenAnswer(inv -> inv.getArgument(0));

        ForbiddenOperationException whenFree = assertThrows(ForbiddenOperationException.class, () ->
                groceryService.unassignGrocery("user-2", "house-1", "item-1"));
        assertEquals("only the assignee can unassign", whenFree.getMessage());

        item.setAssigneeId("user-2");
        ForbiddenOperationException whenOther = assertThrows(ForbiddenOperationException.class, () ->
                groceryService.unassignGrocery("user-1", "house-1", "item-1"));
        assertEquals("only the assignee can unassign", whenOther.getMessage());

        GroceryResponse unassigned = groceryService.unassignGrocery("user-2", "house-1", "item-1");
        assertNull(unassigned.getAssigneeId());
        assertNull(unassigned.getAssignee());

        item.setAssigneeId("user-2");
        item.setIsPurchased(true);
        ConflictException purchased = assertThrows(ConflictException.class, () ->
                groceryService.unassignGrocery("user-2", "house-1", "item-1"));
        assertEquals("item already purchased", purchased.getMessage());
    }

    @Test
    void markAsPurchased_BuyAnywayCreditsFivePointsOnlyWhenAddedByAnother() {
        Grocery item = grocery("item-1", "house-1", "Leite", "user-1");
        item.setAssigneeId("user-2");
        when(groceryRepository.findById("item-1")).thenReturn(Optional.of(item));
        when(groceryRepository.save(any(Grocery.class))).thenAnswer(inv -> inv.getArgument(0));

        GroceryResponse response = groceryService.markAsPurchased("user-3", "house-1", "item-1");

        assertTrue(response.getIsPurchased());
        assertEquals("user-3", response.getAssigneeId());
        assertEquals("user-3", response.getPurchasedById());
        assertEquals(AppTime.nowUtc(clock), response.getPurchasedAt());
        assertEquals("user-2", item.getPreviousAssigneeId());
        verify(pointService).creditIfAbsent(
                "user-3", "house-1", PointReason.GROCERY_BOUGHT, 5,
                PointSourceType.GROCERY, "item-1", "", "Leite");

        ConflictException alreadyPurchased = assertThrows(ConflictException.class, () ->
                groceryService.markAsPurchased("user-1", "house-1", "item-1"));
        assertEquals("item already purchased", alreadyPurchased.getMessage());
        assertEquals("user-2", item.getPreviousAssigneeId());
    }

    @Test
    void markAsPurchased_DoesNotCreditPointsWhenCreatorBuysOwnItem() {
        Grocery item = grocery("item-1", "house-1", "Leite", "user-1");
        when(groceryRepository.findById("item-1")).thenReturn(Optional.of(item));
        when(groceryRepository.save(any(Grocery.class))).thenAnswer(inv -> inv.getArgument(0));

        groceryService.markAsPurchased("user-1", "house-1", "item-1");

        verify(pointService, never()).creditIfAbsent(
                anyString(), anyString(), any(), anyInt(), any(), anyString(), anyString(), anyString());
    }

    @Test
    void unpurchaseGrocery_RestoresAssigneeAndRemovesPointsWithinTenMinutes() {
        Grocery item = grocery("item-1", "house-1", "Leite", "user-1");
        item.setIsPurchased(true);
        item.setAssigneeId("user-3");
        item.setPurchasedById("user-3");
        item.setPreviousAssigneeId("user-2");
        item.setPurchasedAt(AppTime.nowUtc(clock).minusMinutes(10));

        when(groceryRepository.findById("item-1")).thenReturn(Optional.of(item));
        when(userRepository.findById("user-2")).thenReturn(Optional.of(user("user-2", "Bruno", "house-1")));
        when(groceryRepository.save(any(Grocery.class))).thenAnswer(inv -> inv.getArgument(0));

        GroceryResponse response = groceryService.unpurchaseGrocery("user-3", "house-1", "item-1");

        assertFalse(response.getIsPurchased());
        assertEquals("user-2", response.getAssigneeId());
        assertNull(response.getPurchasedById());
        assertNull(response.getPurchasedBy());
        assertNull(response.getPurchasedAt());
        assertNull(item.getPreviousAssigneeId());
        verify(pointService).removeByOrigin(PointSourceType.GROCERY, "item-1", "");
    }

    @Test
    void unpurchaseGrocery_ClearsPreviousAssigneeIfUserLeftHouse() {
        Grocery item = grocery("item-1", "house-1", "Leite", "user-1");
        item.setIsPurchased(true);
        item.setAssigneeId("user-3");
        item.setPurchasedById("user-3");
        item.setPreviousAssigneeId("user-2");
        item.setPurchasedAt(AppTime.nowUtc(clock).minusMinutes(2));

        when(groceryRepository.findById("item-1")).thenReturn(Optional.of(item));
        when(userRepository.findById("user-2")).thenReturn(Optional.of(user("user-2", "Bruno", "other-house")));
        when(groceryRepository.save(any(Grocery.class))).thenAnswer(inv -> inv.getArgument(0));

        GroceryResponse response = groceryService.unpurchaseGrocery("user-3", "house-1", "item-1");

        assertNull(response.getAssigneeId());
        assertNull(response.getAssignee());
    }

    @Test
    void unpurchaseGrocery_ValidatesStateBuyerAndWindow() {
        Grocery item = grocery("item-1", "house-1", "Leite", "user-1");
        when(groceryRepository.findById("item-1")).thenReturn(Optional.of(item));

        ConflictException notPurchased = assertThrows(ConflictException.class, () ->
                groceryService.unpurchaseGrocery("user-3", "house-1", "item-1"));
        assertEquals("item is not purchased", notPurchased.getMessage());

        item.setIsPurchased(true);
        item.setPurchasedById("user-3");
        item.setPurchasedAt(AppTime.nowUtc(clock).minusMinutes(10).minusSeconds(1));

        ForbiddenOperationException wrongBuyer = assertThrows(ForbiddenOperationException.class, () ->
                groceryService.unpurchaseGrocery("user-1", "house-1", "item-1"));
        assertEquals("only the buyer can unpurchase", wrongBuyer.getMessage());

        BusinessRuleException expired = assertThrows(BusinessRuleException.class, () ->
                groceryService.unpurchaseGrocery("user-3", "house-1", "item-1"));
        assertEquals("unpurchase window expired", expired.getMessage());
    }

    @Test
    void assignGrocery_ThrowsWhenItemNotInHouse() {
        Grocery item = grocery("item-1", "other-house", "Arroz", "user-1");
        when(groceryRepository.findById("item-1")).thenReturn(Optional.of(item));

        assertThrows(ResourceNotFoundException.class, () ->
                groceryService.assignGrocery("user-1", "house-1", "item-1"));
    }
}
