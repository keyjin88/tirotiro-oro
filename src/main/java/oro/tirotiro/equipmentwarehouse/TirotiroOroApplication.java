package oro.tirotiro.equipmentwarehouse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TirotiroOroApplication {

    public static void main(String[] args) {
        SpringApplication.run(TirotiroOroApplication.class, args);
    }
}
