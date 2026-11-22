package az.tarlan.utility.proxy.adapter.web;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SwaggerUiDisabledIT extends AbstractWebIT {

    @ParameterizedTest
    @ValueSource(strings = {"/swagger-ui/index.html", "/swagger-ui.html", "/v3/api-docs", "/openapi.yaml"})
    void documentationIsNotServedOutsideTheLocalProfile(String path) {
        assertThat(get(path).status()).isEqualTo(404);
    }
}
