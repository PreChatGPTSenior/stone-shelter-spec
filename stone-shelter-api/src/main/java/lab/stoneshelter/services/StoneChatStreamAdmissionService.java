package lab.stoneshelter.services;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import lab.stoneshelter.exceptions.StoneChatStreamCapacityExceededException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class StoneChatStreamAdmissionService {
    private final Semaphore slots;
    public StoneChatStreamAdmissionService(StoneChatStreamSettings settings) { slots = new Semaphore(settings.maximumConcurrent()); }
    public Lease acquire() {
        if (!slots.tryAcquire()) throw new StoneChatStreamCapacityExceededException();
        return new Lease(slots);
    }
    public static final class Lease implements AutoCloseable {
        private final Semaphore slots;
        private final AtomicBoolean exited = new AtomicBoolean();
        private Lease(Semaphore slots) { this.slots = slots; }
        /** Called only after actual transport/writer exit, never by mere timeout notification. */
        @Override public void close() { if (exited.compareAndSet(false, true)) slots.release(); }
    }
}
