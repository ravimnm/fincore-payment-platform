package com.fincore.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@ConfigurationPropertiesScan
@SpringBootApplication
public class FinCoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(FinCoreApplication.class, args);
	}
	
}
