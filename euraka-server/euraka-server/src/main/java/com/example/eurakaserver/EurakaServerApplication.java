package com.example.eurakaserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class EurakaServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(EurakaServerApplication.class, args);
	}
}
