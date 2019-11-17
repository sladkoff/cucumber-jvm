package io.cucumber.jupiter.engine;

import io.cucumber.core.resource.ClassFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.ConfigurationParameters;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.TestTag;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.CompositeTestSource;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static io.cucumber.core.resource.ClasspathSupport.CLASSPATH_SCHEME_PREFIX;
import static io.cucumber.jupiter.engine.FeatureResolver.createFeatureResolver;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsIterableContaining.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.platform.engine.TestDescriptor.Type.CONTAINER;
import static org.junit.platform.engine.TestDescriptor.Type.TEST;
import static org.junit.platform.engine.TestTag.create;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClasspathResource;
import static org.junit.platform.engine.support.descriptor.ClasspathResourceSource.from;
import static org.junit.platform.engine.support.descriptor.FilePosition.from;

class FeatureResolverTest {
    private final String featurePath = "io/cucumber/jupiter/engine/feature-with-outline.feature";
    private final String featureSegmentValue = CLASSPATH_SCHEME_PREFIX + featurePath;
    private TestDescriptor testDescriptor;
    private UniqueId id;

    @BeforeEach
    void before() {
        CucumberTestEngine engine = new CucumberTestEngine();
        ConfigurationParameters configuration = new EmptyConfigurationParameters();
        EngineDiscoveryRequest discoveryRequest = new EmptyEngineDiscoveryRequest(configuration);
        id = UniqueId.forEngine(engine.getId());
        testDescriptor = engine.discover(discoveryRequest, id);
        FeatureResolver featureResolver = createFeatureResolver(testDescriptor, ClassFilter.of(aClass -> true, aClass -> true));
        featureResolver.resolveClasspathResource(selectClasspathResource(featurePath));
    }

    @Test
    void feature() {
        TestDescriptor feature = getFeature();
        assertEquals("A feature with scenario outlines", feature.getDisplayName());
        assertEquals(emptySet(), feature.getTags());
        assertThat(getSource(feature), hasItems(from(featurePath, from(2, 1))));
        assertEquals(CONTAINER, feature.getType());
        assertEquals(
            id.append("feature", featureSegmentValue),
            feature.getUniqueId()
        );
    }

    @Test
    void scenario() {
        TestDescriptor scenario = getScenario();
        assertEquals("A scenario", scenario.getDisplayName());
        assertEquals(
            asSet(create("@FeatureTag"), create("@ScenarioTag")),
            scenario.getTags()
        );
        assertThat(getSource(scenario), hasItems(from(featurePath, from(5, 3))));

        assertEquals(TEST, scenario.getType());
        assertEquals(
            id.append("feature", featureSegmentValue)
                .append("scenario", "5"),
            scenario.getUniqueId()
        );

        PickleDescriptor pickleDescriptor = (PickleDescriptor) scenario;
        assertEquals(Optional.of("io.cucumber.jupiter.engine"), pickleDescriptor.getPackage());
    }

    private List<TestSource> getSource(TestDescriptor scenario) {
        return scenario.getSource()
            .map(CompositeTestSource.class::cast)
            .map(CompositeTestSource::getSources)
            .orElse(emptyList());
    }

    @Test
    void outline() {
        TestDescriptor outline = getOutline();
        assertEquals("A scenario outline", outline.getDisplayName());
        assertEquals(
            emptySet(),
            outline.getTags()
        );
        assertThat(getSource(outline), hasItems(from(featurePath, from(11, 3))));
        assertEquals(CONTAINER, outline.getType());
        assertEquals(
            id.append("feature", featureSegmentValue)
                .append("outline", "11"),
            outline.getUniqueId()
        );
    }

    @Test
    void example() {
        TestDescriptor example = getExample();
        assertEquals("Example #1", example.getDisplayName());
        assertEquals(
            asSet(create("@FeatureTag"), create("@Example1Tag"), create("@ScenarioOutlineTag")),
            example.getTags()
        );
        assertThat(getSource(example), hasItems(from(featurePath, from(19, 9))));
        assertEquals(TEST, example.getType());

        assertEquals(
            id.append("feature", featureSegmentValue)
                .append("outline", "11")
                .append("examples", "17")
                .append("example", "19"),
            example.getUniqueId()
        );

        PickleDescriptor pickleDescriptor = (PickleDescriptor) example;
        assertEquals(Optional.of("io.cucumber.jupiter.engine"), pickleDescriptor.getPackage());
    }

    private Set<TestTag> asSet(TestTag... tags) {
        return new HashSet<>(asList(tags));
    }

    private TestDescriptor getFeature() {
        Set<? extends TestDescriptor> features = testDescriptor.getChildren();
        return features.iterator().next();
    }

    private TestDescriptor getScenario() {
        return getFeature().getChildren().iterator().next();
    }

    private TestDescriptor getOutline() {
        Iterator<? extends TestDescriptor> iterator = getFeature().getChildren().iterator();
        iterator.next();
        return iterator.next();
    }

    private TestDescriptor getExample() {
        return getOutline().getChildren().iterator().next().getChildren().iterator().next();
    }
}