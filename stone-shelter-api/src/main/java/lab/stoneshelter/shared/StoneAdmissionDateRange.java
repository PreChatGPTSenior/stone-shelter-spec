package lab.stoneshelter.shared;

import java.time.Instant;

public record StoneAdmissionDateRange(Instant startInclusive, Instant endExclusive) {}
