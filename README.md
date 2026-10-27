# VersionGate

Velocity plugin that rejects Minecraft clients outside a configured protocol range or list. Put it on the proxy in front of 1.8 backends that run ViaVersion and ViaRewind, so 1.7 and 1.8 clients can join and 1.9+ clients are disconnected before they reach a backend.

## Requirements

- Velocity 4.3 or newer, running on Java 25 (Velocity 4's own requirement).
- JDK 25 or newer to compile. `velocity-api` 4.3.0 is Java 25 bytecode. The plugin is compiled with `maven.compiler.release` 17, so the jar itself is Java 17 bytecode and loads on the Java 25 proxy.

## Build

```bash
mvn clean package
```

The shaded jar is `target/VersionGate.jar`. SnakeYAML is relocated inside that jar. Velocity, Adventure, and SLF4J are not shaded.

## Install

1. Copy `target/VersionGate.jar` into the proxy `plugins` folder.
2. Start the proxy once. VersionGate writes `plugins/versiongate/config.yml` if it is missing.
3. Edit that file. The defaults allow protocols 4 through 47, which is 1.7.2 through 1.8.9. Protocol 5 (1.7.6 through 1.7.10) sits inside that range.
4. Run `/versiongate reload`. A parse error keeps the previous configuration. Invalid fields fall back to the safe defaults and are reported as warnings.

Quote version names in the file (`"1.8.9"`). An unquoted `1.8` is a YAML decimal and is rejected.

## Commands and permissions

- `/versiongate reload` requires `versiongate.reload`
- `/versiongate info` requires `versiongate.info`
- `versiongate.bypass` skips the version check during login

`versiongate.bypass` is checked only when `bypass-permission-enabled` is true. That check runs on `LoginEvent`, because `PreLoginEvent` has no player and therefore no permissions. With the option left false, the version check runs on `PreLoginEvent`. Usernames in `bypass-usernames` skip the check on whichever of those events is active.

With `update-server-list-ping` left true, a blocked client sees the `ping-version-label` text (by default `Requires 1.7.2 - 1.8.9`) as an incompatible version in the server list.
