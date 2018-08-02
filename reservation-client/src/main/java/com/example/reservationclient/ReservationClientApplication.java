package com.example.reservationclient;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.reactivestreams.Publisher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.gateway.filter.ratelimit.PrincipalNameKeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.netflix.hystrix.HystrixCommands;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

// josh@Joshlong.com
// @starbuxman

@EnableBinding(Source.class)
@SpringBootApplication
public class ReservationClientApplication {

		public static void main(String[] args) {
				SpringApplication.run(ReservationClientApplication.class, args);
		}

		@Bean
		MapReactiveUserDetailsService authentication() {
				return new MapReactiveUserDetailsService(
					User.withDefaultPasswordEncoder().username("jane").password("pw").roles("USER").build(),
					User.withDefaultPasswordEncoder().username("josh").password("pw").roles("USER").build()
				);
		}

		@Bean
		SecurityWebFilterChain authorization(ServerHttpSecurity http) {
				//@formatter:off
				return
					http
					.authorizeExchange()
							.pathMatchers("/proxy").authenticated()
							.anyExchange().permitAll()
					.and()
					.csrf().disable()
					.httpBasic()
					.and()
					.build();
				//@formatter:on
		}

		@Bean
		RedisRateLimiter redisRateLimiter() {
				return new RedisRateLimiter(5, 7);
		}

		@Bean
		RouteLocator gateway(RouteLocatorBuilder rlb) {
				return rlb
					.routes()
					.route(
						routeSpec -> routeSpec
							.host("*.gw.sc").and().path("/proxy")
							.filters(fSpec -> fSpec
								.setPath("/reservations")
								.requestRateLimiter(rlSpec -> rlSpec
									.setRateLimiter(redisRateLimiter())
									.setKeyResolver(new PrincipalNameKeyResolver())
								)
							)
							.uri("lb://reservation-service/")
					)
					.build();
		}


		@Bean
		@LoadBalanced
		WebClient client(WebClient.Builder builder) {
				return builder.build();
		}

		@Bean
		RouterFunction<ServerResponse> routes(
			WebClient client,
			Source source) {

				return route(GET("/reservations/names"), request -> {

						Flux<String> names = client
							.get()
							.uri("http://reservation-service/reservations")
							.retrieve()
							.bodyToFlux(Reservation.class)
							.map(Reservation::getReservationName);

						Publisher<String> fallback = HystrixCommands
							.from(names)
							.commandName("names")
							.eager()
							.fallback(Flux.just("EEEK!"))
							.build();

						return ServerResponse.ok().body(fallback, String.class);
				})
					.andRoute(POST("/reservations"), request -> {

							Flux<Boolean> map = request
								.bodyToFlux(Reservation.class)
								.map(Reservation::getReservationName)
								.map(rn -> MessageBuilder.withPayload(rn).build())
								.map(msg -> source.output().send(msg));

							return ServerResponse.ok().body(map, Boolean.class);
					});
		}
}


@Data
@AllArgsConstructor
@NoArgsConstructor
class Reservation {
		private String id, reservationName;
}