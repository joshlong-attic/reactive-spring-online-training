package com.example.tweetservice;

import org.junit.Assert;
import org.junit.Test;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.junit.Assert.*;

/**
	* @author <a href="mailto:josh@joshlong.com">Josh Long</a>
	*/
public class SyntheticServiceTest {

		private final SyntheticService syntheticService = new SyntheticService();

		@Test
		public void greetings() {
				StepVerifier
					.withVirtualTime(() -> this.syntheticService.greetings().take(10).collectList())
					.thenAwait(Duration.ofSeconds(20))
					.consumeNextWith(results -> Assert.assertEquals(results.size(), 10))
					.verifyComplete();
		}
}