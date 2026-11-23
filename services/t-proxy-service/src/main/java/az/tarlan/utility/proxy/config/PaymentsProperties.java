package az.tarlan.utility.proxy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("proxy.payments")
public record PaymentsProperties(@DefaultValue("1s") Duration retryAfter) {
}
