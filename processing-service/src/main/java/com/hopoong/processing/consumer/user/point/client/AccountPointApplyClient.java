package com.hopoong.processing.consumer.user.point.client;

import com.hopoong.core.event.UserPointChangedEvent;
import com.hopoong.processing.consumer.user.point.exception.UserPointChangedProcessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AccountPointApplyClient {

    private final RestClient restClient;

    public AccountPointApplyClient(@Value("${app.account.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public void apply(UserPointChangedEvent event) {
        UserPointChangedApplyRequest request = new UserPointChangedApplyRequest(
                event.eventId(),
                event.userId(),
                event.orderId(),
                event.pointType(),
                event.changeAmount(),
                event.balanceAfter(),
                event.occurredAt()
        );

        try {
            restClient.post()
                    .uri("/internal/v1/users/point-changed")
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (httpRequest, response) -> {
                        throw UserPointChangedProcessException.business(
                                "account internal API 4xx 응답. status=" + response.getStatusCode().value()
                                        + ", userId=" + event.userId()
                        );
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (httpRequest, response) -> {
                        throw new UserPointChangedProcessException(
                                "account internal API 5xx 응답. status=" + response.getStatusCode().value(),
                                UserPointChangedProcessException.SYSTEM,
                                true
                        );
                    })
                    .toBodilessEntity();
        } catch (ResourceAccessException exception) {
            throw UserPointChangedProcessException.timeout("account internal API 타임아웃/네트워크 오류", exception);
        } catch (RestClientException exception) {
            throw UserPointChangedProcessException.system("account internal API 호출 실패", exception);
        }
    }
}
