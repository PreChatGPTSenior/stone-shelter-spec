package lab.stoneshelter.services;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PhotoClockConfiguration {
    @Bean
    public Clock photoClock() { return Clock.systemUTC(); }
}
