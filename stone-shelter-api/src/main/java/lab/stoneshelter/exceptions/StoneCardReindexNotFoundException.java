package lab.stoneshelter.exceptions;

import java.util.UUID;

public class StoneCardReindexNotFoundException extends RuntimeException {
    public StoneCardReindexNotFoundException(UUID jobId) { super("Reindex job " + jobId + " does not exist."); }
}
