package com.onsemi.cim.apps.exensio.exensioDearchiver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ExensioDearchiverApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(ExensioDearchiverApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(ExensioDearchiverApplication.class);
    }

}
