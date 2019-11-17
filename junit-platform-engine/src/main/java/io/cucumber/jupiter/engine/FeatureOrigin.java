package io.cucumber.jupiter.engine;

import gherkin.ast.Examples;
import gherkin.ast.Feature;
import gherkin.ast.Location;
import gherkin.ast.Node;
import gherkin.ast.ScenarioOutline;
import gherkin.ast.TableCell;
import io.cucumber.core.feature.CucumberFeature;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.ClasspathResourceSource;
import org.junit.platform.engine.support.descriptor.FilePosition;
import org.junit.platform.engine.support.descriptor.FileSource;
import org.junit.platform.engine.support.descriptor.UriSource;

import java.net.URI;

import static java.util.Arrays.asList;
import static org.junit.platform.engine.support.descriptor.ClasspathResourceSource.CLASSPATH_SCHEME;
import static org.junit.platform.engine.support.descriptor.ClasspathResourceSource.from;
import static org.junit.platform.engine.support.descriptor.CompositeTestSource.from;
import static org.junit.platform.engine.support.descriptor.FileSource.from;

abstract class FeatureOrigin {

    static final String FEATURE_SEGMENT_TYPE = "feature";
    private static final String SCENARIO_SEGMENT_TYPE = "scenario";
    private static final String OUTLINE_SEGMENT_TYPE = "outline";
    private static final String EXAMPLES_SEGMENT_TYPE = "examples";
    private static final String EXAMPLE_SEGMENT_TYPE = "example";

    private static FilePosition createFilePosition(Location location) {
        return FilePosition.from(location.getLine(), location.getColumn());
    }

    // TODO: IDEA clicks through to location of parent node
    // TODO: FileSource is in target directory
    static FeatureOrigin create(CucumberFeature feature) {
        Feature gherkinFeature = feature.getGherkinFeature();
        Location location = gherkinFeature.getLocation();

        FeatureOrigin featureOrigin = create(feature.getUri(), location);
        FeatureOrigin pathOrigin = create(feature.getPath().toUri(), location);

        return new CompositeFeatureOrigin(featureOrigin, pathOrigin);
    }

    private static FeatureOrigin create(URI uri, Location location) {
        FilePosition filePosition = FilePosition.from(location.getLine(), location.getColumn());

        // ClasspathResourceSource.from expects all resources to start with /
        if (CLASSPATH_SCHEME.equals(uri.getScheme())) {
            String classPathResource = uri.getSchemeSpecificPart();
            if (!classPathResource.startsWith("/")) {
                classPathResource = "/" + classPathResource;
            }
            return new ClasspathFeatureOrigin(from(classPathResource, filePosition));
        }

        UriSource source = UriSource.from(uri);
        if (source instanceof FileSource) {
            FileSource fileSource = (FileSource) source;
            return new FileFeatureOrigin(from(fileSource.getFile(), filePosition));
        }

        return new UriFeatureOrigin(source);
    }

    static boolean isFeatureSegment(UniqueId.Segment segment) {
        return FEATURE_SEGMENT_TYPE.equals(segment.getType());
    }

    abstract TestSource featureSource();

    abstract TestSource nodeSource(Node scenarioDefinition);

    abstract UniqueId featureSegment(UniqueId parent, CucumberFeature feature);

    UniqueId scenarioSegment(UniqueId parent, Node scenarioDefinition) {
        return parent.append(SCENARIO_SEGMENT_TYPE, String.valueOf(scenarioDefinition.getLocation().getLine()));
    }

    UniqueId outlineSegment(UniqueId parent, ScenarioOutline scenarioOutline) {
        return parent.append(OUTLINE_SEGMENT_TYPE, String.valueOf(scenarioOutline.getLocation().getLine()));
    }

    UniqueId examplesSegment(UniqueId parent, Examples examples) {
        return parent.append(EXAMPLES_SEGMENT_TYPE, String.valueOf(examples.getLocation().getLine()));
    }

    UniqueId exampleSegment(UniqueId parent, TableCell firstCell) {
        Location location = firstCell.getLocation();
        return parent.append(EXAMPLE_SEGMENT_TYPE, String.valueOf(location.getLine()));
    }

    private static class FileFeatureOrigin extends FeatureOrigin {

        private final FileSource source;

        FileFeatureOrigin(FileSource source) {
            this.source = source;
        }

        @Override
        TestSource featureSource() {
            return source;
        }

        @Override
        TestSource nodeSource(Node node) {
            return from(source.getFile(), createFilePosition(node.getLocation()));
        }

        @Override
        UniqueId featureSegment(UniqueId parent, CucumberFeature feature) {
            return parent.append(FEATURE_SEGMENT_TYPE, source.getUri().toString());
        }
    }

    private static class UriFeatureOrigin extends FeatureOrigin {

        private final UriSource source;

        UriFeatureOrigin(UriSource source) {
            this.source = source;
        }

        @Override
        TestSource featureSource() {
            return source;
        }

        @Override
        TestSource nodeSource(Node node) {
            return source;
        }

        @Override
        UniqueId featureSegment(UniqueId parent, CucumberFeature feature) {
            return parent.append(FEATURE_SEGMENT_TYPE, source.getUri().toString());
        }
    }

    private static class ClasspathFeatureOrigin extends FeatureOrigin {

        private final ClasspathResourceSource source;

        ClasspathFeatureOrigin(ClasspathResourceSource source) {
            this.source = source;
        }

        @Override
        TestSource featureSource() {
            return source;
        }

        @Override
        TestSource nodeSource(Node node) {
            return from(source.getClasspathResourceName(), createFilePosition(node.getLocation()));
        }

        @Override
        UniqueId featureSegment(UniqueId parent, CucumberFeature feature) {
            return parent.append(FEATURE_SEGMENT_TYPE, feature.getUri().toString());
        }
    }

    private static class CompositeFeatureOrigin extends FeatureOrigin {

        private final FeatureOrigin featureOrigin;
        private final FeatureOrigin pathOrigin;

        private CompositeFeatureOrigin(FeatureOrigin featureOrigin, FeatureOrigin pathOrigin) {
            this.featureOrigin = featureOrigin;
            this.pathOrigin = pathOrigin;
        }

        @Override
        TestSource featureSource() {
            return from(asList(featureOrigin.featureSource(), pathOrigin.featureSource()));
        }

        @Override
        TestSource nodeSource(Node scenarioDefinition) {
            return from(asList(featureOrigin.nodeSource(scenarioDefinition), pathOrigin.nodeSource(scenarioDefinition)));
        }

        @Override
        UniqueId featureSegment(UniqueId parent, CucumberFeature feature) {
            return featureOrigin.featureSegment(parent, feature);
        }
    }
}
