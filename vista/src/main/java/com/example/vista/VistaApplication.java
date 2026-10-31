package com.example.vista;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VistaApplication {
	public static void main(String[] args) {
		SpringApplication.run(VistaApplication.class, args);
	}
}
