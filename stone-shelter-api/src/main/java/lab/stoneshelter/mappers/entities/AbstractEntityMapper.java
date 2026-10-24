package lab.stoneshelter.mappers.entities;

public abstract class AbstractEntityMapper<S, T> {
    public final T toEntity(S source) {
        if (source == null) {
            return null;
        }
        return populateEntity(source, newEntity());
    }

    public final T toEntity(T entity, S source) {
        if (source == null) {
            return null;
        }
        if (entity == null) {
            throw new IllegalArgumentException("Target entity must not be null.");
        }
        return populateEntity(source, entity);
    }

    protected abstract T newEntity();

    protected abstract T populateEntity(S source, T entity);
}
