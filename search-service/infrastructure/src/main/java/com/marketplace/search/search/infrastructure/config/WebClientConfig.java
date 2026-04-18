package com.marketplace.search.search.infrastructure.config;

import java.time.Duration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import reactor.netty.http.client.HttpClient;

/**
 * Configuração do WebClient para chamadas HTTP reativas
 * Usado pelo MlRankingClient para comunicação com o ML Ranking Service
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder(ObjectProvider<WebClientCustomizer> customizerProvider) {
        // Configurar HttpClient do Netty com timeouts
        // Timeouts aumentados para suportar ML Ranking Service que pode demorar mais
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
            .responseTimeout(Duration.ofSeconds(20))
            .doOnConnected(conn ->
                conn.addHandlerLast(new ReadTimeoutHandler(20))
                    .addHandlerLast(new WriteTimeoutHandler(20)));

        WebClient.Builder builder = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient));

        // Aplicar customizers do Spring Boot (inclui ObservationWebClientCustomizer para tracing)
        customizerProvider.orderedStream().forEach(customizer -> customizer.customize(builder));

        return builder;
    }
}
