package com.nossoteto.service;

import com.nossoteto.dto.CreateHouseRequest;
import com.nossoteto.dto.HouseResponse;
import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.exception.ForbiddenOperationException;
import com.nossoteto.exception.ResourceNotFoundException;
import com.nossoteto.model.House;
import com.nossoteto.model.User;
import com.nossoteto.model.UserProfile;
import com.nossoteto.model.enums.VotingRule;
import com.nossoteto.repository.HouseRepository;
import com.nossoteto.repository.UserProfileRepository;
import com.nossoteto.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HouseServiceTest {

    @Mock
    private HouseRepository houseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private HouseAccessValidator houseAccessValidator;

    @Mock
    private CompatibilityCalculator compatibilityCalculator;

    private HouseService houseService;

    @BeforeEach
    void setUp() {
        houseService = new HouseService(
                houseRepository,
                userRepository,
                userProfileRepository,
                houseAccessValidator,
                compatibilityCalculator
        );
    }

    @Test
    void createHouseShouldSaveHouseAndAssignCreatorAsAdmin() {
        User creator = new User();
        creator.setId("user-1");
        creator.setName("Alice");
        creator.setEmail("alice@test.com");

        when(houseAccessValidator.requireAuthenticatedUser("user-1")).thenReturn(creator);
        when(houseRepository.save(any(House.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByHouseIdOrderByIdAsc(anyString())).thenReturn(List.of(creator));

        CreateHouseRequest request = new CreateHouseRequest();
        request.setName("República Solar");
        request.setAddress("Rua das Flores, 123");
        request.setDescription("Casa ampla");
        request.setBasicRules("Sem barulho após 22h");
        request.setRentAmount(new BigDecimal("1200.00"));
        request.setAllowsPets(true);
        request.setAllowsSmokers(false);
        request.setExpectedOrganizationLevel(4);
        request.setVisitorTolerance(3);
        request.setHasFurniture(true);
        request.setVotingRule(VotingRule.MAJORITY);

        HouseResponse response = houseService.createHouse("user-1", request);

        assertNotNull(response.getId());
        assertEquals("República Solar", response.getName());
        assertEquals("Rua das Flores, 123", response.getAddress());
        assertEquals(new BigDecimal("1200.00"), response.getRentAmount());
        assertEquals(VotingRule.MAJORITY, response.getVotingRule());
        assertTrue(response.getIsAcceptingMatches());
        assertEquals(1, response.getMembers().size());
        assertEquals("user-1", response.getMembers().get(0).getId());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals(response.getId(), userCaptor.getValue().getHouseId());
        assertTrue(userCaptor.getValue().getIsHouseAdmin());
    }

    @Test
    void createHouseShouldThrowBusinessRuleExceptionWhenNameIsBlank() {
        User creator = new User();
        creator.setId("user-1");
        when(houseAccessValidator.requireAuthenticatedUser("user-1")).thenReturn(creator);

        CreateHouseRequest request = new CreateHouseRequest();
        request.setName("  ");

        assertThrows(BusinessRuleException.class, () -> houseService.createHouse("user-1", request));
        verifyNoInteractions(houseRepository);
    }

    @Test
    void listOpenHousesShouldApplyFiltersAndCalculateCompatibilityScore() {
        House h1 = new House();
        h1.setId("house-1");
        h1.setName("Solar");
        h1.setRentAmount(new BigDecimal("1000.00"));
        h1.setAllowsPets(true);
        h1.setAllowsSmokers(false);
        h1.setIsAcceptingMatches(true);

        House h2 = new House();
        h2.setId("house-2");
        h2.setName("Caro");
        h2.setRentAmount(new BigDecimal("2500.00"));
        h2.setAllowsPets(true);
        h2.setAllowsSmokers(false);
        h2.setIsAcceptingMatches(true);

        House h3 = new House();
        h3.setId("house-3");
        h3.setName("Sem Pets");
        h3.setRentAmount(new BigDecimal("900.00"));
        h3.setAllowsPets(false);
        h3.setAllowsSmokers(false);
        h3.setIsAcceptingMatches(true);

        UserProfile profile = new UserProfile();
        profile.setUserId("user-1");

        when(houseRepository.findByIsAcceptingMatchesTrue()).thenReturn(List.of(h1, h2, h3));
        when(userProfileRepository.findById("user-1")).thenReturn(Optional.of(profile));
        when(compatibilityCalculator.calculateScore(profile, h1)).thenReturn(95);
        when(userRepository.findByHouseIdOrderByIdAsc("house-1")).thenReturn(List.of());

        List<HouseResponse> result = houseService.listOpenHouses(
                "user-1",
                new BigDecimal("1500.00"),
                true,
                false
        );

        assertEquals(1, result.size());
        assertEquals("house-1", result.get(0).getId());
        assertEquals(95, result.get(0).getCompatibilityScore());
    }

    @Test
    void getHouseDetailsShouldReturnHouseWithMembersAndScore() {
        House house = new House();
        house.setId("house-1");
        house.setName("República Solar");

        User member = new User();
        member.setId("user-2");
        member.setName("Bob");
        member.setEmail("bob@test.com");
        member.setIsHouseAdmin(true);

        UserProfile profile = new UserProfile();
        profile.setUserId("user-1");

        when(houseRepository.findById("house-1")).thenReturn(Optional.of(house));
        when(userProfileRepository.findById("user-1")).thenReturn(Optional.of(profile));
        when(compatibilityCalculator.calculateScore(profile, house)).thenReturn(85);
        when(userRepository.findByHouseIdOrderByIdAsc("house-1")).thenReturn(List.of(member));

        HouseResponse response = houseService.getHouseDetails("user-1", "house-1");

        assertEquals("house-1", response.getId());
        assertEquals(85, response.getCompatibilityScore());
        assertEquals(1, response.getMembers().size());
        assertEquals("Bob", response.getMembers().get(0).getName());
    }

    @Test
    void getHouseDetailsShouldThrowResourceNotFoundWhenHouseDoesNotExist() {
        when(houseRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> houseService.getHouseDetails("user-1", "missing"));
    }

    @Test
    void createHouseShouldRejectUserWhoAlreadyBelongsToAHouse() {
        User resident = new User();
        resident.setId("user-1");
        resident.setHouseId("house-A");
        resident.setIsHouseAdmin(true);
        when(houseAccessValidator.requireAuthenticatedUser("user-1")).thenReturn(resident);

        CreateHouseRequest request = new CreateHouseRequest();
        request.setName("Outra Casa");

        assertThrows(BusinessRuleException.class, () -> houseService.createHouse("user-1", request));
        assertEquals("house-A", resident.getHouseId());
        verifyNoInteractions(houseRepository);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createHouseShouldRejectScaleOutsideOneToFive() {
        User creator = new User();
        creator.setId("user-1");
        when(houseAccessValidator.requireAuthenticatedUser("user-1")).thenReturn(creator);

        CreateHouseRequest request = new CreateHouseRequest();
        request.setName("Casa");
        request.setVisitorTolerance(9);

        assertThrows(BusinessRuleException.class, () -> houseService.createHouse("user-1", request));
        verifyNoInteractions(houseRepository);
    }

    private User resident(String id, String houseId, String email) {
        User user = new User();
        user.setId(id);
        user.setHouseId(houseId);
        user.setEmail(email);
        return user;
    }

    @Test
    void getHouseDetailsShouldHideResidentEmailsFromNonResidents() {
        House house = new House();
        house.setId("house-1");

        when(houseRepository.findById("house-1")).thenReturn(Optional.of(house));
        when(userRepository.findById("visitor")).thenReturn(Optional.of(resident("visitor", null, "v@test.com")));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1"))
                .thenReturn(List.of(resident("user-2", "house-1", "bob@test.com")));

        HouseResponse response = houseService.getHouseDetails("visitor", "house-1");

        assertEquals("user-2", response.getMembers().get(0).getId());
        assertNull(response.getMembers().get(0).getEmail());
    }

    @Test
    void getHouseDetailsShouldShowResidentEmailsToResidents() {
        House house = new House();
        house.setId("house-1");

        when(houseRepository.findById("house-1")).thenReturn(Optional.of(house));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(resident("user-1", "house-1", "a@test.com")));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1"))
                .thenReturn(List.of(resident("user-2", "house-1", "bob@test.com")));

        HouseResponse response = houseService.getHouseDetails("user-1", "house-1");

        assertEquals("bob@test.com", response.getMembers().get(0).getEmail());
    }

    @Test
    void listOpenHousesShouldHideResidentEmailsFromCandidates() {
        House house = new House();
        house.setId("house-1");
        house.setIsAcceptingMatches(true);

        when(houseRepository.findByIsAcceptingMatchesTrue()).thenReturn(List.of(house));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1"))
                .thenReturn(List.of(resident("user-2", "house-1", "bob@test.com")));

        List<HouseResponse> result = houseService.listOpenHouses("candidate", null, null, null);

        assertNull(result.get(0).getMembers().get(0).getEmail());
    }

    @Test
    void updateHouseShouldApplyOnlyProvidedFieldsWhenCalledByAdmin() {
        House house = new House();
        house.setId("house-1");
        house.setName("Solar");
        house.setRentAmount(new BigDecimal("1000.00"));
        house.setVotingRule(VotingRule.UNANIMITY);
        house.setIsAcceptingMatches(true);

        when(houseRepository.findById("house-1")).thenReturn(Optional.of(house));
        when(houseRepository.save(any(House.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1")).thenReturn(List.of());

        CreateHouseRequest request = new CreateHouseRequest();
        request.setIsAcceptingMatches(false);
        request.setVotingRule(VotingRule.MAJORITY);

        HouseResponse response = houseService.updateHouse("admin-1", "house-1", request);

        verify(houseAccessValidator).requireHouseAdmin("admin-1", "house-1");
        assertFalse(response.getIsAcceptingMatches());
        assertEquals(VotingRule.MAJORITY, response.getVotingRule());
        assertEquals("Solar", response.getName());
        assertEquals(new BigDecimal("1000.00"), response.getRentAmount());
    }

    @Test
    void updateHouseShouldBeForbiddenForNonAdmin() {
        doThrow(new ForbiddenOperationException("User is not an administrator of this house"))
                .when(houseAccessValidator).requireHouseAdmin("user-2", "house-1");

        CreateHouseRequest request = new CreateHouseRequest();
        request.setIsAcceptingMatches(false);

        assertThrows(ForbiddenOperationException.class,
                () -> houseService.updateHouse("user-2", "house-1", request));
        verifyNoInteractions(houseRepository);
    }
}
