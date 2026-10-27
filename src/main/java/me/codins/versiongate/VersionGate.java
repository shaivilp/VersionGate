package me.codins.versiongate;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import me.codins.versiongate.command.VersionGateCommand;
import me.codins.versiongate.config.ConfigException;
import me.codins.versiongate.config.ConfigLoader;
import me.codins.versiongate.config.VersionGateConfig;
import me.codins.versiongate.listener.GateListener;
import org.slf4j.Logger;

@Plugin(
        id = "versiongate",
        name = "VersionGate",
        version = "1.0.0",
        description = "Restricts which Minecraft client versions can connect through the proxy.",
        authors = {"codins"}
)
public final class VersionGate {

    public static final String PERMISSION_BYPASS = "versiongate.bypass";
    public static final String PERMISSION_RELOAD = "versiongate.reload";
    public static final String PERMISSION_INFO = "versiongate.info";

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final AtomicReference<VersionGateConfig> config = new AtomicReference<>();
    private final ConfigLoader configLoader = new ConfigLoader();

    @Inject
    public VersionGate(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        try {
            apply(configLoader.load(dataDirectory), true);
        } catch (ConfigException exception) {
            logger.warn(
                    "Could not load config.yml ({}); using safe defaults (protocols 4 through 47, unknown versions blocked).",
                    exception.getMessage());
            config.set(VersionGateConfig.defaults());
            logger.info("VersionGate loaded in range mode. Allowed: {}.", config.get().getAllowedDescription());
        }
    }

    /**
     * Velocity registers this plugin instance as a listener. The gate listener
     * and the command are registered here, after the proxy has finished loading plugins.
     */
    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        server.getEventManager().register(this, new GateListener(this));
        CommandManager commands = server.getCommandManager();
        commands.register(
                commands.metaBuilder("versiongate").plugin(this).build(),
                new VersionGateCommand(this));
    }

    public VersionGateConfig config() {
        return config.get();
    }

    public Logger logger() {
        return logger;
    }

    /**
     * @return the outcome of the reload. A parse failure leaves the previous snapshot in place.
     */
    public ReloadResult reload() {
        try {
            ConfigLoader.LoadResult loaded = configLoader.load(dataDirectory);
            apply(loaded, false);
            return ReloadResult.applied(loaded.warnings(), loaded.config());
        } catch (ConfigException exception) {
            logger.warn("Config reload failed; keeping the previous configuration: {}", exception.getMessage());
            return ReloadResult.rejected(exception.getMessage());
        }
    }

    private void apply(ConfigLoader.LoadResult loaded, boolean startup) {
        for (String warning : loaded.warnings()) {
            logger.warn(warning);
        }
        config.set(loaded.config());
        String when = startup ? "loaded" : "reloaded";
        logger.info(
                "VersionGate {} in {} mode. Allowed: {}.",
                when,
                loaded.config().getMode().name().toLowerCase(java.util.Locale.ROOT),
                loaded.config().getAllowedDescription());
    }

    public static final class ReloadResult {
        private final boolean applied;
        private final String error;
        private final List<String> warnings;
        private final VersionGateConfig config;

        private ReloadResult(boolean applied, String error, List<String> warnings, VersionGateConfig config) {
            this.applied = applied;
            this.error = error;
            this.warnings = warnings;
            this.config = config;
        }

        static ReloadResult applied(List<String> warnings, VersionGateConfig config) {
            return new ReloadResult(true, "", List.copyOf(warnings), config);
        }

        static ReloadResult rejected(String error) {
            return new ReloadResult(false, error, List.of(), null);
        }

        public boolean applied() {
            return applied;
        }

        public String error() {
            return error;
        }

        public List<String> warnings() {
            return warnings;
        }

        public VersionGateConfig config() {
            return config;
        }
    }
}
