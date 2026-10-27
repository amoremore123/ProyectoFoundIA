package com.proyectointegrador.config;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.slf4j.LoggerFactory;
import java.util.concurrent.Executor;

@Configuration @EnableScheduling @EnableAsync
public class CoincidenciasRetryConfig {
    @Bean("avisosCoincidenciaExecutor")
    public Executor avisosCoincidenciaExecutor(){
        var executor=new ThreadPoolTaskExecutor();executor.setCorePoolSize(2);executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);executor.setThreadNamePrefix("avisos-");
        executor.setRejectedExecutionHandler((t,p) -> LoggerFactory.getLogger(CoincidenciasRetryConfig.class)
                .warn("Cola de avisos ocupada; el evento durable permanece pendiente para reintento."));
        return executor;
    }
}
