package com.bcosmos.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
public class TestBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(TestBackendApplication.class, args);
	}

}
