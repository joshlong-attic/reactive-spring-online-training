package demo;

import akka.actor.ActorSystem;
import akka.japi.function.Function;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.AsPublisher;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/**
	* @author <a href="mailto:josh@joshlong.com">Josh Long</a>
	*/
@SpringBootApplication
public class DemoApplication {

		public static void main(String[] args) {
				SpringApplication.run(DemoApplication.class, args);
		}

		@Bean
		RouterFunction<ServerResponse> routes(TweetService ts) {
				return
					route(GET("/tweets"), request -> ServerResponse.ok().body(ts.hashtags(), HashTag.class))
						.andRoute(GET("/hashtags/unique"), r -> ServerResponse.ok().body(ts.hashtags(), HashTag.class));
		}
}

@Component
class DataRunner implements ApplicationRunner {

		private final TweetRepository tweetRepository;

		DataRunner(TweetRepository tweetRepository) {
				this.tweetRepository = tweetRepository;
		}

		@Override
		public void run(ApplicationArguments args) throws Exception {
				Author viktor = new Author("viktorklang");
				Author jonas = new Author("jboner");
				Author josh = new Author("starbuxman");
				Tweet[] array = {
					new Tweet("Woot, Konrad will be talking about #Enterprise #Integration done right! #akka #alpakka", viktor),
					new Tweet("#scala implicits can easily be used to model Capabilities, but can they encode Obligations easily?\n\n* Easy as in: ergonomically.", viktor),
					new Tweet("This is so cool! #akka", viktor),
					new Tweet("Cross Data Center replication of Event Sourced #Akka Actors is soon available (using #CRDTs, and more).", jonas),
					new Tweet("a reminder: @SpringBoot lets you pair-program with the #Spring team.", josh),
					new Tweet("whatever your next #platform is, don't build it yourself. \n\nEven companies with the $$ and motivation to do it fail. a LOT.", josh)
				};
				Flux<Tweet> tweets = Flux.fromArray(array);
				Log log = LogFactory.getLog(getClass());
				tweetRepository
					.deleteAll()
					.thenMany(tweetRepository.saveAll(tweets))
					.thenMany(tweetRepository.findAll())
					.subscribe(t -> log.info("tweet: " + t.getAuthor().getHandle() + " : " + t.getHashTags().stream().map(HashTag::getTag).collect(Collectors.joining(","))));
		}
}

@Service
class TweetService {

		private final TweetRepository tweetRepository;
		private final ActorMaterializer actorMaterializer;

		TweetService(TweetRepository tweetRepository, ActorMaterializer actorMaterializer) {
				this.tweetRepository = tweetRepository;
				this.actorMaterializer = actorMaterializer;
		}

		Publisher<Tweet> tweets() {
				return this.tweetRepository.findAll();
		}

		Publisher<HashTag> hashtags() {
				return
					Source
						.fromPublisher(tweets())
						.map(Tweet::getHashTags)
						.reduce((a, b) -> {
								Set<HashTag> reduction = new HashSet<>();
								reduction.addAll(a);
								reduction.addAll(b);
								return reduction;
						})
						.mapConcat((Function<Set<HashTag>, Iterable<HashTag>>) param -> param)
						.runWith(Sink.asPublisher(AsPublisher.WITH_FANOUT), this.actorMaterializer);

		}
}

interface TweetRepository extends ReactiveMongoRepository<Tweet, String> {
}

@AllArgsConstructor
@NoArgsConstructor
@Data
@Document
class Tweet {

		@Id
		private String text;

		private Author author;

		public Set<HashTag> getHashTags() {
				return Stream
					.of(this.text.split(" "))
					.filter(t -> t.startsWith("#"))
					.map(t -> new HashTag(t.replaceAll("[^#\\w]", "").toLowerCase()))
					.collect(Collectors.toSet());
		}
}

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document
class HashTag {

		@Id
		private String tag;
}

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document
class Author {

		@Id
		private String handle;
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