package com.example.tweetclient;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.reactivestreams.Publisher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.ratelimit.PrincipalNameKeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.netflix.hystrix.HystrixCommands;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;

import java.util.Set;

import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;


@EnableBinding(Source.class)
@SpringBootApplication
public class TweetClientApplication {

		@Bean
		WebClient client(WebClient.Builder build) {
				return build.build();
		}

		@Bean
		RouterFunction<ServerResponse> routes(Source src, WebClient client) {
				return route(RequestPredicates.GET("/tags"), request -> {
						Publisher<String> map = client
							.get()
							.uri("http://localhost:8080/hashtags")
							.retrieve()
							.bodyToFlux(HashTag.class)
							.map(HashTag::getTag)
							.map(String::toUpperCase);

						Publisher<String> build = HystrixCommands
							.from(map)
							.fallback(Flux.just("EEEK!"))
							.eager()
							.commandName("fallback-uppercase-tags")
							.build();

						return ServerResponse.ok().body(build, String.class);
				})
					.andRoute(POST("/authors"), request -> {

							Flux<Boolean> map = request

								.bodyToFlux(Author.class)
								.map(Author::getHandle)
								.map(handle -> MessageBuilder.withPayload(handle).build())
								.map(msg -> src.output().send(msg));

							return ServerResponse.ok().body(map, Boolean.class);
					});
		}

		@Bean
		RedisRateLimiter redisRateLimiter() {
				return new RedisRateLimiter(3, 4);
		}

		@Bean
		SecurityWebFilterChain authorization(ServerHttpSecurity http) {
				http.httpBasic();
				http.csrf().disable();
				http
					.authorizeExchange()
					.pathMatchers("/proxy").authenticated()
					.anyExchange().permitAll();
				return http.build();
		}

		@Bean
		MapReactiveUserDetailsService authentications() {
				return new MapReactiveUserDetailsService(
					User.withDefaultPasswordEncoder().username("jlong").roles("USER").password("password").build()
				);
		}

		@Bean
		RouteLocator gateway(RouteLocatorBuilder rlb) {
				return rlb
					.routes()
					.route(rSpec ->
						rSpec
							.path("/proxy").and().host("*.foo.gw")
							.filters(fSpec -> fSpec.requestRateLimiter(config ->
								config
									.setRateLimiter(this.redisRateLimiter())
									.setKeyResolver(new PrincipalNameKeyResolver())
							))
							.uri("http://localhost:8080/hashtags")
					)
					.build();

		}

		public static void main(String[] args) {
				SpringApplication.run(TweetClientApplication.class, args);
		}
}


@Data
@AllArgsConstructor
class Tweet {

		@Id
		private String text;

		private Author author;

		private Set<HashTag> hashTags;
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class Author {

		@Id
		private String handle;
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class HashTag {

		@Id
		private String tag;
}