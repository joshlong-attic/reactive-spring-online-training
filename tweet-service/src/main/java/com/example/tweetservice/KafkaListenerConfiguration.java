package com.example.tweetservice;

import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.messaging.Sink;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;


@EnableBinding(Sink.class)
@Configuration
public class KafkaListenerConfiguration {

		private final AuthorRepository repository;

		public KafkaListenerConfiguration(AuthorRepository repository) {
				this.repository = repository;
		}

		@StreamListener
		public void process(@Input(Sink.INPUT) Flux<String> names) {
				names
					.map(Author::new)
					.flatMap(this.repository::save)
					.subscribe(saved -> System.out.println("saved " + saved.toString() + '.'));
		}
}
