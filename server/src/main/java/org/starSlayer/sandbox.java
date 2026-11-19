package org.starSlayer;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import org.java_websocket.WebSocket;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class sandbox {

    private static final String IMAGE = "godot:4.7.2";
    public static final HashMap<WebSocket, Boolean> players = new HashMap<>();
    // Debug: set -Dsandbox.keep=true (or env SANDBOX_KEEP=1) to leave containers
    // in place so they show up in Docker Desktop / `docker ps -a`.
    private static final boolean KEEP_CONTAINER =
            Boolean.getBoolean("sandbox.keep") || "1".equals(System.getenv("SANDBOX_KEEP"));

    private final DockerClient dockerClient;

    public sandbox() {
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        var httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .connectionTimeout(Duration.ofSeconds(5))
                .responseTimeout(Duration.ofSeconds(15))
                .build();
        this.dockerClient = DockerClientImpl.getInstance(config, httpClient);
    }

    HttpClient httpClient = HttpClient.newHttpClient();

    public void runScript(String script, long timeoutSeconds, CompletableFuture<String> future,WebSocket conn) {
        if (players.get(conn) == true) {
            future.complete("ERROR: Already running a script for this player.");
            return;
        }

        players.put(conn, true);

        System.out.println("Running script: " + script);
        HttpClient.Builder httpClientBuilder = HttpClient.newBuilder();
        Path tempDir = null;
        String containerId = null;
        StringBuilder output = new StringBuilder();
        boolean timedOut = false;

        try {
            try {
                dockerClient.inspectImageCmd(IMAGE).exec();
            } catch (Exception e) {
                throw new IllegalStateException("Docker image '" + IMAGE + "' not found locally. "
                        + "Build or pull it first (docker images).", e);
            }

            tempDir = Files.createTempDirectory("sandbox");
            Path scriptPath = tempDir.resolve("script.gd");
            Path hostScriptPath = tempDir.resolve("host_script.gd");
            Path playerScenePath = tempDir.resolve("player.tscn");
            Path blankWorld = tempDir.resolve("blankWorld.tscn");

            copyResourceFile("/host_script.gd", hostScriptPath);
            copyResourceFile("/player.tscn", playerScenePath);
            copyResourceFile("/blankWorld.tscn", blankWorld);
            Files.writeString(scriptPath, script, StandardCharsets.UTF_8);

            makeWorldReadable(tempDir);

            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withNetworkMode("none")
                    .withReadonlyRootfs(true)
                    .withTmpFs(Map.of("/tmp", "rw,nosuid,size=64m"))
                    .withMemory(512L * 1024L * 1024L) // 512MB (128MB is too tight for Godot; lower it again once it works)
                    .withMemorySwap(512L * 1024L * 1024L)
                    .withNanoCPUs(500_000_000L) // 0.5 CPU cores
                    .withPidsLimit(64L) // no fork bombs
                    .withCapDrop(Capability.ALL)
                    .withBinds(
                            new Bind(scriptPath.toAbsolutePath().toString(),
                                    new Volume("/sandbox/script.gd"), AccessMode.ro),
                            new Bind(hostScriptPath.toAbsolutePath().toString(),
                                    new Volume("/sandbox/host_script.gd"), AccessMode.ro),
                            new Bind(playerScenePath.toAbsolutePath().toString(),
                                    new Volume("/sandbox/player.tscn"), AccessMode.ro),
                            new Bind(blankWorld.toAbsolutePath().toString(),
                                    new Volume("/sandbox/blankWorld.tscn"), AccessMode.ro)
                    );

            containerId = dockerClient.createContainerCmd(IMAGE)
                    .withHostConfig(hostConfig)
                    .withEnv("HOME=/tmp", "XDG_DATA_HOME=/tmp", "XDG_CONFIG_HOME=/tmp", "XDG_CACHE_HOME=/tmp")
                    .withCmd("--headless", "--script", "/sandbox/host_script.gd")
                    .exec()
                    .getId();
            System.out.println("Created container " + containerId);

            dockerClient.startContainerCmd(containerId).exec();

            try {
                Integer exitCode = dockerClient.waitContainerCmd(containerId)
                        .start()
                        .awaitStatusCode(timeoutSeconds, TimeUnit.SECONDS);
                System.out.println("Container exited with code " + exitCode);
            } catch (Exception e) {
                System.err.println("Wait failed or timed out: " + e);
                timedOut = true;
                try {
                    dockerClient.killContainerCmd(containerId).exec();
                } catch (Exception ignored) {}
            }

            // Always read logs, even after a timeout, so partial output isn't lost
            dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withFollowStream(false)
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                            output.append(new String(frame.getPayload(), StandardCharsets.UTF_8));
                        }
                    })
                    .awaitCompletion(6, TimeUnit.SECONDS);

        } catch (Exception e) {
            e.printStackTrace(); // don't let failures vanish silently
            future.completeExceptionally(e);
            return;
        } finally {
            if (containerId != null && !KEEP_CONTAINER) {
                try {
                    dockerClient.removeContainerCmd(containerId).withForce(true).exec();
                } catch (Exception ignored) {
                }
            } else if (containerId != null) {
                System.out.println("KEEP_CONTAINER on, leaving container " + containerId);
            }
            if (tempDir != null && !KEEP_CONTAINER) {
                // Delete children first, then the directory itself
                try (var paths = Files.walk(tempDir)) {
                    paths.sorted(Comparator.reverseOrder())
                            .forEach(p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (IOException ignored) {
                                }
                            });
                } catch (IOException ignored) {
                }
            }
        }
        String result = output.toString().replaceAll(
                "Godot Engine v4\\.7\\.2\\.stable\\.official\\.ed1daf0bf - https://godotengine\\.org\\n\\n", "");
        if (result.length() >= 5000) {
            future.complete("ERROR: Result too long.");
            return;
        }
        result = truncate(result, 5000);
        System.out.println("Final result: " + result);
        if (timedOut && script.contains("process(delta)")) {
            future.complete(result);
            return;
        }
        future.complete(timedOut ? "TIMEOUT\n" + result : result);
        players.put(conn, false);
    }

    // ---------- resource helpers ----------

    /** Copies a single classpath resource to a file. */
    private static void copyResourceFile(String resource, Path target) throws IOException {
        try (InputStream in = sandbox.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException(resource + " resource not found");
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Makes everything under dir readable (and dirs traversable) by the container user. */
    private static void makeWorldReadable(Path dir) throws IOException {
        try (var stream = Files.walk(dir)) {
            stream.forEach(p -> {
                p.toFile().setReadable(true, false);
                p.toFile().setExecutable(true, false);
            });
        }
    }

    public static String truncate(String str, int maxLength) {
        if (str == null) {
            return null;
        }
        return str.substring(0, Math.min(str.length(), maxLength));
    }
}