package com.example.demo.config;

import com.els.context.AclUserProvider;
import com.example.demo.security.UserContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AclConfig {

    @Bean
    public AclUserProvider aclUserProvider() {
        // Biblioteka zapyta: "Kto teraz pyta?"
        // My odpowiadamy: "Ten, kogo wyciągnęliśmy z nagłówka X-User-Id"
        return () -> UserContext.getUserId();
    }
}