package com.example.tweetservice;

import akka.actor.ActorSystem;
import akka.japi.function.Function;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Source;
import akka.stream.scaladsl.Sink;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;

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

		public static void main(String[] args) {
				SpringApplication.run(TweetServiceApplication.class, args);
		}
}

@Component
class TweetWriter implements ApplicationRunner {

		private final TweetRepository repository;

		TweetWriter(@Value("${message}") String msg, TweetRepository repository) {
				this.repository = repository;
				System.out.println("message: " + msg);
		}

		@Override
		public void run(ApplicationArguments args) throws Exception {
				Author viktor = new Author("viktorklang");
				Author jboner = new Author("jboner");
				Author josh = new Author("starbuxman");

				Tweet[] tweetsArray = {
					new Tweet("woot, konrad will be talking #enterprise #integration done right! #akka #alpakka", viktor),
					new Tweet("#scala implicits can easily be used to model capabilities, but can they encode obligations easily? \n\n* Easy in ergonomically?", viktor),
					new Tweet("this is so cool! #akka", viktor),
					new Tweet("cross data center replication of event sourced #akka actors is soon available (using #crdts and more!)", jboner),
					new Tweet("a reminder: @SpringBoot lets you pair program with the #Spring team", josh),
					new Tweet("whatever your next platform is, don't build it yourself.\n\n Even companies with the $$ and motivation to do it fail. a LOT", josh)
				};
				Flux<Tweet> tweets = Flux.fromArray(tweetsArray);
				this.repository
					.deleteAll()
					.thenMany(this.repository.saveAll(tweets))
					.thenMany(this.repository.findAll())
					.subscribe(System.out::println);
		}
}

@Service
class SyntheticService {

		Flux<String> generate() {
				return Flux.<String>generate(sink -> sink.next("hello, " + Instant.now().toString() + "!"))
					.delayElements(Duration.ofSeconds(1));
		}
}

@Service
class TweetService {

		private final TweetRepository repository;
		private final ActorMaterializer actorMaterializer;

		TweetService(TweetRepository repository, ActorMaterializer actorMaterializer) {
				this.repository = repository;
				this.actorMaterializer = actorMaterializer;
		}

		Publisher<Tweet> tweets() {
				return repository.findAll();
		}

		Publisher<HashTag> hashtags() {
				return Source
					.fromPublisher(this.tweets())
					.map(Tweet::getHashTags)
					.reduce((a, b) -> {
							Set<HashTag> hashTags = new HashSet<>();
							hashTags.addAll(a);
							hashTags.addAll(b);
							return hashTags;
					})
					.mapConcat((Function<Set<HashTag>, Iterable<HashTag>>) param -> param)
					.runWith(Sink.asPublisher(true), this.actorMaterializer);
		}
}

@Configuration
class TweetRoutes {

		private final TweetService tweetService;

		TweetRoutes(TweetService tweetService) {
				this.tweetService = tweetService;
		}

		@Bean
		RouterFunction<ServerResponse> routes() {
				return route(GET("/tweets"), r -> ok().body(this.tweetService.tweets(), Tweet.class))
					.andRoute(GET("/hashtags/unique"), r -> ok().body(tweetService.hashtags(), HashTag.class));
		}
}

@Configuration
class AkkaConfiguration {

		@Bean
		ActorMaterializer actorMaterializer() {
				return ActorMaterializer.create(actorSystem());
		}

		@Bean
		ActorSystem actorSystem() {
				return ActorSystem.create("bootiful-akka");
		}
}

interface TweetRepository extends ReactiveMongoRepository<Tweet, String> {
}

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document
class HashTag {

		@Id
		private String tag;
}

@Document
@AllArgsConstructor
@NoArgsConstructor
@Data
class Tweet {

		@Id
		private String text;

		private Author author;

		public Set<HashTag> getHashTags() {
				return Stream
					.of(this.text.split(" "))
					.filter(x -> x.startsWith("#"))
					.map(x -> new HashTag(x.replaceAll("[^#\\w]", "")))
					.collect(Collectors.toSet());
		}

}

@Document
@AllArgsConstructor
@NoArgsConstructor
@Data
class Author {

		@Id
		private String handle;
}