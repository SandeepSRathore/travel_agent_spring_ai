package com.example.travelagent.advisor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiRedactionAdvisorTests {

	@Test
	void redactsEmail() {
		var result = PiiRedactionAdvisor.redact("Mail the plan to jane.doe+trips@example.co.in please");
		assertThat(result.text()).isEqualTo("Mail the plan to [EMAIL] please");
		assertThat(result.count()).isEqualTo(1);
	}

	@Test
	void redactsValidCardNumbers() {
		var result = PiiRedactionAdvisor.redact("Charge 4111 1111 1111 1111 or 5500-0000-0000-0004");
		assertThat(result.text()).isEqualTo("Charge [CARD] or [CARD]");
		assertThat(result.count()).isEqualTo(2);
	}

	@Test
	void redactsPhoneNumbers() {
		var result = PiiRedactionAdvisor.redact("Call +91 98765 43210 or (415) 555-2671");
		assertThat(result.text()).isEqualTo("Call [PHONE] or [PHONE]");
		assertThat(result.count()).isEqualTo(2);
	}

	@Test
	void keepsOrdinaryTravelNumbers() {
		String text = "Budget 150000 INR for 2 people, 12-18 Dec 2026, flight AI 302, 3-4 nights";
		var result = PiiRedactionAdvisor.redact(text);
		assertThat(result.text()).isEqualTo(text);
		assertThat(result.count()).isZero();
	}

}
