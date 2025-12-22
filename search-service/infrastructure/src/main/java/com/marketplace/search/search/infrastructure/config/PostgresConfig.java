package com.marketplace.search.search.infrastructure.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Configuração do PostgreSQL para Feature Store Offline no Search Service
 * Só é ativada se a feature flag estiver habilitada
 */
@Configuration
@ConditionalOnProperty(name = "features.feature-store-offline.enabled", havingValue = "true", matchIfMissing = true)
@EnableJpaRepositories(
    basePackages = "com.marketplace.search.search.infrastructure.featurestore",
    entityManagerFactoryRef = "featureStoreEntityManagerFactory",
    transactionManagerRef = "featureStoreTransactionManager"
)
@EnableTransactionManagement
public class PostgresConfig {

    @Value("${spring.datasource.feature-store.url:jdbc:postgresql://localhost:5432/catalog}")
    private String jdbcUrl;

    @Value("${spring.datasource.feature-store.username:catalog}")
    private String username;

    @Value("${spring.datasource.feature-store.password:catalog}")
    private String password;

    @Value("${spring.datasource.feature-store.driver-class-name:org.postgresql.Driver}")
    private String driverClassName;

    @Bean(name = "featureStoreDataSource")
    public DataSource featureStoreDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        
        return new HikariDataSource(config);
    }

    @Bean(name = "featureStoreEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean featureStoreEntityManagerFactory(
            @Qualifier("featureStoreDataSource") DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.marketplace.search.search.infrastructure.featurestore");
        
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setShowSql(false);
        vendorAdapter.setGenerateDdl(false);
        
        em.setJpaVendorAdapter(vendorAdapter);
        
        java.util.Properties properties = new java.util.Properties();
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.setProperty("hibernate.hbm2ddl.auto", "none");
        properties.setProperty("hibernate.format_sql", "true");
        em.setJpaProperties(properties);
        
        return em;
    }

    @Bean(name = "featureStoreTransactionManager")
    public PlatformTransactionManager featureStoreTransactionManager(
            @Qualifier("featureStoreEntityManagerFactory") LocalContainerEntityManagerFactoryBean emfBean) {
        return new JpaTransactionManager(emfBean.getObject());
    }
}

