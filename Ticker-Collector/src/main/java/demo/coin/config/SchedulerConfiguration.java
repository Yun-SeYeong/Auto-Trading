package demo.coin.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration
public class SchedulerConfiguration implements SchedulingConfigurer {

    private final int POOL_SIZE = 20;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        ThreadPoolTaskScheduler tpts = new ThreadPoolTaskScheduler();
        tpts.setPoolSize(POOL_SIZE);
        tpts.setThreadNamePrefix("coin-scheduler-pool-");
        tpts.initialize();

        taskRegistrar.setTaskScheduler(tpts);
    }
}
