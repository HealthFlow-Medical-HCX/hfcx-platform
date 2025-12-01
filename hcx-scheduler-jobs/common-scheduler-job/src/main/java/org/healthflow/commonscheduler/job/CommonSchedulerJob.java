package org.healthflow.commonscheduler.job;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages={"org.healthflow.commonscheduler"})
@EnableScheduling
public class CommonSchedulerJob {

    public static void main(String[] args) {
        SpringApplication.run(CommonSchedulerJob.class, args);
    }

}