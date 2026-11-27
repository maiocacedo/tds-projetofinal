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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GroceryService {

    static final int PURCHASE_POINTS = 5;
    static final Duration UNPURCHASE_WINDOW = Duration.ofMinutes(10);
    static final int VISIBLE_PURCHASED_DAYS = 7;

    static final String ITEM_ALREADY_PURCHASED = "item already purchased";
    static final String ITEM_ALREADY_ASSIGNED = "item already assigned";
    static final String ONLY_ASSIGNEE_CAN_UNASSIGN = "only the assignee can unassign";
    static final String ITEM_NOT_PURCHASED = "item is not purchased";
    static final String ONLY_BUYER_CAN_UNPURCHASE = "only the buyer can unpurchase";
    static final String UNPURCHASE_WINDOW_EXPIRED = "unpurchase window expired";

    private final GroceryRepository groceryRepository;
    private final HouseAccessValidator houseAccessValidator;
    private final PointService pointService;
    private final UserRepository userRepository;
    private final Clock clock;

    public GroceryService(
            GroceryRepository groceryRepository,
            HouseAccessValidator houseAccessValidator,
            PointService pointService,
            UserRepository userRepository,
            Clock clock) {
        this.groceryRepository = groceryRepository;
        this.houseAccessValidator = houseAccessValidator;
        this.pointService = pointService;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public GroceryResponse createGrocery(String userId, String houseId, CreateGroceryRequest request) {
        houseAccessValidator.requireHouseMember(userId, houseId);
        ValidatedItem validated = validateItemRequest(request);

        Grocery grocery = new Grocery();
        grocery.setId(UUID.randomUUID().toString());
        grocery.setHouseId(houseId);
        grocery.setItemName(validated.itemName());
        grocery.setQuantity(validated.quantity());
        grocery.setIsPurchased(false);
        grocery.setCreatedById(userId);

        Grocery saved = groceryRepository.save(grocery);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<GroceryResponse> listGroceries(String userId, String houseId) {
        houseAccessValidator.requireHouseMember(userId, houseId);
        LocalDateTime cutoff = AppTime.nowUtc(clock).minusDays(VISIBLE_PURCHASED_DAYS);
        List<Grocery> items = groceryRepository.findVisibleForHouse(houseId, cutoff);

        Set<String> userIds = new HashSet<>();
        for (Grocery item : items) {
            userIds.add(item.getCreatedById());
            userIds.add(item.getAssigneeId());
            userIds.add(item.getPurchasedById());
        }
        Map<String, String> userNames = resolveNames(userIds);

        return items.stream()
                .map(item -> GroceryResponse.fromEntity(item, userNames))
                .collect(Collectors.toList());
    }

    @Transactional
    public GroceryResponse updateGrocery(
            String userId,
            String houseId,
            String itemId,
            CreateGroceryRequest request) {
        houseAccessValidator.requireHouseMember(userId, houseId);
        Grocery grocery = findGroceryInHouse(houseId, itemId);
        requireNotPurchased(grocery);

        ValidatedItem validated = validateItemRequest(request);
        grocery.setItemName(validated.itemName());
        grocery.setQuantity(validated.quantity());

        return toResponse(groceryRepository.save(grocery));
    }

    @Transactional
    public void deleteGrocery(String userId, String houseId, String itemId) {
        houseAccessValidator.requireHouseMember(userId, houseId);
        Grocery grocery = findGroceryInHouse(houseId, itemId);
        requireNotPurchased(grocery);

        groceryRepository.delete(grocery);
    }

    @Transactional
    public GroceryResponse assignGrocery(String userId, String houseId, String itemId) {
        houseAccessValidator.requireHouseMember(userId, houseId);
        Grocery grocery = findGroceryInHouse(houseId, itemId);
        requireNotPurchased(grocery);

        if (grocery.getAssigneeId() != null) {
            throw new ConflictException(ITEM_ALREADY_ASSIGNED);
        }

        grocery.setAssigneeId(userId);
        return toResponse(groceryRepository.save(grocery));
    }

    @Transactional
    public GroceryResponse unassignGrocery(String userId, String houseId, String itemId) {
        houseAccessValidator.requireHouseMember(userId, houseId);
        Grocery grocery = findGroceryInHouse(houseId, itemId);
        requireNotPurchased(grocery);

        if (!userId.equals(grocery.getAssigneeId())) {
            throw new ForbiddenOperationException(ONLY_ASSIGNEE_CAN_UNASSIGN);
        }

        grocery.setAssigneeId(null);
        return toResponse(groceryRepository.save(grocery));
    }

    @Transactional
    public GroceryResponse markAsPurchased(String userId, String houseId, String itemId) {
        houseAccessValidator.requireHouseMember(userId, houseId);
        Grocery grocery = findGroceryInHouse(houseId, itemId);
        requireNotPurchased(grocery);

        grocery.setPreviousAssigneeId(grocery.getAssigneeId());
        grocery.setAssigneeId(userId);
        grocery.setPurchasedById(userId);
        grocery.setIsPurchased(true);
        grocery.setPurchasedAt(AppTime.nowUtc(clock));

        Grocery saved = groceryRepository.save(grocery);
        if (saved.getCreatedById() != null && !saved.getCreatedById().equals(userId)) {
            pointService.creditIfAbsent(
                    userId,
                    houseId,
                    PointReason.GROCERY_BOUGHT,
                    PURCHASE_POINTS,
                    PointSourceType.GROCERY,
                    saved.getId(),
                    "",
                    saved.getItemName());
        }
        return toResponse(saved);
    }

    @Transactional
    public GroceryResponse unpurchaseGrocery(String userId, String houseId, String itemId) {
        houseAccessValidator.requireHouseMember(userId, houseId);
        Grocery grocery = findGroceryInHouse(houseId, itemId);

        if (!Boolean.TRUE.equals(grocery.getIsPurchased())) {
            throw new ConflictException(ITEM_NOT_PURCHASED);
        }
        if (!userId.equals(grocery.getPurchasedById())) {
            throw new ForbiddenOperationException(ONLY_BUYER_CAN_UNPURCHASE);
        }

        LocalDateTime now = AppTime.nowUtc(clock);
        if (grocery.getPurchasedAt() == null
                || Duration.between(grocery.getPurchasedAt(), now).compareTo(UNPURCHASE_WINDOW) > 0) {
            throw new BusinessRuleException(UNPURCHASE_WINDOW_EXPIRED);
        }

        grocery.setAssigneeId(resolveRestorableAssignee(houseId, grocery.getPreviousAssigneeId()));
        grocery.setPurchasedById(null);
        grocery.setPurchasedAt(null);
        grocery.setPreviousAssigneeId(null);
        grocery.setIsPurchased(false);

        Grocery saved = groceryRepository.save(grocery);
        pointService.removeByOrigin(PointSourceType.GROCERY, saved.getId(), "");
        return toResponse(saved);
    }

    private String resolveRestorableAssignee(String houseId, String previousAssigneeId) {
        if (previousAssigneeId == null) {
            return null;
        }
        return userRepository.findById(previousAssigneeId)
                .filter(user -> houseId.equals(user.getHouseId()))
                .map(User::getId)
                .orElse(null);
    }

    private void requireNotPurchased(Grocery grocery) {
        if (Boolean.TRUE.equals(grocery.getIsPurchased())) {
            throw new ConflictException(ITEM_ALREADY_PURCHASED);
        }
    }

    private ValidatedItem validateItemRequest(CreateGroceryRequest request) {
        if (request == null || request.getItemName() == null || request.getItemName().isBlank()) {
            throw new BusinessRuleException("Grocery item name is required");
        }
        int quantity = (request.getQuantity() == null) ? 1 : request.getQuantity();
        if (quantity <= 0) {
            throw new BusinessRuleException("Grocery item quantity must be greater than zero");
        }
        return new ValidatedItem(request.getItemName().trim(), quantity);
    }

    private Grocery findGroceryInHouse(String houseId, String itemId) {
        Grocery grocery = groceryRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Grocery item not found: " + itemId));
        if (!houseId.equals(grocery.getHouseId())) {
            throw new ResourceNotFoundException("Grocery item not found in this house");
        }
        return grocery;
    }

    private GroceryResponse toResponse(Grocery grocery) {
        Map<String, String> names = resolveNames(List.of(
                grocery.getCreatedById() == null ? "" : grocery.getCreatedById(),
                grocery.getAssigneeId() == null ? "" : grocery.getAssigneeId(),
                grocery.getPurchasedById() == null ? "" : grocery.getPurchasedById()));
        return GroceryResponse.fromEntity(grocery, names);
    }

    private Map<String, String> resolveNames(Collection<String> userIds) {
        Set<String> ids = userIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, String> names = new HashMap<>();
        for (User user : userRepository.findAllById(ids)) {
            names.put(user.getId(), user.getName());
        }
        return names;
    }

    private record ValidatedItem(String itemName, int quantity) {}
}
