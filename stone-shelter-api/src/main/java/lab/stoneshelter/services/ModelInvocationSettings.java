package lab.stoneshelter.services;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ModelInvocationSettings {
    private final int concurrentCalls;
    private final long dailyBudget;
    public ModelInvocationSettings(Environment environment) {
        concurrentCalls = environment.getProperty("MODEL_MAX_CONCURRENT_CALLS", Integer.class, 1);
        dailyBudget = environment.getProperty("MODEL_DAILY_TOKEN_BUDGET", Long.class, 100000L);
        if (concurrentCalls < 1 || dailyBudget < 1) throw new IllegalArgumentException("Invalid shared model limits");
    }
    public int concurrentCalls() { return concurrentCalls; }
    public long dailyBudget() { return dailyBudget; }
}
