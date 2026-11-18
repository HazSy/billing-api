package dev.hazsy.billing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BillingApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BillingApiApplication.class, args);
    }
}
