package com.example.reservationservice

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import reactor.test.StepVerifier
import java.time.Duration

/**
 * @author [Josh Long](mailto:josh@joshlong.com)
 */
@SpringBootTest
@RunWith(SpringRunner::class)
class SseServiceTest {

	val sseService = SseService()

	@Test
	fun sse() {

		StepVerifier
				.withVirtualTime { sseService.sse().take(10).collectList() }
				.thenAwait(Duration.ofSeconds(20))
				.consumeNextWith { Assert.assertTrue(it.size == 10) }
				.verifyComplete()

	}
}