package run.halo.fmimage.service;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.fmimage.model.FetchImageContentRequest;

@Service
public class GeneratedImageContentService {
    private static final int MAX_IMAGE_BYTES = 64 * 1024 * 1024;

    private final WebClient.Builder webClientBuilder;

    public GeneratedImageContentService(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    public Mono<ResponseEntity<byte[]>> fetch(FetchImageContentRequest request) {
        var uri = validateSourceUri(request.sourceUrl());
        return webClientBuilder
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IMAGE_BYTES))
            .build()
            .get()
            .uri(uri)
            .exchangeToMono(response -> {
                if (response.statusCode().isError()) {
                    return response.bodyToMono(String.class)
                        .defaultIfEmpty("上游图片地址返回错误")
                        .flatMap(message -> Mono.error(new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY,
                            "读取生成图片失败: " + compactMessage(message)
                        )));
                }

                var contentType = response.headers().contentType()
                    .orElseGet(() -> fallbackMediaType(request.mediaType()));
                return response.bodyToMono(byte[].class)
                    .defaultIfEmpty(new byte[0])
                    .flatMap(body -> {
                        if (body.length == 0) {
                            return Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_GATEWAY,
                                "读取生成图片失败: 上游返回了空内容"
                            ));
                        }

                        return Mono.just(ResponseEntity.ok()
                            .contentType(contentType)
                            .cacheControl(CacheControl.noStore())
                            .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(body.length))
                            .body(body));
                    });
            })
            .onErrorMap(ResponseStatusException.class, ex -> ex)
            .onErrorMap(ex -> new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "读取生成图片失败: " + rootMessage(ex),
                ex
            ));
    }

    private URI validateSourceUri(String sourceUrl) {
        if (!StringUtils.hasText(sourceUrl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sourceUrl 不能为空");
        }

        try {
            var uri = URI.create(sourceUrl.trim());
            var scheme = uri.getScheme();
            if (!StringUtils.hasText(scheme)
                || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 http 或 https 图片地址");
            }

            var host = uri.getHost();
            if (!StringUtils.hasText(host)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片地址缺少主机名");
            }
            ensurePublicHost(host.trim());
            return uri;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片地址格式无效", ex);
        }
    }

    private void ensurePublicHost(String host) {
        var normalized = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(normalized) || normalized.endsWith(".local")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不允许读取本地或局域网地址");
        }

        try {
            for (var address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()
                    || isCarrierGradeNat(address)
                    || isUniqueLocalIpv6(address)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不允许读取本地或局域网地址");
                }
            }
        } catch (UnknownHostException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片地址主机名无法解析", ex);
        }
    }

    private boolean isCarrierGradeNat(InetAddress address) {
        if (!(address instanceof Inet4Address)) {
            return false;
        }
        var bytes = address.getAddress();
        var first = Byte.toUnsignedInt(bytes[0]);
        var second = Byte.toUnsignedInt(bytes[1]);
        return first == 100 && second >= 64 && second <= 127;
    }

    private boolean isUniqueLocalIpv6(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return false;
        }
        var first = Byte.toUnsignedInt(address.getAddress()[0]);
        return (first & 0xfe) == 0xfc;
    }

    private MediaType fallbackMediaType(String raw) {
        if (StringUtils.hasText(raw)) {
            try {
                return MediaType.parseMediaType(raw.trim());
            } catch (IllegalArgumentException ignored) {
                // Fall back to png below.
            }
        }
        return MediaType.IMAGE_PNG;
    }

    private String compactMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return "上游图片地址返回错误";
        }
        var compact = message.replaceAll("\\s+", " ").trim();
        return compact.length() > 240 ? compact.substring(0, 240) + "..." : compact;
    }

    private String rootMessage(Throwable throwable) {
        var cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (StringUtils.hasText(cause.getMessage())) {
            return cause.getMessage();
        }
        return throwable.getMessage();
    }
}
