package com.example.tweetservice;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.junit.Assert.*;

public class SyntheticServiceTest {

		private final SyntheticService syntheticService = new SyntheticService();

		@Test
		public void generate() {
				StepVerifier
					.withVirtualTime(() -> this.syntheticService.generate().take(10).collectList())
					.thenAwait(Duration.ofSeconds(20))
					.consumeNextWith(x -> Assert.assertEquals(10, x.size()))
					.verifyComplete();
		}
}