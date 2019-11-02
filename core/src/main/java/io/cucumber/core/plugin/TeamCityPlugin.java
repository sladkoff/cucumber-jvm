package io.cucumber.core.plugin;

import gherkin.AstBuilder;
import gherkin.Parser;
import gherkin.TokenMatcher;
import gherkin.ast.Feature;
import gherkin.ast.GherkinDocument;
import gherkin.ast.Scenario;
import gherkin.ast.ScenarioDefinition;
import gherkin.ast.ScenarioOutline;
import io.cucumber.plugin.EventListener;
import io.cucumber.plugin.StrictAware;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.HookTestStep;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.Status;
import io.cucumber.plugin.event.TestCase;
import io.cucumber.plugin.event.TestCaseFinished;
import io.cucumber.plugin.event.TestCaseStarted;
import io.cucumber.plugin.event.TestRunFinished;
import io.cucumber.plugin.event.TestRunStarted;
import io.cucumber.plugin.event.TestSourceRead;
import io.cucumber.plugin.event.TestStep;
import io.cucumber.plugin.event.TestStepFinished;
import io.cucumber.plugin.event.TestStepStarted;

import java.io.PrintStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class TeamCityPlugin implements EventListener, StrictAware {
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'hh:mm:ss.SSSZ");

    public static final String TEAMCITY_PREFIX = "##teamcity";
    public static final String TEMPLATE_TEST_RUN_STARTED = TEAMCITY_PREFIX + "[testSuiteStarted timestamp = '%s' name = 'Cucumber']";
    public static final String TEMPLATE_TEST_RUN_FINISHED = TEAMCITY_PREFIX + "[testSuiteFinished timestamp = '%s' name = 'Cucumber']";

    public static final String TEMPLATE_ENTER_THE_MATRIX = TEAMCITY_PREFIX + "[enteredTheMatrix timestamp = '%s']";
    public static final String TEMPLATE_TEST_SUITE_STARTED = TEAMCITY_PREFIX + "[testSuiteStarted timestamp = '%s' locationHint = '%s' name = '%s']";
    public static final String TEMPLATE_TEST_SUITE_FINISHED = TEAMCITY_PREFIX + "[testSuiteFinished timestamp = '%s' name = '%s']";

    public static final String FILE_RESOURCE_PREFIX = "file://";

    public static final String TEMPLATE_TEST_STARTED = TEAMCITY_PREFIX + "[testStarted timestamp = '%s' locationHint = '%s' captureStandardOutput = 'true' name = '%s']";
    public static final String TEMPLATE_TEST_FINISHED = TEAMCITY_PREFIX + "[testFinished timestamp = '%s' duration = '%s' name = '%s']";
    public static final String TEMPLATE_TEST_FAILED = TEAMCITY_PREFIX + "[testFailed timestamp = '%s' duration = '%s' details = '%s' message = '%s' name = '%s' %s]";
    public static final String TEMPLATE_TEST_PENDING = TEAMCITY_PREFIX + "[testIgnored timestamp = '%s' message = 'Skipped step' name = '%s' ]";


    public static final String TEMPLATE_COMPARISON_TEST_FAILED = TEAMCITY_PREFIX + "[testFailed timestamp = '%s' details = '%s' message = '%s' expected='%s' actual='%s' name = '%s' %s]";

    public static final String TEMPLATE_SCENARIO_FAILED = TEAMCITY_PREFIX + "[customProgressStatus timestamp='%s' type='testFailed']";


    public static final String TEMPLATE_SCENARIO_COUNTING_STARTED =
        TEAMCITY_PREFIX + "[customProgressStatus testsCategory = 'Scenarios' count = '%s' timestamp = '%s']";
    public static final String TEMPLATE_SCENARIO_COUNTING_FINISHED =
        TEAMCITY_PREFIX + "[customProgressStatus testsCategory = '' count = '0' timestamp = '%s']";
//    public static final String TEMPLATE_SCENARIO_STARTED = TEAMCITY_PREFIX + "[customProgressStatus type = 'testStarted' timestamp = '%s']";
//    public static final String TEMPLATE_SCENARIO_FINISHED = TEAMCITY_PREFIX + "[customProgressStatus type = 'testFinished' timestamp = '%s']";

    private final PrintStream out = System.out;
    private final Map<URI, GherkinDocument> sources = new HashMap<>();
    private URI currentFeature;
    private boolean strict = false;
    private ScenarioLine currentScenario;
    private ScenarioLine currentExamples;

    private static String escapeCommand(String command, Object... parameters) {
        String[] escapedParameters = new String[parameters.length];
        for (int i = 0; i < escapedParameters.length; i++) {
            escapedParameters[i] = escape(parameters[i].toString());
        }

        return String.format(command, escapedParameters);
    }

    private static String escape(String source) {
        if (source == null) {
            return "";
        }
        return source
            .replace("|", "||")
            .replace("\n", "|n")
            .replace("\r", "|r")
            .replace("'", "|'");
    }

    @Override
    public void setEventPublisher(EventPublisher publisher) {

        publisher.registerHandlerFor(TestSourceRead.class, event -> {
            Parser<GherkinDocument> parser = new Parser<>(new AstBuilder());
            TokenMatcher matcher = new TokenMatcher();
            GherkinDocument gherkinDocument = parser.parse(event.getSource(), matcher);
            sources.put(event.getUri(), gherkinDocument);
        });

        publisher.registerHandlerFor(TestRunStarted.class, this::printTestRunStarted);

        publisher.registerHandlerFor(TestCaseStarted.class, event -> {
            Instant instant = event.getInstant();
            if (currentScenario != null) {
                printScenarioFinished(instant);
            }

            TestCase testCase = event.getTestCase();
            URI uri = testCase.getUri();
            if (!Objects.equals(uri, currentFeature)) {
                if (currentFeature != null) {
                    printFeatureFinished(event.getInstant());
                }
                currentFeature = uri;
                printFeatureStarted(event);
            }

            ScenarioLine scenarioId = new ScenarioLine(testCase.getUri(), testCase.getLine());
            if (!Objects.equals(scenarioId, currentExamples)) {
                currentExamples = scenarioId;
                printExamplesStarted(instant);
            }

            if (!Objects.equals(scenarioId, currentScenario)) {
                currentScenario = scenarioId;
                printScenarioStarted(instant);
            }

        });

        publisher.registerHandlerFor(TestStepStarted.class, this::printTestStepStarted);
        publisher.registerHandlerFor(TestStepFinished.class, this::printTestStepFinished);
        publisher.registerHandlerFor(TestCaseFinished.class, this::printTestCaseFinished);

        publisher.registerHandlerFor(TestRunFinished.class, event -> {
            if (currentScenario != null) {
                printScenarioFinished(event.getInstant());
            }
            if (currentFeature != null) {
                printFeatureFinished(event.getInstant());
            }
            printTestRunFinished(event);
        });

    }

    private void printTestRunFinished(TestRunFinished event) {
        ZonedDateTime date = event.getInstant().atZone(ZoneOffset.UTC);
        print(TEMPLATE_TEST_RUN_FINISHED, DATE_FORMAT.format(date));
    }

    private void printTestCaseFinished(TestCaseFinished event) {
        ZonedDateTime date = event.getInstant().atZone(ZoneOffset.UTC);
        Duration duration = event.getResult().getDuration();
        String scenario = event.getTestCase().getName();
        Throwable error = event.getResult().getError();
        Status status = event.getResult().getStatus();
        if (status.is(Status.PENDING)) {
            print(TEMPLATE_TEST_PENDING, DATE_FORMAT.format(date), duration.toMillis(), scenario);
        } else if (!status.isOk(strict) || error != null) {
            String message = error.getMessage();
            print(TEMPLATE_TEST_FAILED, DATE_FORMAT.format(date), duration.toMillis(), message, scenario);
        }
        print(TEMPLATE_TEST_SUITE_FINISHED, DATE_FORMAT.format(date), duration.toMillis(), scenario);
    }

    private void printTestStepFinished(TestStepFinished event) {
        ZonedDateTime date = event.getInstant().atZone(ZoneOffset.UTC);
        URI uri = event.getTestCase().getUri();
        Duration duration = event.getResult().getDuration();
        Throwable error = event.getResult().getError();
        Status status = event.getResult().getStatus();
        String step = getStepText(event.getTestStep());

        if (status.is(Status.PENDING)) {
            print(TEMPLATE_TEST_PENDING, DATE_FORMAT.format(date), duration.toMillis(), step);
        } else if (!status.isOk(strict) || error != null) {
            String message = error.getMessage();
            print(TEMPLATE_TEST_FAILED, DATE_FORMAT.format(date), duration.toMillis(), message, step);
        }
        print(TEMPLATE_TEST_FINISHED, DATE_FORMAT.format(date), uri, step);
    }

    private void printTestStepStarted(TestStepStarted event) {
        ZonedDateTime date = event.getInstant().atZone(ZoneOffset.UTC);
        URI uri = event.getTestCase().getUri();
        String step = getStepText(event.getTestStep());
        print(TEMPLATE_TEST_STARTED, DATE_FORMAT.format(date), uri, step);
    }

    private void printTestRunStarted(TestRunStarted event) {
        ZonedDateTime date = event.getInstant().atZone(ZoneOffset.UTC);
        print(TEMPLATE_ENTER_THE_MATRIX, DATE_FORMAT.format(date));
        print(TEMPLATE_TEST_RUN_STARTED, DATE_FORMAT.format(date));
    }

    private void printExamplesStarted(Instant instant) {
        ZonedDateTime date = instant.atZone(ZoneOffset.UTC);
        print(TEMPLATE_TEST_SUITE_STARTED, DATE_FORMAT.format(date), currentExamples.uri, "Examples:");
    }

    private void printScenarioStarted(Instant instant) {
        ZonedDateTime date = instant.atZone(ZoneOffset.UTC);
        print(TEMPLATE_TEST_SUITE_STARTED, DATE_FORMAT.format(date), currentScenario.uri, scenarioName(currentScenario));
    }

    private void printScenarioFinished(Instant instant) {
        ZonedDateTime date = instant.atZone(ZoneOffset.UTC);
        print(TEMPLATE_TEST_SUITE_FINISHED, DATE_FORMAT.format(date), scenarioName(currentScenario));
    }

    private void printFeatureStarted(TestCaseStarted event) {
        ZonedDateTime date = event.getInstant().atZone(ZoneOffset.UTC);
        URI uri = event.getTestCase().getUri();
        Feature feature = sources.get(uri).getFeature();
        print(TEMPLATE_TEST_SUITE_STARTED, DATE_FORMAT.format(date), uri, feature.getName());
    }

    private void printFeatureFinished(Instant instant) {
        ZonedDateTime date = instant.atZone(ZoneOffset.UTC);
        Feature feature = sources.get(currentFeature).getFeature();
        print(TEMPLATE_TEST_SUITE_FINISHED, DATE_FORMAT.format(date), feature.getName());
    }


    private String getStepText(TestStep testStep) {
        if (testStep instanceof PickleStepTestStep) {
            //TODO:
            PickleStepTestStep pickleStepTestStep = (PickleStepTestStep) testStep;
            pickleStepTestStep.getStepLine();

            return testStep.getCodeLocation() == null ? "null" : testStep.getCodeLocation();
        } else {
            HookTestStep hookTestStep = (HookTestStep) testStep;
            //TODO:
            return "Hook " + hookTestStep.getHookType();
        }
    }

    private String scenarioName(ScenarioLine previousScenarioStarted) {
        GherkinDocument gherkinDocument = sources.get(previousScenarioStarted.uri);
        return gherkinDocument.getFeature()
            .getChildren()
            .stream()
            .filter(scenarioDefinition -> containsLine(scenarioDefinition, previousScenarioStarted.line))
            .map(ScenarioDefinition::getName)
            .filter(s -> !s.isEmpty()) //TODO: Check empty name or null?
            .findFirst()
            .orElse("Nameless");
    }

    private boolean containsLine(ScenarioDefinition scenarioDefinition, int line) {
        if (scenarioDefinition instanceof ScenarioOutline) {
            ScenarioOutline scenarioOutline = (ScenarioOutline) scenarioDefinition;
            return scenarioOutline.getExamples()
                .stream()
                .flatMap(example -> example.getTableBody().stream())
                .anyMatch(tableRow -> tableRow.getLocation().getLine() == line);
        } else if (scenarioDefinition instanceof Scenario) {
            Scenario scenario = (Scenario) scenarioDefinition;
            return scenario.getLocation().getLine() == line;
        }
        return false;
    }

    private void print(String template, Object... args) {
        out.println(escapeCommand(template, args));
    }

    @Override
    public void setStrict(boolean strict) {
        this.strict = strict;
    }

    private static class ScenarioLine {
        final URI uri;
        final int line;

        private ScenarioLine(URI uri, int line) {
            this.uri = uri;
            this.line = line;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ScenarioLine that = (ScenarioLine) o;
            return line == that.line && uri.equals(that.uri);
        }

        @Override
        public int hashCode() {
            return Objects.hash(uri, line);
        }
    }
}
