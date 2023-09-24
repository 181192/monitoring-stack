package dev.kalli.demo.tracing.config;

import io.micrometer.observation.ObservationPredicate;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.actuate.endpoint.web.PathMappedEndpoints;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.web.filter.reactive.ServerHttpObservationFilter;

import java.nio.file.Path;

@Configuration
public class ObservationFilterConfiguration {

    // if an ObservationRegistry is configured
    @ConditionalOnBean(ObservationRegistry.class)
    // if we do not use Actuator
    @ConditionalOnMissingBean(ServerHttpObservationFilter.class)
    @Bean
    public ServerHttpObservationFilter observationFilter(ObservationRegistry registry) {
        return new ServerHttpObservationFilter(registry);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(value = "management.observations.http.server.actuator.enabled", havingValue = "false")
    static class ActuatorWebEndpointObservationConfiguration {

        @Bean
        ObservationPredicate actuatorWebEndpointObservationPredicate(WebFluxProperties webFluxProperties,
                                                                     PathMappedEndpoints pathMappedEndpoints) {
            return (name, context) -> {
                if (context instanceof ServerRequestObservationContext serverContext) {
                    String endpointPath = getEndpointPath(webFluxProperties, pathMappedEndpoints);
                    return !serverContext.getCarrier().getURI().getPath().startsWith(endpointPath);
                }
                return true;
            };

        }

        private static String getEndpointPath(WebFluxProperties webFluxProperties,
                                              PathMappedEndpoints pathMappedEndpoints) {
            String webFluxBasePath = getWebFluxBasePath(webFluxProperties);
            return Path.of(webFluxBasePath, pathMappedEndpoints.getBasePath()).toString();
        }

        private static String getWebFluxBasePath(WebFluxProperties webFluxProperties) {
            return (webFluxProperties.getBasePath() != null) ? webFluxProperties.getBasePath() : "";
        }

    }
}
