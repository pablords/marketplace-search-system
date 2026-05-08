package com.marketplace.search.indexing.infrastructure.config;

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
     * Executor para operações assíncronas de indexação (usado pelos use cases)
     */
    @Bean(name = "asyncIndexingExecutor")
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // Configurações otimizadas para alto volume de indexação
        executor.setCorePoolSize(20);          // Aumentado de 5 para 20
        executor.setMaxPoolSize(50);           // Aumentado de 10 para 50
        executor.setQueueCapacity(1000);       // Aumentado de 100 para 1000
        executor.setThreadNamePrefix("async-indexer-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        // Estratégia de Backpressure: Quando a fila encher, a thread que está 
        // produzindo (Kafka Listener) executará a tarefa, naturalmente 
        // diminuindo o ritmo de consumo do Kafka até que o pool libere espaço.
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        
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
