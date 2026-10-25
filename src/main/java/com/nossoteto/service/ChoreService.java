package com.nossoteto.service;

import com.nossoteto.dto.ChoreResponse;
import com.nossoteto.dto.CreateChoreRequest;
import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.exception.ResourceNotFoundException;
import com.nossoteto.model.Chore;
import com.nossoteto.model.House;
import com.nossoteto.model.User;
import com.nossoteto.model.enums.ChoreStatus;
import com.nossoteto.model.enums.ChoreType;
import com.nossoteto.repository.ChoreRepository;
import com.nossoteto.repository.HouseRepository;
import com.nossoteto.repository.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ChoreService {

    private final ChoreRepository choreRepository;
    private final UserRepository userRepository;
    private final HouseRepository houseRepository;
    private final HouseAccessValidator houseAccessValidator;

    public ChoreService(
            ChoreRepository choreRepository,
            UserRepository userRepository,
            HouseRepository houseRepository,
            HouseAccessValidator houseAccessValidator) {
        this.choreRepository = choreRepository;
        this.userRepository = userRepository;
        this.houseRepository = houseRepository;
        this.houseAccessValidator = houseAccessValidator;
    }

    @Transactional
    public ChoreResponse createChore(String userId, String houseId, CreateChoreRequest request) {
        houseAccessValidator.requireHouseMember(userId, houseId);

        if (request == null || request.getDescription() == null || request.getDescription().isBlank()) {
            throw new BusinessRuleException("Chore description is required");
        }

        ChoreType choreType = ChoreType.fromString(request.getChoreType());

        String targetAssigneeId = request.getAssigneeId();
        if (targetAssigneeId == null || targetAssigneeId.isBlank()) {
            targetAssigneeId = userId;
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
        chore.setLastResetDate(LocalDate.now());

        Chore saved = choreRepository.save(chore);
        return ChoreResponse.fromEntity(saved);
    }

    public List<ChoreResponse> listChores(String userId, String houseId) {
        houseAccessValidator.requireHouseMember(userId, houseId);
        return choreRepository.findByHouseId(houseId).stream()
                .map(ChoreResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public ChoreResponse completeChore(String userId, String houseId, String choreId) {
        houseAccessValidator.requireHouseMember(userId, houseId);

        Chore chore = choreRepository.findById(choreId)
                .orElseThrow(() -> new ResourceNotFoundException("Chore not found: " + choreId));
        if (!houseId.equals(chore.getHouseId())) {
            throw new ResourceNotFoundException("Chore not found in this house");
        }

        chore.setStatus(ChoreStatus.COMPLETED);
        Chore saved = choreRepository.save(chore);
        return ChoreResponse.fromEntity(saved);
    }

    // Closing a cycle marks everyone's unfinished chores as DELAYED, so only the admin may force it.
    @Transactional
    public List<ChoreResponse> rotateHouseChores(String userId, String houseId) {
        houseAccessValidator.requireHouseAdmin(userId, houseId);
        return processHouseWeeklyCycle(houseId);
    }

    @Scheduled(cron = "0 0 0 * * SUN")
    @Transactional
    public void processWeeklyCycleForAllHouses() {
        List<House> houses = houseRepository.findAll();
        for (House house : houses) {
            processHouseWeeklyCycle(house.getId());
        }
    }

    @Transactional
    public List<ChoreResponse> processHouseWeeklyCycle(String houseId) {
        List<User> members = userRepository.findByHouseIdOrderByIdAsc(houseId);
        List<Chore> chores = choreRepository.findByHouseId(houseId);

        for (Chore chore : chores) {
            // An unfinished chore (PENDING or already DELAYED) stays DELAYED with the same resident until
            // they complete it, so the delay is not handed over to the next person in the rotation (RF14).
            if (chore.getStatus() != ChoreStatus.COMPLETED) {
                chore.setStatus(ChoreStatus.DELAYED);
                chore.setLastResetDate(LocalDate.now());
                continue;
            }

            chore.setStatus(ChoreStatus.PENDING);
            if (chore.getChoreType() == ChoreType.ROTATIONAL && !members.isEmpty()) {
                int currentIdx = -1;
                for (int i = 0; i < members.size(); i++) {
                    if (members.get(i).getId().equals(chore.getCurrentAssigneeId())) {
                        currentIdx = i;
                        break;
                    }
                }
                int nextIdx = (currentIdx == -1) ? 0 : (currentIdx + 1) % members.size();
                chore.setCurrentAssigneeId(members.get(nextIdx).getId());
            }

            chore.setLastResetDate(LocalDate.now());
        }

        List<Chore> saved = choreRepository.saveAll(chores);
        return saved.stream()
                .map(ChoreResponse::fromEntity)
                .collect(Collectors.toList());
    }
}
