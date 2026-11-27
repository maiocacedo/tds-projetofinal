package com.nossoteto.service;

import com.nossoteto.dto.ChoreResponse;
import com.nossoteto.dto.ChoreSwapDto;
import com.nossoteto.dto.CreateChoreRequest;
import com.nossoteto.dto.CreateChoreSwapRequest;
import com.nossoteto.dto.UpdateRotationRequest;
import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.exception.ForbiddenOperationException;
import com.nossoteto.exception.ResourceNotFoundException;
import com.nossoteto.model.Chore;
import com.nossoteto.model.ChoreSwap;
import com.nossoteto.model.House;
import com.nossoteto.model.User;
import com.nossoteto.model.enums.ChoreFrequency;
import com.nossoteto.model.enums.ChoreStatus;
import com.nossoteto.model.enums.ChoreSwapStatus;
import com.nossoteto.model.enums.ChoreType;
import com.nossoteto.model.enums.PointReason;
import com.nossoteto.model.enums.PointSourceType;
import com.nossoteto.repository.ChoreRepository;
import com.nossoteto.repository.ChoreSwapRepository;
import com.nossoteto.repository.HouseRepository;
import com.nossoteto.repository.UserRepository;
import com.nossoteto.util.AppTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ChoreService {

    static final int ON_TIME_POINTS = 20;

    static final String ONLY_ASSIGNEE_CAN_COMPLETE = "only the assignee can complete";
    static final String ALREADY_COMPLETED = "chore already completed for this cycle";
    static final String NOTHING_TO_UNDO = "chore has no completions to undo";
    static final String ASSIGNEE_CANNOT_REOPEN = "assignee cannot reopen own chore";
    static final String NOT_COMPLETED = "chore is not completed";
    static final String INVALID_ROTATION = "rotation order must match current house members";
    static final String INVALID_SWAP = "invalid chore swap";
    static final String ONLY_TARGET_CAN_ANSWER = "only the target resident can answer the swap";

    /** Residents in the order a fresh rotation uses: who joined first, then id to break ties. */
    private static final Comparator<User> JOIN_ORDER = Comparator
            .comparing(User::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(User::getId);

    private final ChoreRepository choreRepository;
    private final ChoreSwapRepository choreSwapRepository;
    private final UserRepository userRepository;
    private final HouseRepository houseRepository;
    private final HouseAccessValidator houseAccessValidator;
    private final PointService pointService;
    private final Clock clock;

    public ChoreService(
            ChoreRepository choreRepository,
            ChoreSwapRepository choreSwapRepository,
            UserRepository userRepository,
            HouseRepository houseRepository,
            HouseAccessValidator houseAccessValidator,
            PointService pointService,
            Clock clock) {
        this.choreRepository = choreRepository;
        this.choreSwapRepository = choreSwapRepository;
        this.userRepository = userRepository;
        this.houseRepository = houseRepository;
        this.houseAccessValidator = houseAccessValidator;
        this.pointService = pointService;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ create and list

    @Transactional
    public ChoreResponse createChore(String userId, String houseId, CreateChoreRequest request) {
        houseAccessValidator.requireHouseMember(userId, houseId);

        if (request == null || request.getDescription() == null || request.getDescription().isBlank()) {
            throw new BusinessRuleException("Chore description is required");
        }

        ChoreType choreType = ChoreType.fromString(request.getChoreType());
        ChoreFrequency frequency = ChoreFrequency.fromString(request.getFrequency());
        HouseMembers members = currentMembers(houseId);

        List<String> rotationOrder;
        if (request.getRotationOrder() != null) {
            requireExactMembers(request.getRotationOrder(), members);
            rotationOrder = List.copyOf(request.getRotationOrder());
        } else {
            rotationOrder = members.orderedIds();
        }

        String targetAssigneeId = request.getAssigneeId();
        if (targetAssigneeId == null || targetAssigneeId.isBlank()) {
            targetAssigneeId = request.getRotationOrder() != null ? rotationOrder.get(0) : userId;
        } else {
            User assignee = userRepository.findById(targetAssigneeId)
                    .orElseThrow(() -> new BusinessRuleException("Assignee user not found"));
            if (!houseId.equals(assignee.getHouseId())) {
                throw new BusinessRuleException("Assignee must be a member of the house");
            }
        }

        Chore chore = new Chore();
        chore.setId(UUID.randomUUID().toString());
        chore.setHouseId(houseId);
        chore.setDescription(request.getDescription().trim());
        chore.setChoreType(choreType);
        chore.setCurrentAssigneeId(targetAssigneeId);
        chore.setStatus(ChoreStatus.PENDING);
        chore.setLastResetDate(AppTime.today(clock));
        chore.setFrequency(frequency);
        chore.setLinkedToGroceries(Boolean.TRUE.equals(request.getLinkedToGroceries()));
        chore.assignRotationOrderIds(rotationOrder);

        Chore saved = choreRepository.save(chore);
        return toResponse(saved, members, List.of());
    }

    /** Kept with this signature: {@code DashboardService} uses it for the home screen. */
    @Transactional(readOnly = true)
    public List<ChoreResponse> listChores(String userId, String houseId) {
        houseAccessValidator.requireHouseMember(userId, houseId);
        return toResponses(houseId, choreRepository.findByHouseId(houseId), currentMembers(houseId));
    }

    // ------------------------------------------------------------------ completions

    @Transactional
    public ChoreResponse completeChore(String userId, String houseId, String choreId) {
        houseAccessValidator.requireHouseMember(userId, houseId);
        Chore chore = requireChoreInHouse(choreId, houseId);
        HouseMembers members = currentMembers(houseId);

        if (!userId.equals(effectiveAssignee(chore, members))) {
            throw new ForbiddenOperationException(ONLY_ASSIGNEE_CAN_COMPLETE);
        }
        int required = required(chore);
        int completions = completions(chore);
        if (chore.getStatus() == ChoreStatus.COMPLETED || completions >= required) {
            throw new BusinessRuleException(ALREADY_COMPLETED);
        }

        completions++;
        chore.setCompletionsThisCycle(completions);
        if (completions >= required) {
            chore.setStatus(ChoreStatus.COMPLETED);
        }
        return toResponse(choreRepository.save(chore), members, pendingSwapsOfHouse(houseId));
    }

    @Transactional
    public ChoreResponse uncompleteChore(String userId, String houseId, String choreId) {
        houseAccessValidator.requireHouseMember(userId, houseId);
        Chore chore = requireChoreInHouse(choreId, houseId);
        HouseMembers members = currentMembers(houseId);

        if (!userId.equals(effectiveAssignee(chore, members))) {
            throw new ForbiddenOperationException(ONLY_ASSIGNEE_CAN_COMPLETE);
        }
        int completions = completions(chore);
        if (completions <= 0) {
            throw new BusinessRuleException(NOTHING_TO_UNDO);
        }

        completions--;
        chore.setCompletionsThisCycle(completions);
        if (completions < required(chore)) {
            chore.setStatus(Boolean.TRUE.equals(chore.getDelayedThisCycle())
                    ? ChoreStatus.DELAYED : ChoreStatus.PENDING);
        }
        return toResponse(choreRepository.save(chore), members, pendingSwapsOfHouse(houseId));
    }

    /**
     * Another resident says the chore was marked done but not done: it goes back to DELAYED right away and,
     * because it was delayed in this cycle, the close no longer credits the on-time points.
     */
    @Transactional
    public ChoreResponse reopenChore(String userId, String houseId, String choreId) {
        houseAccessValidator.requireHouseMember(userId, houseId);
        Chore chore = requireChoreInHouse(choreId, houseId);
        HouseMembers members = currentMembers(houseId);

        if (userId.equals(effectiveAssignee(chore, members))) {
            throw new ForbiddenOperationException(ASSIGNEE_CANNOT_REOPEN);
        }
        if (chore.getStatus() != ChoreStatus.COMPLETED) {
            throw new BusinessRuleException(NOT_COMPLETED);
        }

        chore.setStatus(ChoreStatus.DELAYED);
        chore.setDelayedThisCycle(true);
        chore.setCompletionsThisCycle(Math.max(0, required(chore) - 1));
        chore.setReopenedById(userId);
        chore.setReopenedAt(AppTime.nowUtc(clock));
        return toResponse(choreRepository.save(chore), members, pendingSwapsOfHouse(houseId));
    }

    // ------------------------------------------------------------------ rotation order

    @Transactional
    public ChoreResponse updateRotation(String userId, String houseId, String choreId, UpdateRotationRequest request) {
        houseAccessValidator.requireHouseAdmin(userId, houseId);
        Chore chore = requireChoreInHouse(choreId, houseId);
        HouseMembers members = currentMembers(houseId);

        requireExactMembers(request == null ? null : request.getOrder(), members);
        chore.assignRotationOrderIds(List.copyOf(request.getOrder()));
        return toResponse(choreRepository.save(chore), members, pendingSwapsOfHouse(houseId));
    }

    // ------------------------------------------------------------------ swaps

    @Transactional(readOnly = true)
    public List<ChoreSwapDto> listPendingSwaps(String userId, String houseId) {
        houseAccessValidator.requireHouseMember(userId, houseId);
        List<ChoreSwap> swaps = pendingSwapsOfHouse(houseId);
        Map<String, Chore> chores = choreRepository.findByHouseId(houseId).stream()
                .collect(Collectors.toMap(Chore::getId, c -> c));
        Map<String, String> names = namesFor(currentMembers(houseId), swapUserIds(swaps));
        return swaps.stream().map(s -> toSwapDto(s, chores, names)).toList();
    }

    @Transactional
    public ChoreSwapDto createSwap(String userId, String houseId, String choreId, CreateChoreSwapRequest request) {
        houseAccessValidator.requireHouseMember(userId, houseId);
        Chore requesterChore = requireChoreInHouse(choreId, houseId);
        HouseMembers members = currentMembers(houseId);

        String targetChoreId = request == null ? null : request.getTargetChoreId();
        if (targetChoreId == null || targetChoreId.isBlank() || targetChoreId.equals(choreId)) {
            throw new BusinessRuleException(INVALID_SWAP);
        }
        Chore targetChore = choreRepository.findById(targetChoreId)
                .filter(c -> houseId.equals(c.getHouseId()))
                .orElseThrow(() -> new BusinessRuleException(INVALID_SWAP));

        String targetUserId = effectiveAssignee(targetChore, members);
        if (!userId.equals(effectiveAssignee(requesterChore, members))
                || targetUserId == null
                || targetUserId.equals(userId)
                || !members.contains(targetUserId)
                || hasActiveTemporaryAssignee(requesterChore, members)
                || hasActiveTemporaryAssignee(targetChore, members)
                || !choreSwapRepository.findByStatusInvolvingChore(ChoreSwapStatus.PENDING, requesterChore.getId()).isEmpty()
                || !choreSwapRepository.findByStatusInvolvingChore(ChoreSwapStatus.PENDING, targetChore.getId()).isEmpty()) {
            throw new BusinessRuleException(INVALID_SWAP);
        }

        ChoreSwap swap = new ChoreSwap();
        swap.setId(UUID.randomUUID().toString());
        swap.setHouseId(houseId);
        swap.setRequesterChoreId(requesterChore.getId());
        swap.setTargetChoreId(targetChore.getId());
        swap.setRequesterId(userId);
        swap.setTargetUserId(targetUserId);
        swap.setStatus(ChoreSwapStatus.PENDING);
        swap.setCycleNumber(cycleNumber(requesterChore));
        swap.setCreatedAt(AppTime.nowUtc(clock));
        ChoreSwap saved = choreSwapRepository.save(swap);

        return toSwapDto(saved,
                Map.of(requesterChore.getId(), requesterChore, targetChore.getId(), targetChore),
                namesFor(members, swapUserIds(List.of(saved))));
    }

    /**
     * The target resident takes the requester's chore and hands over theirs, for the current cycle only:
     * the rotation slot ({@code currentAssigneeId}) of both chores stays untouched.
     */
    @Transactional
    public ChoreSwapDto acceptSwap(String userId, String houseId, String swapId) {
        houseAccessValidator.requireHouseMember(userId, houseId);
        ChoreSwap swap = requirePendingSwapForTarget(userId, houseId, swapId);
        HouseMembers members = currentMembers(houseId);

        Chore requesterChore = choreRepository.findById(swap.getRequesterChoreId())
                .filter(c -> houseId.equals(c.getHouseId()))
                .orElseThrow(() -> new BusinessRuleException(INVALID_SWAP));
        Chore targetChore = choreRepository.findById(swap.getTargetChoreId())
                .filter(c -> houseId.equals(c.getHouseId()))
                .orElseThrow(() -> new BusinessRuleException(INVALID_SWAP));

        boolean stillValid = Objects.equals(cycleNumber(requesterChore), swap.getCycleNumber())
                && members.contains(swap.getRequesterId())
                && members.contains(swap.getTargetUserId())
                && swap.getRequesterId().equals(effectiveAssignee(requesterChore, members))
                && swap.getTargetUserId().equals(effectiveAssignee(targetChore, members));
        if (!stillValid) {
            throw new BusinessRuleException(INVALID_SWAP);
        }

        swap.setStatus(ChoreSwapStatus.ACCEPTED);
        requesterChore.setTemporaryAssigneeId(swap.getTargetUserId());
        targetChore.setTemporaryAssigneeId(swap.getRequesterId());
        choreRepository.saveAll(List.of(requesterChore, targetChore));
        ChoreSwap saved = choreSwapRepository.save(swap);

        return toSwapDto(saved,
                Map.of(requesterChore.getId(), requesterChore, targetChore.getId(), targetChore),
                namesFor(members, swapUserIds(List.of(saved))));
    }

    @Transactional
    public ChoreSwapDto rejectSwap(String userId, String houseId, String swapId) {
        houseAccessValidator.requireHouseMember(userId, houseId);
        ChoreSwap swap = requirePendingSwapForTarget(userId, houseId, swapId);
        swap.setStatus(ChoreSwapStatus.REJECTED);
        ChoreSwap saved = choreSwapRepository.save(swap);

        Map<String, Chore> chores = new HashMap<>();
        choreRepository.findById(saved.getRequesterChoreId()).ifPresent(c -> chores.put(c.getId(), c));
        choreRepository.findById(saved.getTargetChoreId()).ifPresent(c -> chores.put(c.getId(), c));
        return toSwapDto(saved, chores, namesFor(currentMembers(houseId), swapUserIds(List.of(saved))));
    }

    private ChoreSwap requirePendingSwapForTarget(String userId, String houseId, String swapId) {
        ChoreSwap swap = choreSwapRepository.findById(swapId)
                .filter(s -> houseId.equals(s.getHouseId()))
                .orElseThrow(() -> new ResourceNotFoundException("Chore swap not found: " + swapId));
        if (!userId.equals(swap.getTargetUserId())) {
            throw new ForbiddenOperationException(ONLY_TARGET_CAN_ANSWER);
        }
        if (swap.getStatus() != ChoreSwapStatus.PENDING) {
            throw new BusinessRuleException(INVALID_SWAP);
        }
        return swap;
    }

    // ------------------------------------------------------------------ closing the cycle

    /**
     * Manual close by the house admin (e.g. on Friday). It always runs and stamps the ISO week, so the
     * Sunday job of that same week skips these chores instead of rotating them twice.
     */
    @Transactional
    public List<ChoreResponse> rotateHouseChores(String userId, String houseId) {
        houseAccessValidator.requireHouseAdmin(userId, houseId);
        return closeHouseCycle(houseId, false);
    }

    @Scheduled(cron = "0 0 0 * * SUN", zone = "America/Sao_Paulo")
    @Transactional
    public void rotateAllHousesScheduled() {
        List<House> houses = houseRepository.findAll();
        for (House house : houses) {
            closeHouseCycle(house.getId(), true);
        }
    }

    @Transactional
    public List<ChoreResponse> closeHouseCycle(String houseId, boolean scheduled) {
        HouseMembers members = currentMembers(houseId);
        List<Chore> chores = choreRepository.findByHouseId(houseId);
        LocalDate today = AppTime.today(clock);
        String weekKey = isoWeekKey(today);

        for (Chore chore : chores) {
            if (scheduled && weekKey.equals(chore.getLastClosedWeek())) {
                continue;
            }

            // A biweekly cycle spans two weeks: the first close only counts the week.
            if (chore.getFrequency() == ChoreFrequency.BIWEEKLY && weeksInCycle(chore) == 0) {
                chore.setWeeksInCycle(1);
                chore.setLastClosedWeek(weekKey);
                continue;
            }

            // An unfinished chore (PENDING or already DELAYED) stays DELAYED with the same resident until
            // they complete it, so the delay is not handed over to the next person in the rotation (RF14).
            if (chore.getStatus() != ChoreStatus.COMPLETED) {
                chore.setStatus(ChoreStatus.DELAYED);
                chore.setDelayedThisCycle(true);
                chore.setLastClosedWeek(weekKey);
                chore.setLastResetDate(today);
                continue;
            }

            // Both are resolved before any reset: the credit goes to whoever covered the chore this cycle,
            // the rotation moves on from the slot owner, never from the temporary assignee.
            List<String> order = effectiveOrder(chore, members);
            String doneBy = effectiveAssignee(chore, members, order);
            String nextOwner = nextAssignee(chore, order);

            if (!Boolean.TRUE.equals(chore.getDelayedThisCycle()) && doneBy != null) {
                pointService.creditIfAbsent(doneBy, houseId, PointReason.CHORE_ON_TIME, ON_TIME_POINTS,
                        PointSourceType.CHORE, chore.getId(), "cycle-" + cycleNumber(chore), chore.getDescription());
            }

            chore.setCycleNumber(cycleNumber(chore) + 1);
            chore.setCompletionsThisCycle(0);
            chore.setDelayedThisCycle(false);
            chore.setReopenedById(null);
            chore.setReopenedAt(null);
            chore.setTemporaryAssigneeId(null);
            chore.setWeeksInCycle(0);
            chore.setStatus(ChoreStatus.PENDING);
            chore.setLastClosedWeek(weekKey);
            chore.setLastResetDate(today);
            cancelPendingSwaps(chore.getId());

            if (chore.getChoreType() == ChoreType.ROTATIONAL && nextOwner != null) {
                chore.setCurrentAssigneeId(nextOwner);
            }
        }

        List<Chore> saved = choreRepository.saveAll(chores);
        return toResponses(houseId, saved, members);
    }

    private void cancelPendingSwaps(String choreId) {
        List<ChoreSwap> pending = choreSwapRepository.findByStatusInvolvingChore(ChoreSwapStatus.PENDING, choreId);
        if (pending.isEmpty()) {
            return;
        }
        pending.forEach(s -> s.setStatus(ChoreSwapStatus.CANCELLED));
        choreSwapRepository.saveAll(pending);
    }

    /** ISO-8601 week key such as {@code 2026-W48}; uses the week-based year, not the calendar year. */
    static String isoWeekKey(LocalDate date) {
        return String.format("%d-W%02d",
                date.get(IsoFields.WEEK_BASED_YEAR),
                date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    // ------------------------------------------------------------------ rotation and assignee rules

    /**
     * The saved order, kept only for people who still live in the house, followed by residents missing
     * from it (newcomers) in join order. Legacy rows with no saved order use the join order.
     */
    static List<String> effectiveOrder(Chore chore, HouseMembers members) {
        LinkedHashSet<String> order = new LinkedHashSet<>();
        for (String id : chore.rotationOrderIds()) {
            if (members.contains(id)) {
                order.add(id);
            }
        }
        order.addAll(members.orderedIds());
        return new ArrayList<>(order);
    }

    static String effectiveAssignee(Chore chore, HouseMembers members) {
        return effectiveAssignee(chore, members, effectiveOrder(chore, members));
    }

    private static String effectiveAssignee(Chore chore, HouseMembers members, List<String> order) {
        String temporary = chore.getTemporaryAssigneeId();
        if (temporary != null && members.contains(temporary)) {
            return temporary;
        }
        String owner = chore.getCurrentAssigneeId();
        if (owner != null && members.contains(owner)) {
            return owner;
        }
        return order.isEmpty() ? owner : order.get(0);
    }

    static String nextAssignee(Chore chore, List<String> order) {
        if (chore.getChoreType() != ChoreType.ROTATIONAL || order.isEmpty()) {
            return chore.getCurrentAssigneeId();
        }
        int index = order.indexOf(chore.getCurrentAssigneeId());
        return index < 0 ? order.get(0) : order.get((index + 1) % order.size());
    }

    private static boolean hasActiveTemporaryAssignee(Chore chore, HouseMembers members) {
        return chore.getTemporaryAssigneeId() != null && members.contains(chore.getTemporaryAssigneeId());
    }

    private static void requireExactMembers(List<String> order, HouseMembers members) {
        if (order == null
                || order.size() != members.size()
                || order.stream().anyMatch(Objects::isNull)
                || new HashSet<>(order).size() != order.size()
                || !members.ids().equals(new HashSet<>(order))) {
            throw new BusinessRuleException(INVALID_ROTATION);
        }
    }

    // ------------------------------------------------------------------ helpers

    private Chore requireChoreInHouse(String choreId, String houseId) {
        Chore chore = choreRepository.findById(choreId)
                .orElseThrow(() -> new ResourceNotFoundException("Chore not found: " + choreId));
        if (!houseId.equals(chore.getHouseId())) {
            throw new ResourceNotFoundException("Chore not found in this house");
        }
        return chore;
    }

    private HouseMembers currentMembers(String houseId) {
        List<User> sorted = new ArrayList<>(userRepository.findByHouseIdOrderByIdAsc(houseId));
        sorted.sort(JOIN_ORDER);
        return new HouseMembers(sorted);
    }

    private List<ChoreSwap> pendingSwapsOfHouse(String houseId) {
        return choreSwapRepository.findByHouseIdAndStatusOrderByCreatedAtAsc(houseId, ChoreSwapStatus.PENDING);
    }

    private static int required(Chore chore) {
        ChoreFrequency frequency = chore.getFrequency() != null ? chore.getFrequency() : ChoreFrequency.ONCE;
        return frequency.requiredCompletions();
    }

    private static int completions(Chore chore) {
        return chore.getCompletionsThisCycle() != null ? chore.getCompletionsThisCycle() : 0;
    }

    private static int cycleNumber(Chore chore) {
        return chore.getCycleNumber() != null ? chore.getCycleNumber() : 1;
    }

    private static int weeksInCycle(Chore chore) {
        return chore.getWeeksInCycle() != null ? chore.getWeeksInCycle() : 0;
    }

    private List<ChoreResponse> toResponses(String houseId, List<Chore> chores, HouseMembers members) {
        List<ChoreSwap> swaps = chores.isEmpty() ? List.of() : pendingSwapsOfHouse(houseId);
        Set<String> referenced = new HashSet<>(swapUserIds(swaps));
        chores.forEach(c -> referenced.addAll(choreUserIds(c)));
        Map<String, String> names = namesFor(members, referenced);
        Map<String, Chore> byId = chores.stream().collect(Collectors.toMap(Chore::getId, c -> c, (a, b) -> a));
        return chores.stream()
                .map(c -> buildResponse(c, members, swaps, names, byId))
                .collect(Collectors.toList());
    }

    private ChoreResponse toResponse(Chore chore, HouseMembers members, List<ChoreSwap> houseSwaps) {
        Set<String> referenced = new HashSet<>(swapUserIds(houseSwaps));
        referenced.addAll(choreUserIds(chore));
        Map<String, String> names = namesFor(members, referenced);
        Map<String, Chore> byId = new HashMap<>();
        byId.put(chore.getId(), chore);
        for (ChoreSwap swap : houseSwaps) {
            for (String otherId : List.of(swap.getRequesterChoreId(), swap.getTargetChoreId())) {
                if (!byId.containsKey(otherId) && involves(swap, chore.getId())) {
                    choreRepository.findById(otherId).ifPresent(other -> byId.put(other.getId(), other));
                }
            }
        }
        return buildResponse(chore, members, houseSwaps, names, byId);
    }

    private ChoreResponse buildResponse(
            Chore chore,
            HouseMembers members,
            List<ChoreSwap> houseSwaps,
            Map<String, String> names,
            Map<String, Chore> choresById) {
        ChoreResponse response = ChoreResponse.fromEntity(chore);
        List<String> order = effectiveOrder(chore, members);
        String effective = effectiveAssignee(chore, members, order);
        String temporary = hasActiveTemporaryAssignee(chore, members) ? chore.getTemporaryAssigneeId() : null;
        String next = nextAssignee(chore, order);

        response.setRotationOrder(order);
        response.setCurrentAssigneeName(names.get(chore.getCurrentAssigneeId()));
        response.setEffectiveAssigneeId(effective);
        response.setEffectiveAssigneeName(names.get(effective));
        response.setTemporaryAssigneeId(temporary);
        response.setTemporaryAssigneeName(temporary == null ? null : names.get(temporary));
        response.setNextAssigneeId(next);
        response.setNextAssigneeName(names.get(next));
        response.setReopenedByName(chore.getReopenedById() == null ? null : names.get(chore.getReopenedById()));
        response.setPendingSwaps(houseSwaps.stream()
                .filter(s -> involves(s, chore.getId()))
                .map(s -> toSwapDto(s, choresById, names))
                .collect(Collectors.toList()));
        return response;
    }

    private static boolean involves(ChoreSwap swap, String choreId) {
        return choreId.equals(swap.getRequesterChoreId()) || choreId.equals(swap.getTargetChoreId());
    }

    private static ChoreSwapDto toSwapDto(ChoreSwap swap, Map<String, Chore> chores, Map<String, String> names) {
        Chore requesterChore = chores.get(swap.getRequesterChoreId());
        Chore targetChore = chores.get(swap.getTargetChoreId());
        return new ChoreSwapDto(
                swap.getId(),
                swap.getHouseId(),
                swap.getRequesterChoreId(),
                requesterChore == null ? null : requesterChore.getDescription(),
                swap.getTargetChoreId(),
                targetChore == null ? null : targetChore.getDescription(),
                swap.getRequesterId(),
                names.get(swap.getRequesterId()),
                swap.getTargetUserId(),
                names.get(swap.getTargetUserId()),
                swap.getStatus(),
                swap.getCycleNumber(),
                swap.getCreatedAt());
    }

    private static Set<String> swapUserIds(Collection<ChoreSwap> swaps) {
        Set<String> ids = new HashSet<>();
        for (ChoreSwap swap : swaps) {
            ids.add(swap.getRequesterId());
            ids.add(swap.getTargetUserId());
        }
        return ids;
    }

    private static Set<String> choreUserIds(Chore chore) {
        Set<String> ids = new HashSet<>();
        ids.add(chore.getCurrentAssigneeId());
        ids.add(chore.getTemporaryAssigneeId());
        ids.add(chore.getReopenedById());
        ids.remove(null);
        return ids;
    }

    /** Names of residents, plus former residents still referenced by a chore or swap. */
    private Map<String, String> namesFor(HouseMembers members, Collection<String> referencedIds) {
        Map<String, String> names = new HashMap<>(members.names());
        Set<String> missing = referencedIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> !names.containsKey(id))
                .collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            for (User user : userRepository.findAllById(missing)) {
                names.put(user.getId(), user.getName());
            }
        }
        return names;
    }

    /** Current residents of a house in join order. */
    record HouseMembers(List<User> users) {

        List<String> orderedIds() {
            return users.stream().map(User::getId).toList();
        }

        Set<String> ids() {
            return users.stream().map(User::getId).collect(Collectors.toSet());
        }

        boolean contains(String userId) {
            return userId != null && users.stream().anyMatch(u -> userId.equals(u.getId()));
        }

        int size() {
            return users.size();
        }

        Map<String, String> names() {
            Map<String, String> names = new HashMap<>();
            users.forEach(u -> names.put(u.getId(), u.getName()));
            return names;
        }
    }
}
