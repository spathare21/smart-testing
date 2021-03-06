package org.arquillian.smart.testing.mvn.ext.dependencies;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import org.apache.maven.model.Dependency;

/**
 * Resolves dependencies for strategies defined by keywords (e.g. new, changed, affected)
 *
 * For example one can defined mapping between "awesome" strategy to its corresponding dependency as follows:
 *
 * smart.testing.strategy.awesome=org.awesome:smart-testing-awesome:0.0.1
 *
 * Version in the g:a:v can be omitted and will be resolved to the one matching "Smart Testing Extension" which is in use.
 *
 * Properties are loaded with the following precedence:
 *
 *  - default ones stored internally are loaded first
 *  - custom file can overwrite defaults
 *  - System properties overwrite all above
 */
class StrategyDependencyResolver {

    private static final String SMART_TESTING_STRATEGY_PREFIX = "smart.testing.strategy.";

    private Path propertiesPath; // TODO this could be configurable through system property and with this we need a path

    StrategyDependencyResolver(Path propertiesPath) {
        this.propertiesPath = propertiesPath;
    }

    StrategyDependencyResolver() {
        this.propertiesPath = null;
    }

    Map<String, Dependency> resolveDependencies() {
        final Properties properties = new Properties();
        properties.putAll(loadDefaultMapping());
        properties.putAll(loadFromFile());
        properties.putAll(System.getProperties());
        return transformToDependencies(properties);
    }

    private Properties loadDefaultMapping() {
        final Properties properties = new Properties();
        try (InputStream strategyMapping = getClass().getClassLoader().getResourceAsStream("strategies.properties")) {
            if (strategyMapping == null) {
                throw new IllegalStateException("Unable to load default strategy dependencies mapping.");
            }
            properties.load(strategyMapping);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load default strategy dependencies mapping.", e);
        }
        return properties;
    }

    private Properties loadFromFile() {
        final Properties properties = new Properties();
        if (propertiesPath != null) {
            try {
                properties.load(new FileInputStream(this.propertiesPath.toFile()));
            } catch (IOException e) {
                throw new RuntimeException("Unable to load custom strategy mapping", e);
            }
        }

        return properties;
    }

    private Map<String, Dependency> transformToDependencies(Properties properties) {
        return properties
            .stringPropertyNames()
            .stream()
            .filter(key -> key.startsWith(SMART_TESTING_STRATEGY_PREFIX))
            .map(String::valueOf)
            .collect(Collectors.toMap(key -> key.substring(key.lastIndexOf('.') + 1),
                key -> {
                    final String[] gav = ((String) properties.get(key)).split(":");
                    final Dependency dependency = new Dependency();
                    dependency.setGroupId(gav[0]);
                    dependency.setArtifactId(gav[1]);
                    String version = ExtensionVersion.version().toString();
                    if (gav.length == 3) {
                        version = gav[2];
                    }
                    dependency.setVersion(version);
                    dependency.setScope("runtime");
                    return dependency;
                }));
    }
}
