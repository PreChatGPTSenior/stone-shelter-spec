package lab.stoneshelter.services;

import java.time.Instant;
import lab.stoneshelter.entities.StoneEntity;
import lab.stoneshelter.entities.StoneReservationEntity;
import lab.stoneshelter.enums.AdoptionStatus;
import lab.stoneshelter.exceptions.StoneNotFoundException;
import lab.stoneshelter.exceptions.StoneReservationConflictException;
import lab.stoneshelter.mappers.dtos.StoneReservationEntityToStoneReservationCreateResponseMapper;
import lab.stoneshelter.mappers.entities.StoneReservationCreateRequestToStoneReservationEntityMapper;
import lab.stoneshelter.repositories.StoneEntityRepository;
import lab.stoneshelter.repositories.StoneReservationEntityRepository;
import lab.stoneshelter.shared.StoneReservationCreateRequest;
import lab.stoneshelter.shared.StoneReservationCreateResponse;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StoneReservationService {
    private final StoneEntityRepository stoneEntityRepository;
    private final StoneReservationEntityRepository stoneReservationEntityRepository;
    private final StoneReservationCreateRequestToStoneReservationEntityMapper requestMapper;
    private final StoneReservationEntityToStoneReservationCreateResponseMapper responseMapper;

    public StoneReservationService(StoneEntityRepository stoneEntityRepository,
            StoneReservationEntityRepository stoneReservationEntityRepository,
            StoneReservationCreateRequestToStoneReservationEntityMapper requestMapper,
            StoneReservationEntityToStoneReservationCreateResponseMapper responseMapper) {
        this.stoneEntityRepository = stoneEntityRepository;
        this.stoneReservationEntityRepository = stoneReservationEntityRepository;
        this.requestMapper = requestMapper;
        this.responseMapper = responseMapper;
    }

    public StoneReservationCreateResponse create(long id, StoneReservationCreateRequest request) {
        StoneEntity stoneEntity = findById(id);
        if (stoneEntity.getAdoptionStatus() != AdoptionStatus.AVAILABLE
                || stoneReservationEntityRepository.existsByStoneId(id)) {
            throw new StoneReservationConflictException(id);
        }
        stoneEntity.setAdoptionStatus(AdoptionStatus.RESERVED);
        StoneReservationEntity stoneReservationEntity = requestMapper.toEntity(request);
        stoneReservationEntity.setStone(stoneEntity);
        stoneReservationEntity.setCreatedAt(Instant.now());
        try {
            return responseMapper.toDto(stoneReservationEntityRepository.saveAndFlush(stoneReservationEntity));
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException constraintViolationException
                        && "uk_stone_reservation_stone".equals(constraintViolationException.getConstraintName())) {
                    throw new StoneReservationConflictException(id);
                }
            }
            throw exception;
        }
    }

    private StoneEntity findById(long id) {
        return stoneEntityRepository.findByIdForUpdate(id).orElseThrow(() -> new StoneNotFoundException(id));
    }
}
