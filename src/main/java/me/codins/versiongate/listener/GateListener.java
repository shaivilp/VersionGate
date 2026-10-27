package me.codins.versiongate.listener;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.ServerPing;
import java.net.InetSocketAddress;
import me.codins.versiongate.VersionGate;
import me.codins.versiongate.config.VersionGateConfig;
import net.kyori.adventure.text.Component;

public final class GateListener {

    private final VersionGate plugin;

    public GateListener(VersionGate plugin) {
        this.plugin = plugin;
    }

    /**
     * PreLoginEvent fires before the proxy authenticates the player. It exposes
     * the username and the inbound connection, and it has no {@code Player}, so
     * {@code versiongate.bypass} cannot be evaluated on this event.
     * <p>
     * When {@code bypass-permission-enabled} is true, this method returns
     * immediately and {@link #onLogin(LoginEvent)} performs the same check.
     * LoginEvent fires after authentication and supplies a {@code Player} whose
     * permission function is available.
     * <p>
     * {@code Subscribe.order} is deprecated in Velocity 4 in favor of
     * {@code priority}. Priority is left at its default of 0, and
     * {@code VelocityEventManager} then maps {@link PostOrder#EARLY} to
     * {@code Short.MAX_VALUE / 2}. Higher priorities run earlier. Setting a
     * non-zero priority would replace that mapping.
     */
    @SuppressWarnings("deprecation")
    @Subscribe(order = PostOrder.EARLY)
    public void onPreLogin(PreLoginEvent event) {
        VersionGateConfig config = plugin.config();
        if (config.isBypassPermissionEnabled()) {
            return;
        }
        if (!event.getResult().isAllowed()) {
            return;
        }
        Component reason = rejection(
                config,
                event.getUsername(),
                event.getConnection().getRemoteAddress(),
                event.getConnection().getProtocolVersion());
        if (reason == null) {
            return;
        }
        event.setResult(PreLoginEvent.PreLoginComponentResult.denied(reason));
    }

    /**
     * Used only when permission bypass is enabled. See {@link #onPreLogin(PreLoginEvent)}.
     */
    @SuppressWarnings("deprecation")
    @Subscribe(order = PostOrder.EARLY)
    public void onLogin(LoginEvent event) {
        VersionGateConfig config = plugin.config();
        if (!config.isBypassPermissionEnabled()) {
            return;
        }
        if (!event.getResult().isAllowed()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.hasPermission(VersionGate.PERMISSION_BYPASS)) {
            return;
        }
        Component reason = rejection(
                config,
                player.getUsername(),
                player.getRemoteAddress(),
                player.getProtocolVersion());
        if (reason == null) {
            return;
        }
        event.setResult(ResultedEvent.ComponentResult.denied(reason));
    }

    /**
     * A status response whose protocol number differs from the client's is shown
     * in the server list as an incompatible version. {@code ServerPing.Version}
     * documents that the client may still try to connect; the login handlers
     * disconnect that attempt. The label is plain text because the status
     * version name is a string, not a chat component.
     */
    @SuppressWarnings("deprecation")
    @Subscribe(order = PostOrder.EARLY)
    public void onPing(ProxyPingEvent event) {
        VersionGateConfig config = plugin.config();
        if (!config.isUpdateServerListPing()) {
            return;
        }
        if (!event.getResult().isAllowed()) {
            return;
        }
        ProtocolVersion version = event.getConnection().getProtocolVersion();
        if (config.isAllowed(version)) {
            return;
        }
        int clientProtocol = version == null ? -1 : version.getProtocol();
        int shown = clientProtocol == Integer.MAX_VALUE ? clientProtocol - 1 : clientProtocol + 1;
        ServerPing ping = event.getPing().asBuilder()
                .version(new ServerPing.Version(shown, config.pingLabel()))
                .build();
        event.setPing(ping);
    }

    private Component rejection(
            VersionGateConfig config,
            String username,
            InetSocketAddress address,
            ProtocolVersion version) {
        if (config.isUsernameBypassed(username) || config.isAllowed(version)) {
            return null;
        }
        if (config.isLogBlockedAttempts()) {
            plugin.logger().info(
                    "Blocked {} from {} using {}",
                    username,
                    address,
                    VersionGateConfig.describeClient(version));
        }
        return config.kick(version);
    }
}
