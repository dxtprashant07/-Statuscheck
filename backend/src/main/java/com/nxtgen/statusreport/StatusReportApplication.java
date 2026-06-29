package com.nxtgen.statusreport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StatusReportApplication {

    public static void main(String[] args) {
        SpringApplication.run(StatusReportApplication.class, args);
    }
}
