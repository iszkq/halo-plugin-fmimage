package run.halo.fmimage.config;

import io.netty.resolver.DefaultAddressResolverGroup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {
    @Bean
    public WebClient.Builder webClientBuilder() {
        var httpClient = HttpClient.create()
            .followRedirect(true)
            // Prefer the system resolver so container and host DNS settings are honored.
            .resolver(DefaultAddressResolverGroup.INSTANCE);
        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
