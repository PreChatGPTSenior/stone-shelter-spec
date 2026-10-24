package lab.stoneshelter.controllers;

import jakarta.validation.Valid;
import lab.stoneshelter.services.StoneReservationService;
import lab.stoneshelter.shared.StoneReservationCreateRequest;
import lab.stoneshelter.shared.StoneReservationCreateResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stones/{id}/reservations")
public class StoneReservationController {
    private final StoneReservationService stoneReservationService;

    public StoneReservationController(StoneReservationService stoneReservationService) {
        this.stoneReservationService = stoneReservationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StoneReservationCreateResponse create(@PathVariable("id") long id,
            @Valid @RequestBody StoneReservationCreateRequest request) {
        return stoneReservationService.create(id, request);
    }
}
