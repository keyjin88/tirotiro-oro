package oro.tirotiro.equipmentwarehouse;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

class TirotiroOroApplicationTests {

    @Test
    void bootstrapClassDeclaresSpringBootConfiguration() {
        assertThat(TirotiroOroApplication.class)
                .hasAnnotation(SpringBootApplication.class)
                .hasAnnotation(ConfigurationPropertiesScan.class);
    }
}
