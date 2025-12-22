package com.marketplace.search.infrastructure.config;

import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuração para execução assíncrona e thread pools
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    
    private static final Logger logger = LoggerFactory.getLogger(AsyncConfig.class);
    
    /**
     * Executor padrão para tarefas assíncronas gerais
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("search-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
    
    /**
     * Executor específico para indexação de produtos
     */
    @Bean(name = "indexingExecutor")
    public Executor indexingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("indexing-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
    
    /**
     * Executor para operações assíncronas de indexação (usado pelos use cases)
     */
    @Bean(name = "asyncIndexingExecutor")
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // Configurações do pool de threads
        executor.setCorePoolSize(5);           // Threads mínimas
        executor.setMaxPoolSize(10);           // Threads máximas
        executor.setQueueCapacity(100);        // Fila de tarefas
        executor.setThreadNamePrefix("async-indexer-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        // Handler para exceções não capturadas
        executor.setRejectedExecutionHandler((r, executor1) -> 
            logger.warn("Task rejected, thread pool is full and queue is full"));
        
        executor.initialize();
        return executor;
    }
    
    /**
     * Handler para exceções não tratadas em métodos @Async
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            logger.error("Async method '{}' threw exception: {}", 
                        method.getName(), ex.getMessage(), ex);
            // TODO: Implementar notificação de erro (email, Slack, etc.)
        };
    }
}
