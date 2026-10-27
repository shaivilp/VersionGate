package me.codins.versiongate.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.codins.versiongate.VersionGate;
import me.codins.versiongate.config.VersionGateConfig;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/**
 * {@code SimpleCommand} is the current non-deprecated command API in Velocity 4.3.
 * Brigadier is also available; two subcommands do not need it.
 */
public final class VersionGateCommand implements SimpleCommand {

    private final VersionGate plugin;

    public VersionGateCommand(VersionGate plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if (args.length == 0) {
            source.sendRichMessage("<gray>Usage: <white>/versiongate reload</white> or <white>/versiongate info");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(source);
            case "info" -> info(source);
            default -> source.sendRichMessage("<red>Unknown subcommand. <gray>Usage: <white>/versiongate reload</white> or <white>/versiongate info");
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        CommandSource source = invocation.source();
        return source.hasPermission(VersionGate.PERMISSION_RELOAD)
                || source.hasPermission(VersionGate.PERMISSION_INFO);
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length > 1) {
            return List.of();
        }
        String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        CommandSource source = invocation.source();
        List<String> suggestions = new ArrayList<>(2);
        if (source.hasPermission(VersionGate.PERMISSION_RELOAD) && "reload".startsWith(prefix)) {
            suggestions.add("reload");
        }
        if (source.hasPermission(VersionGate.PERMISSION_INFO) && "info".startsWith(prefix)) {
            suggestions.add("info");
        }
        return suggestions;
    }

    private void reload(CommandSource source) {
        if (!source.hasPermission(VersionGate.PERMISSION_RELOAD)) {
            source.sendRichMessage("<red>You do not have permission to reload VersionGate.");
            return;
        }
        VersionGate.ReloadResult result = plugin.reload();
        if (!result.applied()) {
            source.sendRichMessage("<red>Reload failed. The previous configuration is still active.");
            source.sendPlainMessage(result.error());
            return;
        }
        if (result.warnings().isEmpty()) {
            source.sendRichMessage("<green>VersionGate configuration reloaded.");
        } else {
            source.sendRichMessage("<yellow>VersionGate configuration reloaded with warnings. The corrected configuration is active.");
            for (String warning : result.warnings()) {
                source.sendPlainMessage(warning);
            }
        }
        sendAllowed(source, result.config());
    }

    private void info(CommandSource source) {
        if (!source.hasPermission(VersionGate.PERMISSION_INFO)) {
            source.sendRichMessage("<red>You do not have permission to view VersionGate info.");
            return;
        }
        VersionGateConfig config = plugin.config();
        source.sendRichMessage(
                "<gray>Mode: <white><mode>",
                Placeholder.unparsed("mode", config.getMode().name().toLowerCase(Locale.ROOT)));
        if (config.getMode() == VersionGateConfig.Mode.RANGE) {
            source.sendRichMessage(
                    "<gray>Protocols: <white><min> through <max>",
                    Placeholder.unparsed("min", Integer.toString(config.getMinProtocol())),
                    Placeholder.unparsed("max", Integer.toString(config.getMaxProtocol())));
        } else {
            source.sendRichMessage(
                    "<gray>List type: <white><type>",
                    Placeholder.unparsed("type", config.getListType().name().toLowerCase(Locale.ROOT)));
            String listed = config.getProtocols().stream()
                    .sorted()
                    .map(String::valueOf)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("(none)");
            source.sendRichMessage(
                    "<gray>Protocols: <white><protocols>",
                    Placeholder.unparsed("protocols", listed));
        }
        sendAllowed(source, config);
        source.sendRichMessage(
                "<gray>Block unknown versions: <white><value>",
                Placeholder.unparsed("value", Boolean.toString(config.isBlockUnknownVersions())));
        source.sendRichMessage(
                "<gray>Permission bypass: <white><value>",
                Placeholder.unparsed("value", config.isBypassPermissionEnabled() ? "enabled" : "disabled"));
    }

    private static void sendAllowed(CommandSource source, VersionGateConfig config) {
        source.sendRichMessage(
                "<gray>Allowed: <white><allowed>",
                Placeholder.unparsed("allowed", config.getAllowedDescription()));
    }
}
