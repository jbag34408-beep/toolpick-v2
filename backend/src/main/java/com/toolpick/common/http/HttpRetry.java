package com.toolpick.common.http;

import com.toolpick.common.config.ToolPickProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 429·5xx만 재시도한다(최대 3번). 대기는 Retry-After(초)가 있으면 그 값(상한 60초), 없으면 지수 백오프(1s → 2s → 4s).
 * [CS] 4xx(429 제외)는 다시 보내도 결과가 같으므로 재시도하지 않는다.
 */
@Slf4j
@Component
public class HttpRetry {

    static final int MAX_RETRIES = 3;
    private static final Duration MAX_WAIT = Duration.ofSeconds(60);

    private final Duration baseDelay;

    @Autowired
    public HttpRetry(ToolPickProperties props) {
        this(props.retryBaseDelay());
    }

    public HttpRetry(Duration baseDelay) {
        this.baseDelay = baseDelay;
    }

    public <T> T call(Supplier<T> action) {
        for (int attempt = 0; ; attempt++) {
            try {
                return action.get();
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                boolean retryable = status == 429 || status >= 500;
                if (!retryable || attempt >= MAX_RETRIES) {
                    throw e;
                }
                Duration wait = waitTime(e, attempt);
                log.warn("HTTP {} → {}ms 후 재시도 ({}/{})", status, wait.toMillis(), attempt + 1, MAX_RETRIES);
                sleep(wait);
            }
        }
    }

    private Duration waitTime(RestClientResponseException e, int attempt) {
        HttpHeaders headers = e.getResponseHeaders();
        String retryAfter = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter != null && retryAfter.matches("\\d+")) {
            Duration d = Duration.ofSeconds(Long.parseLong(retryAfter));
            return d.compareTo(MAX_WAIT) > 0 ? MAX_WAIT : d;
        }
        return baseDelay.multipliedBy(1L << attempt);
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("재시도 대기 중 중단됨", ie);
        }
    }
}
