package dev.axonia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AxoniaSelfHostedApplication {

    public static void main(String[] args) {
        SpringApplication.run(AxoniaSelfHostedApplication.class, args);
    }

}
