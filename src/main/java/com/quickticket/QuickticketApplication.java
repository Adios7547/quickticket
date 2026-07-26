package com.quickticket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class QuickticketApplication {

	public static void main(String[] args) {
		SpringApplication.run(QuickticketApplication.class, args);
	}

}
