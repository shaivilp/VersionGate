package me.codins.versiongate.config;

/**
 * The configuration file could not be read or parsed.
 * Field-level problems are warnings on a {@link ConfigLoader.LoadResult}, not this exception.
 */
public final class ConfigException extends Exception {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
