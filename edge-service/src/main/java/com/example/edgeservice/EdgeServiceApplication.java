package com.example.edgeservice;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.gateway.discovery.DiscoveryClientRouteDefinitionLocator;
import org.springframework.cloud.gateway.filter.factory.GatewayFilters;
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.Routes;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.client.ExchangeFilterFunctions;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.stream.IntStream;

import static org.springframework.cloud.gateway.handler.predicate.RoutePredicates.path;

// show routes:http://localhost:8081/application/gateway/routes
//https://www.youtube.com/watch?v=TwVtlNX-2Hs&feature=push-u&attr_tag=FSxfxxitr67BkWYL-6
@EnableWebFlux
@SpringBootApplication
public class EdgeServiceApplication {



    @Bean
    ApplicationRunner client () {
        return args ->{
            WebClient client = WebClient.builder().filter(ExchangeFilterFunctions.basicAuthentication("user", "pwd")).build();
            Flux.fromStream(IntStream.range(0, 100).boxed())
                    .flatMap(number -> client.get().uri("http://localhost:8081/rl").exchange())
                    .flatMap(clientResponse -> clientResponse.toEntity(String.class)).map(re -> String.format("status:%s;body:%s", re.getStatusCode(), re.getBody()))
                    .subscribe(System.out::println);

        };
    }


    // authentication
    @Bean
    MapReactiveUserDetailsService authentication() {
        return new MapReactiveUserDetailsService(
                User.withDefaultPasswordEncoder()
                    .username("user")
                    .password("pwd")
                    .roles("USER")
                    .build()
        );
    }

    // authorization
    @Bean
    SecurityWebFilterChain authorization(ServerHttpSecurity security) {
        return security.authorizeExchange().pathMatchers("/rl").authenticated()
                .anyExchange().permitAll()
                .and()
                .httpBasic()
                .and()
                .build();
    }

	@Bean
	DiscoveryClientRouteDefinitionLocator discoveryRoute(DiscoveryClient dc) {
		return new DiscoveryClientRouteDefinitionLocator(dc);
	}

	@Bean
    RouteLocator getwayRoutes(RequestRateLimiterGatewayFilterFactory rl) {
        return Routes
                .locator()
                .route("start")
                .predicate(path("/start"))
                .uri("http://start.spring.io:80")
                .route("lb")
                .predicate(path("/lb"))
                .uri("lb://customer-service/customers")
                .route("cf1")
                .predicate(path("/cf1"))
                // custom filter 1
                .filter( ((serverWebExchange, gatewayFilterChain) ->
                            gatewayFilterChain.filter( serverWebExchange)
                                    .then(Mono.fromRunnable(() -> {
                                        ServerHttpResponse response = serverWebExchange.getResponse();
                                        response.setStatusCode(HttpStatus.CONFLICT);
                                        response.getHeaders().setContentType(MediaType.APPLICATION_PDF);
                                    }))
                ))
                .uri("lb://customer-service/customers")
                // custom filter 2
                .route("cf2")
                .predicate(path("/cf2/**"))
                .filter(GatewayFilters.rewritePath("/cf2/(?<CID>.*)","/customers/${CID}"))
                .uri("lb://customer-service/")
                // circuit breaker
                .route("cb")
                .predicate(path( "/cb"))
                .filter( GatewayFilters.hystrix("cb"))
                .uri("lb://customer-service/delay")
                // rate limiter
                .route("rl")
                .predicate(path("/rl"))
                .filter( rl.apply(RedisRateLimiter.args(5,10)) )
                .uri("lb://customer-service/customers")
                .build();

    }

	public static void main(String[] args) {
		SpringApplication.run(EdgeServiceApplication.class, args);
	}
}
