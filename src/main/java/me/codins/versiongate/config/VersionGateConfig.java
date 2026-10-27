package me.codins.versiongate.config;

import com.velocitypowered.api.network.ProtocolVersion;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/**
 * Immutable view of the configuration the gate is enforcing.
 * Reloads publish a new instance through an {@code AtomicReference}.
 */
public final class VersionGateConfig {

    public static final int DEFAULT_MIN_PROTOCOL = 4;
    public static final int DEFAULT_MAX_PROTOCOL = 47;
    public static final String DEFAULT_KICK_MESSAGE =
            "<red>Your client <white><version></white> is not allowed on this network."
                    + "<newline><gray>Allowed versions: <white><allowed></white></gray>";
    public static final String DEFAULT_PING_LABEL = "Requires <allowed>";

    private final Mode mode;
    private final int minProtocol;
    private final int maxProtocol;
    private final ListType listType;
    private final Set<Integer> protocols;
    private final boolean blockUnknownVersions;
    private final String kickMessage;
    private final Set<String> bypassUsernames;
    private final boolean bypassPermissionEnabled;
    private final boolean logBlockedAttempts;
    private final boolean updateServerListPing;
    private final String pingVersionLabel;
    private final String allowedDescription;

    VersionGateConfig(
            Mode mode,
            int minProtocol,
            int maxProtocol,
            ListType listType,
            Set<Integer> protocols,
            boolean blockUnknownVersions,
            String kickMessage,
            Set<String> bypassUsernames,
            boolean bypassPermissionEnabled,
            boolean logBlockedAttempts,
            boolean updateServerListPing,
            String pingVersionLabel) {
        if (mode == Mode.RANGE && minProtocol > maxProtocol) {
            throw new IllegalArgumentException("minProtocol must be <= maxProtocol");
        }
        if (mode == Mode.LIST && protocols.isEmpty()) {
            throw new IllegalArgumentException("list mode requires at least one protocol");
        }
        this.mode = mode;
        this.minProtocol = minProtocol;
        this.maxProtocol = maxProtocol;
        this.listType = listType;
        this.protocols = Set.copyOf(protocols);
        this.blockUnknownVersions = blockUnknownVersions;
        this.kickMessage = kickMessage;
        this.bypassUsernames = Set.copyOf(bypassUsernames);
        this.bypassPermissionEnabled = bypassPermissionEnabled;
        this.logBlockedAttempts = logBlockedAttempts;
        this.updateServerListPing = updateServerListPing;
        this.pingVersionLabel = pingVersionLabel;
        this.allowedDescription = describeAllowed(mode, minProtocol, maxProtocol, listType, this.protocols);
    }

    public static VersionGateConfig defaults() {
        return new VersionGateConfig(
                Mode.RANGE,
                DEFAULT_MIN_PROTOCOL,
                DEFAULT_MAX_PROTOCOL,
                ListType.WHITELIST,
                Set.of(),
                true,
                DEFAULT_KICK_MESSAGE,
                Set.of(),
                false,
                true,
                true,
                DEFAULT_PING_LABEL);
    }

    /**
     * Unknown clients are decided before the range or list comparison.
     * Velocity reports an unrecognized handshake as {@link ProtocolVersion#UNKNOWN}.
     * In the 4.3 sources {@code UNKNOWN.getProtocol()} is -1, and {@code getProtocol()}
     * returns the snapshot id whenever the release protocol field is -1, so a check
     * that only compares against {@code max-protocol} would allow a brand-new client.
     * When {@code block-unknown-versions} is false, an unknown client is allowed even
     * though -1 sits outside the configured range. {@link ProtocolVersion#LEGACY}
     * (protocol -2) is a known special value and is compared like any other protocol.
     */
    public boolean isAllowed(ProtocolVersion version) {
        if (version == null || version.isUnknown() || version.getProtocol() == -1) {
            return !blockUnknownVersions;
        }
        int protocol = version.getProtocol();
        if (mode == Mode.RANGE) {
            return protocol >= minProtocol && protocol <= maxProtocol;
        }
        boolean listed = protocols.contains(protocol);
        return listType == ListType.WHITELIST ? listed : !listed;
    }

