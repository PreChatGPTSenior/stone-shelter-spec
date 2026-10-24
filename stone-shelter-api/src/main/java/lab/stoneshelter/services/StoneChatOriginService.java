package lab.stoneshelter.services;

import lab.stoneshelter.exceptions.StoneChatOriginRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StoneChatOriginService {
    private final StoneChatSettings settings;
    public StoneChatOriginService(StoneChatSettings settings) { this.settings = settings; }
    public void validate(String origin) {
        if (origin == null || "null".equals(origin) || !settings.origins().contains(origin))
            throw new StoneChatOriginRejectedException();
    }
}
