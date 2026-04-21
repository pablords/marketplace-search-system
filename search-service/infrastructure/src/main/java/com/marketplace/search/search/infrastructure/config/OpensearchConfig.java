package com.marketplace.search.search.infrastructure.config;

import org.apache.http.HttpHost;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
public class OpensearchConfig {

	@Value("${opensearch.scheme:http}")
	private String scheme;

	@Value("${opensearch.host:localhost}")
	private String host;

	@Value("${opensearch.port:9200}")
	private int port;

	@Bean(destroyMethod = "close")
	public RestClient restClient() {
		return RestClient.builder(new HttpHost(host, port, scheme)).build();
	}

	@Bean
	public OpenSearchClient openSearchClient(RestClient restClient) {
		// Configure ObjectMapper so Java Time (Instant) is supported by the OpenSearch JSON mapper
		ObjectMapper om = new ObjectMapper();
		om.registerModule(new JavaTimeModule());
		om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		om.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

		RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper(om));
		return new OpenSearchClient(transport);
	}
}

