package com.bcosmos.worker;

import org.springframework.boot.SpringApplication;

public class TestAnalysisWorkerApplication {

	public static void main(String[] args) {
		SpringApplication.from(AnalysisWorkerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
