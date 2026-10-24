package lab.stoneshelter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lab.stoneshelter.entities.StoneEntity;
import lab.stoneshelter.entities.StoneReservationEntity;
import lab.stoneshelter.enums.AdoptionStatus;
import lab.stoneshelter.enums.StoneSize;
import lab.stoneshelter.enums.StoneType;
import lab.stoneshelter.exceptions.MapperValidationException;
import lab.stoneshelter.exceptions.StoneNotFoundException;
import lab.stoneshelter.exceptions.StoneReservationConflictException;
import lab.stoneshelter.mappers.dtos.StoneReservationEntityToStoneReservationCreateResponseMapper;
import lab.stoneshelter.mappers.entities.StoneReservationCreateRequestToStoneReservationEntityMapper;
import lab.stoneshelter.repositories.StoneEntityRepository;
import lab.stoneshelter.repositories.StoneReservationEntityRepository;
import lab.stoneshelter.services.StoneReservationService;
import lab.stoneshelter.shared.StoneReservationCreateRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest
class StoneReservationServiceTest extends IntegrationTest {
    @Autowired private StoneReservationService stoneReservationService;
    @Autowired private StoneEntityRepository stoneEntityRepository;
    @Autowired private StoneReservationEntityRepository stoneReservationEntityRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoSpyBean private StoneReservationEntityToStoneReservationCreateResponseMapper responseMapper;
    private final List<Long> stoneIds = new ArrayList<>();
    private final StoneReservationCreateRequest request = new StoneReservationCreateRequest("  Visitor 石  ", "find me by the window");

    @AfterEach
    void cleanUp() {
        reset(responseMapper);
        for (long id : stoneIds) jdbcTemplate.update("delete from stone where id = ?", id);
    }

    @Test
    void createsReservationAndStatusTogetherAndReloadsFromDatabase() {
        long id = createStone(AdoptionStatus.AVAILABLE);
        var response = stoneReservationService.create(id, request);
        assertThat(response.id()).isPositive();
        assertThat(response.stoneId()).isEqualTo(id);
        assertThat(response.adoptionStatus()).isEqualTo(AdoptionStatus.RESERVED);
        assertThat(response.createdAt()).isBeforeOrEqualTo(Instant.now());
        assertThat(stoneEntityRepository.findById(id).orElseThrow().getAdoptionStatus()).isEqualTo(AdoptionStatus.RESERVED);
        var stoneReservationEntity = stoneReservationEntityRepository.findById(response.id()).orElseThrow();
        assertThat(stoneReservationEntity.getApplicantName()).isEqualTo(request.applicantName());
        assertThat(stoneReservationEntity.getContactDetails()).isEqualTo(request.contactDetails());
        assertThat(stoneReservationEntity.getCreatedAt().truncatedTo(ChronoUnit.MILLIS)).isEqualTo(response.createdAt().truncatedTo(ChronoUnit.MILLIS));
        assertThatThrownBy(() -> stoneReservationService.create(id, request)).isInstanceOf(StoneReservationConflictException.class);
    }

    @Test
    void rejectsUnavailableAndMissingStones() {
        for (var adoptionStatus : List.of(AdoptionStatus.RESERVED, AdoptionStatus.ADOPTED)) {
            long id = createStone(adoptionStatus);
            assertThatThrownBy(() -> stoneReservationService.create(id, request)).isInstanceOf(StoneReservationConflictException.class);
            assertThat(stoneReservationEntityRepository.existsByStoneId(id)).isFalse();
            assertThat(stoneEntityRepository.findById(id).orElseThrow().getAdoptionStatus()).isEqualTo(adoptionStatus);
        }
        assertThatThrownBy(() -> stoneReservationService.create(Long.MAX_VALUE, request)).isInstanceOf(StoneNotFoundException.class);
    }

    @Test
    void resettingStatusDoesNotPermitAnotherReservationAndApplicantsAreNotUnique() {
        long firstId = createStone(AdoptionStatus.AVAILABLE);
        long secondId = createStone(AdoptionStatus.AVAILABLE);
        var first = stoneReservationService.create(firstId, request);
        assertThat(stoneReservationService.create(secondId, request).stoneId()).isEqualTo(secondId);
        jdbcTemplate.update("update stone set adoption_status = 'AVAILABLE' where id = ?", firstId);
        assertThatThrownBy(() -> stoneReservationService.create(firstId, new StoneReservationCreateRequest("Another", "other contacts")))
                .isInstanceOf(StoneReservationConflictException.class);
        assertThat(stoneReservationEntityRepository.findById(first.id()).orElseThrow().getApplicantName()).isEqualTo(request.applicantName());
    }

    @Test
    void concurrentAttemptsProduceExactlyOneReservation() throws Exception {
        long id = createStone(AdoptionStatus.AVAILABLE);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(6)) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int index = 0; index < 6; index++) {
                results.add(executor.submit(() -> {
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Reservation test start timed out");
                    try {
                        stoneReservationService.create(id, request);
                        return true;
                    } catch (StoneReservationConflictException exception) {
                        return false;
                    }
                }));
            }
            start.countDown();
            int successes = 0;
            for (var result : results) if (result.get(20, TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);
        }
        assertThat(jdbcTemplate.queryForObject("select count(*) from stone_reservation where stone_id = ?", Long.class, id)).isEqualTo(1);
        assertThat(stoneEntityRepository.findById(id).orElseThrow().getAdoptionStatus()).isEqualTo(AdoptionStatus.RESERVED);
    }

    @Test
    void rollsBackFlushedReservationAndStatusOnFailureThenAllowsRetry() {
        long id = createStone(AdoptionStatus.AVAILABLE);
        doThrow(new DataIntegrityViolationException("Injected failure after flush")).when(responseMapper).toDto(any(StoneReservationEntity.class));
        assertThatThrownBy(() -> stoneReservationService.create(id, request)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(stoneReservationEntityRepository.existsByStoneId(id)).isFalse();
        assertThat(stoneEntityRepository.findById(id).orElseThrow().getAdoptionStatus()).isEqualTo(AdoptionStatus.AVAILABLE);
        reset(responseMapper);
        assertThat(stoneReservationService.create(id, request).adoptionStatus()).isEqualTo(AdoptionStatus.RESERVED);
    }

    @Test
    void reservationMappersKeepSharedNullPolicies() {
        assertThat(new StoneReservationCreateRequestToStoneReservationEntityMapper().toEntity(null)).isNull();
        assertThatThrownBy(() -> new StoneReservationEntityToStoneReservationCreateResponseMapper().toDto(null))
                .isInstanceOf(MapperValidationException.class);
    }

    private long createStone(AdoptionStatus adoptionStatus) {
        var stoneEntity = new StoneEntity();
        stoneEntity.setName("Reservation service test");
        stoneEntity.setStoneType(StoneType.values()[0]);
        stoneEntity.setStoneSize(StoneSize.SMALL);
        stoneEntity.setAdoptionStatus(adoptionStatus);
        stoneEntity.setAdmissionDate(Instant.parse("2026-01-01T00:00:00Z"));
        stoneEntity = stoneEntityRepository.saveAndFlush(stoneEntity);
        stoneIds.add(stoneEntity.getId());
        return stoneEntity.getId();
    }
}
