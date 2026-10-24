package lab.stoneshelter.repositories;

import lab.stoneshelter.entities.StoneEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import lab.stoneshelter.criteria.StoneSearchCriteria;
import lab.stoneshelter.enums.AdoptionStatus;
import lab.stoneshelter.enums.StoneSize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StoneEntityRepository extends JpaRepository<StoneEntity, Long>,
        JpaSpecificationExecutor<StoneEntity> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StoneEntity s where s.id = :id")
    Optional<StoneEntity> findByIdForUpdate(@Param("id") long id);

    @Query("select s.id from StoneEntity s order by s.id")
    List<Long> findIds();

    @Query("select s.id from StoneEntity s where s.id in :ids")
    List<Long> findExistingIds(@Param("ids") List<Long> ids);

    default Page<StoneEntity> search(StoneSearchCriteria search) {
        Specification<StoneEntity> filter = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("adoptionStatus"), AdoptionStatus.AVAILABLE));
            if (search.getStoneType() != null) {
                predicates.add(cb.equal(root.get("stoneType"), search.getStoneType()));
            }
            if (search.getStoneSize() != null) {
                predicates.add(cb.equal(root.get("stoneSize"), search.getStoneSize()));
            }
            if (search.getAdoptionStatus() != null) {
                predicates.add(cb.equal(root.get("adoptionStatus"), search.getAdoptionStatus()));
            }
            if (search.getStoneSizes() != null && !search.getStoneSizes().isEmpty()) {
                predicates.add(root.get("stoneSize").in(search.getStoneSizes()));
            }
            if (search.getStoneTypes() != null && !search.getStoneTypes().isEmpty()) {
                predicates.add(root.get("stoneType").in(search.getStoneTypes()));
            }
            if (search.getAdmissionDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("admissionDate"), search.getAdmissionDateFrom()));
            }
            if (search.getAdmissionDateToExclusive() != null) {
                predicates.add(cb.lessThan(root.get("admissionDate"), search.getAdmissionDateToExclusive()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        var pageable = PageRequest.of(search.getPage(), search.getSize());
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            return new PageImpl<>(List.of(), pageable, count(filter));
        }
        Specification<StoneEntity> ordered = (root, query, cb) -> {
            Expression<?> sort = switch (search.getField()) {
                case NAME -> root.get("name");
                case ADMISSION_DATE -> root.get("admissionDate");
                case STONE_SIZE -> cb.<Integer>selectCase()
                        .when(cb.equal(root.get("stoneSize"), StoneSize.SMALL), 0)
                        .when(cb.equal(root.get("stoneSize"), StoneSize.MEDIUM), 1).otherwise(2);
            };
            if (query != null) {
                query.orderBy(search.isDescending() ? cb.desc(sort) : cb.asc(sort), cb.asc(root.get("id")));
            }
            return filter.toPredicate(root, query, cb);
        };
        return findAll(ordered, filter, pageable);
    }
}
