package oro.tirotiro.equipmentwarehouse.config;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String version, ZoneId timeZone, Security security, BootstrapAdmin bootstrapAdmin) {

    public record Security(boolean rememberMeEnabled) {
    }

    public record BootstrapAdmin(String email, String password, String name) {
    }
}
