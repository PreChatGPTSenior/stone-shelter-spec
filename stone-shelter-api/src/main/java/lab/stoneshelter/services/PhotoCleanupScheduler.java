package lab.stoneshelter.services;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
public class PhotoCleanupScheduler {
    private final PhotoCleanupJobService job;
    public PhotoCleanupScheduler(PhotoCleanupJobService job) { this.job = job; }

    @Scheduled(cron = "${stone.photos.cleanup-cron}", zone = "${stone.photos.cleanup-zone}")
    public void cleanDaily() {
        job.run();
    }
}
