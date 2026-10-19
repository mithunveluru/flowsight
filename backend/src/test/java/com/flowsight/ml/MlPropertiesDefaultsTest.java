package com.flowsight.ml;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

// application.yml must ship with ML unconfigured and every capability flag off.
class MlPropertiesDefaultsTest {

    @Test
    void shippedDefaultsAreOff() throws Exception {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
            .forEach(env.getPropertySources()::addLast);
        ConfigurationPropertySources.attach(env);

        MlProperties props = Binder.get(env).bind("application.ml", MlProperties.class).get();

        assertThat(props.configured()).isFalse();
        assertThat(props.connectTimeout()).isLessThan(Duration.ofSeconds(1));
        for (MlCapability capability : MlCapability.values()) {
            MlProperties.CapabilityFlags flags = props.flags(capability);
            assertThat(props.capabilities()).containsKey(capability.id());
            assertThat(flags.enabled()).as(capability.id()).isFalse();
            assertThat(flags.shadow()).as(capability.id()).isFalse();
            if (capability == MlCapability.OCR) {
                // receipt OCR runs outside any transaction and is not on an interactive hot path
                assertThat(flags.timeout()).isBetween(Duration.ofSeconds(10), Duration.ofSeconds(90));
            } else {
                assertThat(flags.timeout()).isLessThan(Duration.ofSeconds(1));
            }
        }
    }
}
