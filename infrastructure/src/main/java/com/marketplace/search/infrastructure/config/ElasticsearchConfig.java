package com.marketplace.search.infrastructure.config;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;

/**
 * Configuração do Elasticsearch
 */
@Configuration
public class ElasticsearchConfig {

    @Value("${elasticsearch.host:localhost}")
    private String host;

    @Value("${elasticsearch.port:9200}")
    private int port;

    @Value("${elasticsearch.scheme:http}")
    private String scheme;

    @Value("${elasticsearch.username:}")
    private String username;

    @Value("${elasticsearch.password:}")
    private String password;

    @Value("${elasticsearch.connection-timeout:10000}")
    private int connectionTimeout;

    @Value("${elasticsearch.socket-timeout:60000}")
    private int socketTimeout;

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        return new ElasticsearchClient(elasticsearchTransport());
    }

    @Bean
    public ElasticsearchTransport elasticsearchTransport() {
        return new RestClientTransport(
            restClient(),
            new JacksonJsonpMapper()
        );
    }

    @Bean
    public RestClient restClient() {
        RestClientBuilder builder = RestClient.builder(
            new HttpHost(host, port, scheme)
        );

        // Configurar timeouts
        builder.setRequestConfigCallback(requestConfigBuilder ->
            requestConfigBuilder
                .setConnectTimeout(connectionTimeout)
                .setSocketTimeout(socketTimeout)
        );

        // Configurar autenticação se fornecida
        if (!username.isEmpty() && !password.isEmpty()) {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                AuthScope.ANY,
                new UsernamePasswordCredentials(username, password)
            );

            builder.setHttpClientConfigCallback(httpClientBuilder ->
                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
            );
        }

        return builder.build();
    }
}