package com.example.tweetclient;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SpringBootApplication
public class TweetClientApplication {

		@Bean
		RouteLocator routeLocator(RouteLocatorBuilder rlb) {
				return rlb
					.routes()
					.route(rs -> rs.path("/proxy")
						.uri("lb://tweet-service/tweets"))
					.build();
		}


		@Bean
		@LoadBalanced
		WebClient webClient(WebClient.Builder builder) {
				return builder.build();
		}

		@Bean
		RouterFunction<ServerResponse> routes(WebClient client) {
				return RouterFunctions.route(RequestPredicates.GET("/tweets/summary"), request -> {
						Flux<String> map = client
						.get()
						.uri("http://tweet-service/tweets")
						.retrieve()
						.bodyToFlux(Tweet.class)
						.map(tweet -> "@" + tweet.getAuthor() + ":" + tweet.getText());
						return ServerResponse.ok().body(map, String.class);
				});
		}

		public static void main(String[] args) {
				SpringApplication.run(TweetClientApplication.class, args);
		}
}


@Data
@AllArgsConstructor
@NoArgsConstructor
class HashTag {

		@Id
		private String tag;
}

@AllArgsConstructor
@NoArgsConstructor
@Data
class Tweet {

		@Id
		private String text;

		private Author author;

		private Set<HashTag> hashTags;

}

@AllArgsConstructor
@NoArgsConstructor
@Data
class Author {

		@Id
		private String handle;
}