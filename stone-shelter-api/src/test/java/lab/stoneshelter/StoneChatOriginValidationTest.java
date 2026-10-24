package lab.stoneshelter;

import lab.stoneshelter.exceptions.StoneChatOriginRejectedException;
import lab.stoneshelter.services.StoneChatOriginService;
import lab.stoneshelter.services.StoneChatSettings;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoneChatOriginValidationTest {
    @Test
    void onlyExactConfiguredOriginIsAccepted() {
        StoneChatOriginService service = new StoneChatOriginService(new StoneChatSettings(
                new MockEnvironment().withProperty("CHAT_ALLOWED_ORIGINS", "https://stones.example")));
        service.validate("https://stones.example");
        for (String origin : new String[]{null, "null", "https://foreign.example", "https://stones.example.evil", "http://stones.example"})
            assertThatThrownBy(() -> service.validate(origin)).isInstanceOf(StoneChatOriginRejectedException.class);
    }
}
