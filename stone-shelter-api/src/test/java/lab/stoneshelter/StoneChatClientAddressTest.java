package lab.stoneshelter;

import lab.stoneshelter.exceptions.StoneChatUnavailableException;
import lab.stoneshelter.services.StoneChatClientAddressService;
import lab.stoneshelter.services.StoneChatSettings;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoneChatClientAddressTest {
    @Test
    void directSpoofingIsIgnoredAndOnlyExplicitTrustedPeerCanForward() {
        MockEnvironment environment = new MockEnvironment().withProperty("CHAT_TRUSTED_PROXIES", "127.0.0.1,::1");
        StoneChatClientAddressService service = new StoneChatClientAddressService(new StoneChatSettings(environment));
        assertThat(service.resolve("192.0.2.10", "198.51.100.12")).isEqualTo("192.0.2.10");
        assertThat(service.resolve("::ffff:127.0.0.1", "::ffff:198.51.100.12")).isEqualTo("198.51.100.12");
        assertThat(service.resolve("127.0.0.1", "198.51.100.12")).isEqualTo("198.51.100.12");
        assertThat(service.resolve("0:0:0:0:0:0:0:1", "2001:db8::1")).isEqualTo("2001:db8:0:0:0:0:0:1");
        assertThatThrownBy(() -> service.resolve("127.0.0.1", "198.51.100.1,192.0.2.1"))
                .isInstanceOf(StoneChatUnavailableException.class);
        assertThatThrownBy(() -> service.canonical("localhost")).isInstanceOf(StoneChatUnavailableException.class);
        assertThatThrownBy(() -> service.canonical("999.1.1.1")).isInstanceOf(StoneChatUnavailableException.class);
    }
}
