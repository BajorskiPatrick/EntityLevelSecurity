package com.els.config;

import com.els.aspect.AclSecurityAspect;
import com.els.context.AclUserProvider;
import com.els.jdbc.AclDataSourceBeanPostProcessor;
import com.els.logger.Logger;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Configuration
public class ElsAutoConfiguration {

    private static final Logger logger = Logger.getInstance();

    @Bean
    public AclSecurityAspect aclSecurityAspect() {
        return new AclSecurityAspect();
    }

    @Bean
    public AclDataSourceBeanPostProcessor aclDataSourceBeanPostProcessor() {
        return new AclDataSourceBeanPostProcessor();
    }

    // Domyślny provider, jeśli aplikacja nie dostarczy własnego
    @Bean
    @ConditionalOnMissingBean(AclUserProvider.class)
    public AclUserProvider defaultUserProvider() {
        return () -> "system-default-user";
    }

    @Bean
    public AclTableInitializer aclTableInitializer(DataSource dataSource) {
        return new AclTableInitializer(dataSource);
    }

    public static class AclTableInitializer {
        private final DataSource dataSource;

        public AclTableInitializer(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @PostConstruct
        public void init() {
            // Używamy składni kompatybilnej z H2 i Postgres
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {

                // SERIAL działa w Postgres i H2.
                // W MySQL trzeba by użyć AUTO_INCREMENT, ale na demo SERIAL wystarczy.
                String sql = """
                    CREATE TABLE IF NOT EXISTS els_acl_table (
                        id SERIAL PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        table_name VARCHAR(50) NOT NULL,
                        row_id BIGINT NOT NULL
                    )
                """;
                stmt.execute(sql);
                logger.log("ACL Table initialized successfully.");

            } catch (Exception e) {
                logger.logException(e);
            }
        }
    }
}