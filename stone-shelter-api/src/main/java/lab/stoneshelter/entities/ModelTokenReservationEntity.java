package lab.stoneshelter.entities;

import java.time.LocalDate;
import java.util.UUID;

public record ModelTokenReservationEntity(UUID id, LocalDate budgetDay, long reservedTokens, Long actualTokens) {}
