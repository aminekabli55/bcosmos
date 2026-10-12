package com.bcosmos.analysis_worker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AnalysisWorkerApplicationTests {

	@Test
	void contextLoads() {
	}

}
