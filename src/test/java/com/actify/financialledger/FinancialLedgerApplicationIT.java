package com.actify.financialledger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Renamed from FinancialLedgerApplicationTests: loading the context needs PostgreSQL,
// so it runs with the other integration tests in "mvnw verify".
@SpringBootTest
@ActiveProfiles("test")
class FinancialLedgerApplicationIT {

	@Test
	void contextLoads() {
	}

}
