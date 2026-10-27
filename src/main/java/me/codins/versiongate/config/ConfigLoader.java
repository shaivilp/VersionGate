package me.codins.versiongate.config;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import me.codins.versiongate.VersionGate;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Reads {@code config.yml} from the plugin data folder.
 * The default resource is copied on first run so its comments survive.
 * SnakeYAML is used only to read the file.
 */
public final class ConfigLoader {

    public LoadResult load(Path dataDirectory) throws ConfigException {
        Path file = dataDirectory.resolve("config.yml");
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException exception) {
            throw new ConfigException("Could not create the plugin data folder: " + exception.getMessage(), exception);
        }
        if (Files.notExists(file)) {
            copyDefault(file);
        }

        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ConfigException("Could not read config.yml: " + exception.getMessage(), exception);
        }

        Object parsed;
        try {
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            parsed = yaml.load(content);
        } catch (RuntimeException exception) {
            throw new ConfigException("Could not parse config.yml: " + exception.getMessage(), exception);
        }
        if (parsed == null) {
            throw new ConfigException("config.yml is empty.");
        }
        if (!(parsed instanceof Map<?, ?> raw)) {
            throw new ConfigException("config.yml must be a YAML mapping.");
        }
        return build(asMap(raw));
    }

    private static void copyDefault(Path target) throws ConfigException {
        try (InputStream input = VersionGate.class.getResourceAsStream("/config.yml")) {
            if (input == null) {
                throw new ConfigException("Default config.yml is missing from the plugin jar.");
            }
            Files.copy(input, target);
        } catch (IOException exception) {
            throw new ConfigException("Could not create config.yml: " + exception.getMessage(), exception);
        }
    }

    private static LoadResult build(Map<String, Object> map) {
        List<String> warnings = new ArrayList<>();

        VersionGateConfig.Mode mode = parseMode(map.get("mode"), warnings);
        Bounds bounds = parseBounds(map, mode, warnings);
        VersionGateConfig.ListType listType = parseListType(map.get("list-type"), warnings);
        Set<Integer> protocols = parseProtocols(map.get("protocols"), warnings);

        if (mode == VersionGateConfig.Mode.LIST && protocols.isEmpty()) {
            warnings.add("list mode has no protocols after resolving the list; falling back to range 4 through 47.");
            mode = VersionGateConfig.Mode.RANGE;
            bounds = new Bounds(VersionGateConfig.DEFAULT_MIN_PROTOCOL, VersionGateConfig.DEFAULT_MAX_PROTOCOL);
        }

        boolean blockUnknown = bool(map, "block-unknown-versions", true, warnings);
        String kickMessage = kickMessage(map.get("kick-message"), warnings);
        Set<String> bypassUsernames = bypassUsernames(map.get("bypass-usernames"), warnings);
        boolean bypassPermission = bool(map, "bypass-permission-enabled", false, warnings);
        boolean logBlocked = bool(map, "log-blocked-attempts", true, warnings);
        boolean updatePing = bool(map, "update-server-list-ping", true, warnings);
        String pingLabel = pingLabel(map.get("ping-version-label"), warnings);

        VersionGateConfig config = new VersionGateConfig(
                mode,
                bounds.min(),
                bounds.max(),
                listType,
                protocols,
                blockUnknown,
                kickMessage,
                bypassUsernames,
                bypassPermission,
                logBlocked,
                updatePing,
                pingLabel);
        return new LoadResult(config, warnings);
    }

    private static VersionGateConfig.Mode parseMode(Object value, List<String> warnings) {
        if (value == null) {
            warnings.add("mode is missing; using range.");
            return VersionGateConfig.Mode.RANGE;
        }
        if (value instanceof String text) {
            if (text.equalsIgnoreCase("range")) {
                return VersionGateConfig.Mode.RANGE;
            }
            if (text.equalsIgnoreCase("list")) {
                return VersionGateConfig.Mode.LIST;
            }
            warnings.add("mode '" + text + "' is not range or list; using range.");
            return VersionGateConfig.Mode.RANGE;
        }
        warnings.add("mode must be range or list; using range.");
        return VersionGateConfig.Mode.RANGE;
    }

    private static Bounds parseBounds(Map<String, Object> map, VersionGateConfig.Mode mode, List<String> warnings) {
        ProtocolParse min = parseProtocol(map.get("min-protocol"), "min-protocol");
        ProtocolParse max = parseProtocol(map.get("max-protocol"), "max-protocol");
        if (!min.ok()) {
            warnings.add(min.warning());
        }
        if (!max.ok()) {
            warnings.add(max.warning());
        }
        boolean listMode = mode == VersionGateConfig.Mode.LIST;
        if (min.ok() && max.ok() && min.protocol() > max.protocol()) {
            String reset = listMode
                    ? "reset to 4 and 47. List mode does not use that range."
                    : "using 4 through 47.";
            warnings.add("min-protocol (" + min.protocol() + ") is greater than max-protocol ("
                    + max.protocol() + "); " + reset);
            return new Bounds(VersionGateConfig.DEFAULT_MIN_PROTOCOL, VersionGateConfig.DEFAULT_MAX_PROTOCOL);
        }
        if (!min.ok() || !max.ok()) {
            if (listMode) {
                warnings.add("min-protocol and max-protocol were reset to 4 and 47. List mode does not use that range.");
            } else {
                warnings.add("Using protocols 4 through 47 because min-protocol and max-protocol must both resolve.");
            }
            return new Bounds(VersionGateConfig.DEFAULT_MIN_PROTOCOL, VersionGateConfig.DEFAULT_MAX_PROTOCOL);
        }
        return new Bounds(min.protocol(), max.protocol());
    }

    private static VersionGateConfig.ListType parseListType(Object value, List<String> warnings) {
        if (value == null) {
            warnings.add("list-type is missing; using whitelist.");
            return VersionGateConfig.ListType.WHITELIST;
        }
        if (value instanceof String text) {
            if (text.equalsIgnoreCase("whitelist")) {
                return VersionGateConfig.ListType.WHITELIST;
            }
            if (text.equalsIgnoreCase("blacklist")) {
                return VersionGateConfig.ListType.BLACKLIST;
            }
            warnings.add("list-type '" + text + "' is not whitelist or blacklist; using whitelist.");
            return VersionGateConfig.ListType.WHITELIST;
        }
        warnings.add("list-type must be whitelist or blacklist; using whitelist.");
        return VersionGateConfig.ListType.WHITELIST;
    }

    private static Set<Integer> parseProtocols(Object value, List<String> warnings) {
        if (value == null) {
            return Set.of();
        }
        if (!(value instanceof List<?> entries)) {
            warnings.add("protocols must be a list; ignoring it.");
            return Set.of();
        }
        Set<Integer> protocols = new LinkedHashSet<>();
        for (int index = 0; index < entries.size(); index++) {
            Object entry = entries.get(index);
            String label = "protocols[" + index + "]";
            ProtocolParse parsed = parseProtocol(entry, label);
            if (!parsed.ok()) {
                warnings.add(parsed.warning() + " That entry was skipped.");
                continue;
            }
            protocols.add(parsed.protocol());
        }
        return Set.copyOf(protocols);
    }

    private static ProtocolParse parseProtocol(Object value, String label) {
        if (value == null) {
            return ProtocolParse.failure(label + " is missing.");
        }
        if (value instanceof Double || value instanceof Float || value instanceof BigDecimal) {
            return ProtocolParse.failure(label + " value '" + value
                    + "' was read as a decimal. Quote version names (for example \"1.8\") or use a protocol number.");
        }
        if (value instanceof Number number) {
            long raw = number.longValue();
            if (raw > Integer.MAX_VALUE || raw < Integer.MIN_VALUE) {
                return ProtocolParse.failure(label + " is outside the range of a protocol number.");
            }
            return ProtocolParse.ok((int) raw);
        }
        if (value instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                return ProtocolParse.failure(label + " is blank.");
            }
            if (trimmed.matches("-?\\d+")) {
                try {
                    return ProtocolParse.ok(Integer.parseInt(trimmed));
                } catch (NumberFormatException exception) {
                    return ProtocolParse.failure(label + " is outside the range of a protocol number.");
                }
            }
            OptionalInt resolved = VersionGateConfig.protocolFromName(trimmed);
            if (resolved.isEmpty()) {
                return ProtocolParse.failure(label + ": could not resolve version name '" + trimmed + "'.");
            }
            return ProtocolParse.ok(resolved.getAsInt());
        }
        return ProtocolParse.failure(label + " must be a protocol number or a quoted version name.");
    }

    private static String kickMessage(Object value, List<String> warnings) {
        if (!(value instanceof String text) || text.isBlank()) {
            warnings.add("kick-message is missing or blank; using the default.");
            return VersionGateConfig.DEFAULT_KICK_MESSAGE;
        }
        try {
            MiniMessage.miniMessage().deserialize(
                    text,
                    Placeholder.unparsed("version", "version"),
                    Placeholder.unparsed("allowed", "allowed"));
            return text;
        } catch (RuntimeException exception) {
            warnings.add("kick-message is not valid MiniMessage (" + exception.getMessage() + "); using the default.");
            return VersionGateConfig.DEFAULT_KICK_MESSAGE;
        }
    }

    private static String pingLabel(Object value, List<String> warnings) {
        if (!(value instanceof String text) || text.isBlank()) {
            warnings.add("ping-version-label is missing or blank; using the default.");
            return VersionGateConfig.DEFAULT_PING_LABEL;
        }
        return text;
    }

    private static Set<String> bypassUsernames(Object value, List<String> warnings) {
        if (value == null) {
            return Set.of();
        }
        if (!(value instanceof List<?> entries)) {
            warnings.add("bypass-usernames must be a list; ignoring it.");
            return Set.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (Object entry : entries) {
            if (entry == null) {
                continue;
            }
            String name = String.valueOf(entry).trim();
            if (!name.isEmpty()) {
                names.add(name.toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(names);
    }

    private static boolean bool(Map<String, Object> map, String key, boolean fallback, List<String> warnings) {
        Object value = map.get(key);
        if (value == null) {
            warnings.add(key + " is missing; using " + fallback + ".");
            return fallback;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof String text) {
            if (text.equalsIgnoreCase("true")) {
                return true;
            }
            if (text.equalsIgnoreCase("false")) {
                return false;
            }
        }
        warnings.add(key + " must be true or false; using " + fallback + ".");
        return fallback;
    }

    private static Map<String, Object> asMap(Map<?, ?> raw) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null) {
                map.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return map;
    }

    public static final class LoadResult {
        private final VersionGateConfig config;
        private final List<String> warnings;

        LoadResult(VersionGateConfig config, List<String> warnings) {
            this.config = config;
            this.warnings = List.copyOf(warnings);
        }

        public VersionGateConfig config() {
            return config;
        }

        public List<String> warnings() {
            return warnings;
        }
    }

    private record Bounds(int min, int max) {
    }

    private record ProtocolParse(boolean ok, int protocol, String warning) {
        static ProtocolParse ok(int protocol) {
            return new ProtocolParse(true, protocol, "");
        }

        static ProtocolParse failure(String warning) {
            return new ProtocolParse(false, 0, warning);
        }
    }
}