    public boolean isUsernameBypassed(String username) {
        if (username == null || username.isEmpty()) {
            return false;
        }
        return bypassUsernames.contains(username.toLowerCase(Locale.ROOT));
    }

    public Component kick(ProtocolVersion version) {
        String versionText = describeClient(version);
        try {
            return MiniMessage.miniMessage().deserialize(
                    kickMessage,
                    Placeholder.unparsed("version", versionText),
                    Placeholder.unparsed("allowed", allowedDescription));
        } catch (RuntimeException exception) {
            return Component.text("Your client " + versionText
                    + " is not allowed. Allowed versions: " + allowedDescription);
        }
    }

    public String pingLabel() {
        return pingVersionLabel.replace("<allowed>", allowedDescription);
    }

    public Mode getMode() {
        return mode;
    }

    public int getMinProtocol() {
        return minProtocol;
    }

    public int getMaxProtocol() {
        return maxProtocol;
    }

    public ListType getListType() {
        return listType;
    }

    public Set<Integer> getProtocols() {
        return protocols;
    }

    public boolean isBlockUnknownVersions() {
        return blockUnknownVersions;
    }

    public boolean isBypassPermissionEnabled() {
        return bypassPermissionEnabled;
    }

    public boolean isLogBlockedAttempts() {
        return logBlockedAttempts;
    }

    public boolean isUpdateServerListPing() {
        return updateServerListPing;
    }

    public String getAllowedDescription() {
        return allowedDescription;
    }

    /**
     * Resolves a readable name such as {@code 1.8.9} through
     * {@link ProtocolVersion#getVersionsSupportedBy()}. {@code getName()} is
     * deprecated in Velocity 4 because one protocol covers several releases.
     */
    static OptionalInt protocolFromName(String name) {
        String trimmed = name.trim();
        for (ProtocolVersion version : ProtocolVersion.values()) {
            if (version.isUnknown() || version.isLegacy()) {
                continue;
            }
            for (String supported : version.getVersionsSupportedBy()) {
                if (supported.equalsIgnoreCase(trimmed)) {
                    return OptionalInt.of(version.getProtocol());
                }
            }
        }
        return OptionalInt.empty();
    }

    public static String describeClient(ProtocolVersion version) {
        if (version == null || version.isUnknown() || version.getProtocol() == -1) {
            int protocol = version == null ? -1 : version.getProtocol();
            return "unknown (protocol " + protocol + ")";
        }
        return describeProtocol(version.getProtocol()) + " (protocol " + version.getProtocol() + ")";
    }

    private static String describeAllowed(
            Mode mode,
            int minProtocol,
            int maxProtocol,
            ListType listType,
            Set<Integer> protocols) {
        if (mode == Mode.RANGE) {
            String start = endpoint(minProtocol, true);
            String end = endpoint(maxProtocol, false);
            if (start.equals(end)) {
                return start;
            }
            return start + " - " + end;
        }
        String listed = protocols.stream()
                .sorted()
                .map(VersionGateConfig::describeProtocol)
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
        if (listType == ListType.BLACKLIST) {
            return "any version except " + listed;
        }
        return listed;
    }

    private static String describeProtocol(int protocol) {
        ProtocolVersion version = ProtocolVersion.getProtocolVersion(protocol);
        if (version.isUnknown()) {
            return Integer.toString(protocol);
        }
        String introduced = version.getVersionIntroducedIn();
        String latest = version.getMostRecentSupportedVersion();
        if (introduced.equals(latest)) {
            return introduced;
        }
        return introduced + "-" + latest;
    }

    private static String endpoint(int protocol, boolean introduced) {
        ProtocolVersion version = ProtocolVersion.getProtocolVersion(protocol);
        if (version.isUnknown()) {
            return Integer.toString(protocol);
        }
        return introduced ? version.getVersionIntroducedIn() : version.getMostRecentSupportedVersion();
    }

    public enum Mode {
        RANGE,
        LIST
    }

    public enum ListType {
        WHITELIST,
        BLACKLIST
    }
}
