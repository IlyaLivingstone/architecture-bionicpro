package ru.bionicpro.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import ru.bionicpro.api.config.ApiProperties;

@SpringBootApplication
@EnableConfigurationProperties(ApiProperties.class)
public class ReportsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportsApplication.class, args);
    }
}
