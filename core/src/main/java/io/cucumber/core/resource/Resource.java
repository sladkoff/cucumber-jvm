package io.cucumber.core.resource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;

/**
 * Minimal representation of a resource e.g. a feature file.
 */
public interface Resource {

    /**
     * Returns a uri representing this resource.
     * <p>
     * Resources on the classpath will have the form
     * {@code classpath:com/example.feature} while resources on the file
     * system will have the form {@code file:/path/to/example.feature}. Other
     * resources will be represented by their exact uri.
     *
     * @return a uri representing this resource
     */
    URI getUri();

    /**
     * Returns a path to this resource.
     * <p>
     * For class path resources the path differs from the URI in that it
     * provides a references to the actual file location rather then the
     * location on the classpath.
     * <p>
     * Note that underlying file system may not be open when used outside of the
     * {@code ResourceScanner.loadResource} call back.
     *
     * @return a path to this resource
     */
    Path getPath();

    /**
     * An input stream to read this resource.
     * <p>
     * Note that underlying file system may not be open when used outside of the
     * {@code ResourceScanner.loadResource} call back.
     *
     * @return input stream to the resource
     * @throws IOException when the underlying file system was closed
     */
    InputStream getInputStream() throws IOException;

}
