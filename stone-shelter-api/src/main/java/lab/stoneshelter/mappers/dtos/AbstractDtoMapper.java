package lab.stoneshelter.mappers.dtos;

import lab.stoneshelter.exceptions.MapperValidationException;

public abstract class AbstractDtoMapper<S, T> {
    public final T toDto(S source) {
        validateSource(source);
        return mapToDto(source);
    }

    protected final void validateSource(S source) {
        if (source == null) {
            throw new MapperValidationException("Cannot map a null entity to a DTO.");
        }
    }

    protected abstract T mapToDto(S source);
}
