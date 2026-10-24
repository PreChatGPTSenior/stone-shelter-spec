package lab.stoneshelter.services;

import java.time.ZoneOffset;
import java.time.Clock;
import java.time.Instant;
import lab.stoneshelter.repositories.StonePhotoDraftEntityRepository;
import lab.stoneshelter.repositories.StonePhotoEntityRepository;
import lab.stoneshelter.exceptions.InvalidAdmissionDateRangeException;
import lab.stoneshelter.enums.StoneSortField;
import lab.stoneshelter.criteria.StoneSearchCriteria;
import lab.stoneshelter.exceptions.StoneNotFoundException;
import lab.stoneshelter.repositories.StoneEntityRepository;

import lab.stoneshelter.shared.StoneCreateRequest;
import lab.stoneshelter.shared.StoneCreateResponse;
import lab.stoneshelter.shared.StoneDeleteResponse;
import lab.stoneshelter.shared.StoneResponse;
import lab.stoneshelter.shared.StoneUpdateRequest;
import lab.stoneshelter.shared.StoneUpdateResponse;
import lab.stoneshelter.shared.StonesSearchRequest;
import lab.stoneshelter.shared.StonesSearchResponse;
import lab.stoneshelter.entities.StoneEntity;
import lab.stoneshelter.mappers.entities.StoneUpdateRequestToStoneEntityMapper;
import lab.stoneshelter.mappers.dtos.StoneEntityToStoneResponseMapper;
import lab.stoneshelter.mappers.dtos.StoneEntityToStoneUpdateResponseMapper;
import lab.stoneshelter.mappers.dtos.PageToStonesSearchResponseMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
@Transactional
public class StoneService {
    private final StoneEntityRepository repository;
    private final StoneIndexEventService stoneIndexEventService;
    private final StonePhotoEntityRepository photoRepository;
    private final PhotoCleanupService cleanup;
    private final StoneCreationService creationService;
    private final StonePhotoTransferService transferService;
    private final StonePhotoDraftEntityRepository draftRepository;
    private final Clock clock;
    private final StoneUpdateRequestToStoneEntityMapper updateRequestMapper;
    private final StoneEntityToStoneResponseMapper responseMapper;
    private final StoneEntityToStoneUpdateResponseMapper updateResponseMapper;
    private final PageToStonesSearchResponseMapper searchResponseMapper;

    public StoneService(StoneEntityRepository repository,
            StonePhotoEntityRepository photoRepository, PhotoCleanupService cleanup,
            StoneCreationService creationService, StonePhotoTransferService transferService,
            StonePhotoDraftEntityRepository draftRepository, Clock clock,
            StoneUpdateRequestToStoneEntityMapper updateRequestMapper,
            StoneEntityToStoneResponseMapper responseMapper,
            StoneEntityToStoneUpdateResponseMapper updateResponseMapper,
            PageToStonesSearchResponseMapper searchResponseMapper, StoneIndexEventService stoneIndexEventService) {
        this.repository = repository;
        this.stoneIndexEventService = stoneIndexEventService;
        this.photoRepository = photoRepository;
        this.cleanup = cleanup;
        this.creationService = creationService;
        this.transferService = transferService;
        this.draftRepository = draftRepository;
        this.clock = clock;
        this.updateRequestMapper = updateRequestMapper;
        this.responseMapper = responseMapper;
        this.updateResponseMapper = updateResponseMapper;
        this.searchResponseMapper = searchResponseMapper;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public StoneCreateResponse create(StoneCreateRequest request) {
        long stoneId = creationService.create(request);
        transferService.copyAll(stoneId);
        return creationService.findById(stoneId);
    }

    @Transactional(readOnly = true)
    public StoneResponse get(long id) {
        return responseMapper.toDto(findById(id));
    }

    public StoneUpdateResponse update(long id, StoneUpdateRequest request) {
        StoneEntity stoneEntity = repository.findByIdForUpdate(id).orElseThrow(() -> new StoneNotFoundException(id));
        var previousSnapshot = stoneIndexEventService.snapshot(stoneEntity);
        return updateResponseMapper.toDto(stoneIndexEventService.publishUpdateIfChanged(
                updateRequestMapper.toEntity(stoneEntity, request), previousSnapshot));
    }

    public StoneDeleteResponse delete(long id) {
        var stoneEntity = repository.findByIdForUpdate(id).orElseThrow(() -> new StoneNotFoundException(id));
        var stonePhotoDraftEntities = draftRepository.findByStoneIdForUpdate(id);
        stonePhotoDraftEntities.forEach(stonePhotoDraftEntity -> cleanup.enqueueDraft(stonePhotoDraftEntity.getObjectKey(), Instant.now(clock)));
        var objectKeys = photoRepository.findByStoneIdOrderByPositionAsc(id).stream().map(photo -> photo.getObjectKey()).toList();
        cleanup.enqueueDeletedPhotos(objectKeys);
        photoRepository.deleteAll(stoneEntity.getPhotos());
        photoRepository.flush();
        draftRepository.deleteAll(stonePhotoDraftEntities);
        draftRepository.flush();
        repository.delete(stoneIndexEventService.publishDelete(stoneEntity));
        return new StoneDeleteResponse(id);
    }

    @Transactional(readOnly = true)
    public StonesSearchResponse search(StonesSearchRequest request) {
        return searchResponseMapper.toDto(repository.search(prepareSearchCriteria(request)));
    }

    private StoneSearchCriteria prepareSearchCriteria(StonesSearchRequest request) {
        var filter = request.filter();
        if (filter != null && filter.getAdmissionDateFrom() != null && filter.getAdmissionDateTo() != null
                && filter.getAdmissionDateFrom().isAfter(filter.getAdmissionDateTo())) {
            throw new InvalidAdmissionDateRangeException();
        }
        var sort = request.sort();
        var field = sort == null || sort.field() == null
                ? StoneSortField.ADMISSION_DATE
                : StoneSortField.fromValue(sort.field());

        var stoneSearchCriteria = new StoneSearchCriteria();
        stoneSearchCriteria.setStoneType(filter == null ? null : filter.getStoneType());
        stoneSearchCriteria.setStoneSize(filter == null ? null : filter.getStoneSize());
        stoneSearchCriteria.setAdoptionStatus(filter == null ? null : filter.getAdoptionStatus());
        stoneSearchCriteria.setStoneSizes(filter == null ? null : filter.getStoneSizes());
        stoneSearchCriteria.setStoneTypes(filter == null ? null : filter.getStoneTypes());
        stoneSearchCriteria.setAdmissionDateFrom(filter == null || filter.getAdmissionDateFrom() == null ? null
                : filter.getAdmissionDateFrom().atStartOfDay().toInstant(ZoneOffset.UTC));
        stoneSearchCriteria.setAdmissionDateToExclusive(filter == null || filter.getAdmissionDateTo() == null ? null
                : filter.getAdmissionDateTo().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
        stoneSearchCriteria.setPage(request.page() == null ? 0 : request.page());
        stoneSearchCriteria.setSize(request.size() == null ? 12 : request.size());
        stoneSearchCriteria.setField(field);
        stoneSearchCriteria.setDescending(sort == null || sort.direction() == null
                ? field == StoneSortField.ADMISSION_DATE
                : "desc".equals(sort.direction()));
        return stoneSearchCriteria;
    }

    private StoneEntity findById(long id) {
        return repository.findById(id).orElseThrow(() -> new StoneNotFoundException(id));
    }

}
