package com.hdp.connectorregistry.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdp.connectorregistry.io.ConnectorLoader;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class RequestPlannerTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    @Test
    void resolvesBaseUrlQpsAndSignerHeaders() throws Exception {
        var loaded = new ConnectorLoader().load(resourcePath("fixtures/connector/minimal/connector.yaml"));
        var config = OBJECT_MAPPER.readTree(resourcePath("fixtures/config/preview-config.json").toFile());

        RequestPreview preview = new RequestPlanner().preview(loaded, "users", config);

        assertThat(preview.streamName()).isEqualTo("users");
        assertThat(preview.method()).isEqualTo("GET");
        assertThat(preview.url()).isEqualTo("https://api.example.com/users");
        assertThat(preview.effectiveQps()).isEqualTo(2);
        assertThat(preview.headers()).containsEntry("X-Signature", "signed");
    }

    private static Path resourcePath(String resourceName) throws URISyntaxException {
        var resource = RequestPlannerTest.class.getClassLoader().getResource(resourceName);
        return Path.of(Objects.requireNonNull(resource, resourceName).toURI());
    }
}
