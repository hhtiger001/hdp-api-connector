package com.hdp.connectorregistry.io;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConnectorLoaderTest {
    @Test
    void loadsConnectorAndResolvesExternalSchemaReference() {
        Path connectorPath = Path.of("src/test/resources/fixtures/connector/minimal/connector.yaml");

        var loaded = new ConnectorLoader().load(connectorPath);

        assertThat(loaded.connector().kind()).isEqualTo("ApiConnector");
        assertThat(loaded.connector().metadata().name()).isEqualTo("demo-users");
        assertThat(loaded.connector().spec().defaults().qps()).isEqualTo("2");
        assertThat(loaded.connector().spec().signers().get("fixed_header").className())
                .isEqualTo("com.hdp.connectorregistry.validator.support.FixedHeaderSigner");
        assertThat(loaded.schemasByRef()).containsKey("schemas/users.json");
        assertThat(loaded.schemasByRef().get("schemas/users.json").get("properties")).isNotNull();
    }
}
