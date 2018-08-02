package com.example.reservationservice

import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.stream.annotation.EnableBinding
import org.springframework.cloud.stream.annotation.Input
import org.springframework.cloud.stream.annotation.StreamListener
import org.springframework.cloud.stream.messaging.Sink
import org.springframework.context.support.beans
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.time.Instant

@EnableBinding(Sink::class)
@SpringBootApplication
class ReservationServiceApplication

@Component
class Processor(val reservationRepository: ReservationRepository) {

	@StreamListener
	fun write(@Input(Sink.INPUT) names: Flux<String>) {
		names
				.map { Reservation(reservationName = it) }
				.flatMap { reservationRepository.save(it) }
				.subscribe {
					println("wrote ${it.reservationName} to the DB with ID # ${it.id}")
				}
	}
}

fun main(args: Array<String>) {
	runApplication<ReservationServiceApplication>(*args) {
		addInitializers(beans {
			bean {
				router {
					val reservationRepository = ref<ReservationRepository>()
					GET("/reservations") {
						ServerResponse.ok().body(reservationRepository.findAll())
					}
				}
			}
			bean {
				ApplicationRunner {
					val reservationRepository = ref<ReservationRepository>()
					reservationRepository
							.deleteAll()
							.thenMany(Flux
									.just("Josh", "Mario", "Madhura", "Cornelia", "Dr. Syer", "Dr. Pollack", "Jennifer", "Violetta")
									.map { Reservation(null, it) }
									.flatMap { reservationRepository.save(it) }
									.subscribeOn(Schedulers.elastic())
							)
							.thenMany(reservationRepository.findAll())
							.subscribe { println(it) }
				}
			}
		})
	}
}

@Service
class SseService {
	fun sse() = Flux
			.generate<String> { sink -> sink.next("Hello @ ${Instant.now()}!") }
			.delayElements(Duration.ofSeconds(1))
}


/*
@RestController
class ReservationRestController(val reservationRepository: ReservationRepository,
                                val sseService: SseService) {

	@GetMapping(produces = arrayOf(MediaType.TEXT_EVENT_STREAM_VALUE), value = arrayOf("/sse"))
	fun sse() = this.sseService.sse()

	@GetMapping("/reservations")
	fun reservations(): Publisher<Reservation> = this.reservationRepository.findAll()
}
*/

interface ReservationRepository : ReactiveMongoRepository<Reservation, String>

@Document
data class Reservation(val id: String? = null, val reservationName: String? = null)