package com.example.tweetservice;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.AsPublisher;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.reactivestreams.Publisher;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@SpringBootApplication
public class TweetServiceApplication {

		private final TweetService tweetService;

		public TweetServiceApplication(TweetService tweetService) {
				this.tweetService = tweetService;
		}


		Mono<ServerResponse> handle(ServerRequest r) {
				return ok().body(tweetService.tweets(), Tweet.class);
		}

		/*	HandlerFunction<ServerResponse> handleErrors(HandlerFunction<ServerResponse> target) {
					return request -> target
						.handle(request)
						.onErrorResume(Throwable.class, throwable -> ServerResponse.badRequest().syncBody("oops!"));

			}
	*/
		@Bean
		RouterFunction<ServerResponse> routes() {
				return route(GET("/tweets"), this::handle)
					.andRoute(GET("/hashtags"), r -> ok().body(tweetService.hashtags(), HashTag.class));
		}

		public static void main(String[] args) {
				SpringApplication.run(TweetServiceApplication.class, args);
		}
}

@Component
class TweetRunner implements ApplicationRunner {

		private final TweetRepository tweetRepository;

		TweetRunner(TweetRepository tweetRepository) {
				this.tweetRepository = tweetRepository;
		}

		@Override
		public void run(ApplicationArguments args) throws Exception {

				Author viktor = new Author("viktorklang");
				Author jboner = new Author("jboner");
				Author josh = new Author("starbuxman");

				Flux<Tweet> tweets = Flux.just(
					new Tweet(viktor, "woot, @Konrad will be talking #enterprise #integration done right!"),
					new Tweet(viktor, "#scala implicits can easily be used to model capabilities, but can they encode obligations easily?"),
					new Tweet(viktor, "this is so cool! #akka"),
					new Tweet(jboner, "cross data center replication of event sourced #akka actors is soon available (using #crdts and more!)"),
					new Tweet(josh, "a reminder: @SpringBoot lets you pair program with the #Spring team. #bootiful"),
					new Tweet(josh, "whatever your next platform is, don't build it yourself.\n\n" +
						"Even companies with the $$ and the motivation to do it fail. A LOT. #bootiful")
				);
				this
					.tweetRepository
					.deleteAll()
					.thenMany(tweets.flatMap(tweetRepository::save))
					.thenMany(tweetRepository.findAll())
//					.subscribeOn(Schedulers.fromExecutor(Executors.newSingleThreadExecutor()))
					.subscribe(System.out::println);
		}
}

/*
@RestController
class TweetRestController {

		private final TweetService tweetService;

		private final SyntheticService syntheticService;

		TweetRestController(TweetService tweetService, SyntheticService syntheticService) {
				this.tweetService = tweetService;
				this.syntheticService = syntheticService;
		}

		@GetMapping(name = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
		Publisher<String> greetings() {
				return this.syntheticService.greetings();
		}

		@GetMapping("/tweets")
		Publisher<Tweet> tweets() {
				return this.tweetService.tweets();
		}

		@GetMapping("/hashtags")
		Publisher<HashTag> hashtags() {
				return this.tweetService.hashtags();
		}


}
*/


@Service
class TweetService {

		private final ActorMaterializer actorMaterializer;
		private final TweetRepository tweetRepository;

		TweetService(ActorMaterializer actorMaterializer, TweetRepository tweetRepository) {
				this.actorMaterializer = actorMaterializer;
				this.tweetRepository = tweetRepository;
		}

		Publisher<HashTag> hashtags() {
				return Source
					.fromPublisher(this.tweets())
					.map(Tweet::getHashTags)
					.reduce((a, b) -> {
							Set<HashTag> tags = new HashSet<>();
							tags.addAll(a);
							tags.addAll(b);
							return tags;
					})
					.mapConcat(param -> param)
					.runWith(Sink.asPublisher(AsPublisher.WITH_FANOUT), this.actorMaterializer);
		}

		Publisher<Tweet> tweets() {
				return this.tweetRepository.findAll();
		}
}

interface AuthorRepository extends ReactiveMongoRepository<Author, String> {
}

@Configuration
class AkkaConfiguration {

		@Bean
		ActorMaterializer actorMaterializer() {
				return ActorMaterializer.create(this.actorSystem());
		}

		@Bean
		ActorSystem actorSystem() {
				return ActorSystem.create("bootiful-akka");
		}
}

@Service
class SyntheticService {

		public Flux<String> greetings() {
				return Flux.<String>generate(sink -> sink.next("hello " + Instant.now().toString()))
					.delayElements(Duration.ofSeconds(1));
		}
}

interface TweetRepository extends ReactiveMongoRepository<Tweet, String> {
}

@Data
@AllArgsConstructor
@Document
class Tweet {

		@Id
		private String text;

		private Author author;

		private Set<HashTag> hashTags;

		Tweet(Author a, String t) {
				this.text = t;
				this.author = a;
				this.hashTags = Stream.of(this.text.split(" "))
					.filter(x -> x.startsWith("#"))
					.map(x -> new HashTag(x.replaceAll("[^#\\w]", "")))
					.collect(Collectors.toSet());
		}
}

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document
class Author {

		@Id
		private String handle;
}

@Document
@Data
@AllArgsConstructor
@NoArgsConstructor
class HashTag {

		@Id
		private String tag;
}