package com.marketplace.search.indexing.infrastructure.config;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.springframework.beans.factory.annotation.Qualifier;
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

	@Value("${opensearch.username:admin}")
	private String username;

	@Value("${opensearch.password:admin}")
	private String password;

	@Bean(destroyMethod = "close")
	public RestClient restClient() {
		final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
		credentialsProvider.setCredentials(AuthScope.ANY,
				new UsernamePasswordCredentials(username, password));

		return RestClient.builder(new HttpHost(host, port, scheme))
				.setHttpClientConfigCallback(httpClientBuilder -> 
					httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider))
				.build();
	}

	@Bean
	@Qualifier("indexingOpensearchClient")
	public OpenSearchClient openSearchClient(RestClient restClient) {
			// Configure ObjectMapper so Java Time (Instant) is supported by the OpenSearch JSON mapper
			ObjectMapper om = new ObjectMapper();
			om.registerModule(new JavaTimeModule());
			om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

			RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper(om));
			return new OpenSearchClient(transport);
	}
}