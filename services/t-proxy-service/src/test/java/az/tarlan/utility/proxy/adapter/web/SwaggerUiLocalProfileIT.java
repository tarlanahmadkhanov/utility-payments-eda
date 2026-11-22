package az.tarlan.utility.proxy.adapter.web;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("local")
class SwaggerUiLocalProfileIT extends AbstractWebIT {

    @Test
    void swaggerUiAndContractAreServed() {
        assertThat(get("/swagger-ui/index.html").status()).isEqualTo(200);

        Response contract = get("/openapi.yaml");

        assertThat(contract.status()).isEqualTo(200);
        assertThat(contract.body()).contains("title: t-proxy-service API");
    }
}
