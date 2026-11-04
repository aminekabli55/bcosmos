package com.bcosmos.miner;

import org.springframework.boot.SpringApplication;

public class TestMinerApplication {

	public static void main(String[] args) {
		SpringApplication.from(MinerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
