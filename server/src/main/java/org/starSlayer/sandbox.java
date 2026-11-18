package org.starSlayer;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class sandbox {

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

    public void runScript(String script, long timeoutSeconds, CompletableFuture<String> future) {
        System.out.println("Running script: " + script);

        Path tempDir = null;
        String containerId = null;
        StringBuilder output = new StringBuilder();
        boolean timedOut = false;

        try {
            tempDir = Files.createTempDirectory("sandbox");
            Path scriptPath = tempDir.resolve("script.gd");
            Path hostScriptPath = tempDir.resolve("host_script.gd");

            try (InputStream in = sandbox.class.getResourceAsStream("/host_script.gd")) {
                if (in == null) {
                    throw new IOException("host_script.gd resource not found");
                }
                Files.copy(in, hostScriptPath, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(scriptPath, script, StandardCharsets.UTF_8);

            tempDir.toFile().setReadable(true, false);
            tempDir.toFile().setExecutable(true, false);
            scriptPath.toFile().setReadable(true, false);
            scriptPath.toFile().setExecutable(true, false);
            hostScriptPath.toFile().setReadable(true, false);
            hostScriptPath.toFile().setExecutable(true, false);

            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withNetworkMode("none")
                    .withReadonlyRootfs(true)
                    .withTmpFs(Map.of("/tmp", "rw,nosuid,size=64m"))
                    .withMemory(128L * 1024L * 1024L) // 128MB
                    .withMemorySwap(128L * 1024L * 1024L)
                    .withNanoCPUs(500_000_000L) // 0.5 CPU cores
                    .withPidsLimit(32L) // no fork bombs
                    .withCapDrop(Capability.ALL)
                    .withBinds(
                            new Bind(
                                    scriptPath.toAbsolutePath().toString(),
                                    new Volume("/sandbox/script.gd"),
                                    AccessMode.ro
                            ),
                            new Bind(
                                    hostScriptPath.toAbsolutePath().toString(),
                                    new Volume("/sandbox/host_script.gd"),
                                    AccessMode.ro
                            )
                    );

            containerId = dockerClient.createContainerCmd("godot:4.7.2")
                    .withHostConfig(hostConfig)
                    .withEnv("HOME=/tmp", "XDG_DATA_HOME=/tmp", "XDG_CONFIG_HOME=/tmp", "XDG_CACHE_HOME=/tmp")
                    .withCmd("--headless", "--script", "/sandbox/host_script.gd")
                    .exec()
                    .getId();

            dockerClient.startContainerCmd(containerId).exec();

            try {
                dockerClient.waitContainerCmd(containerId)
                        .start()
                        .awaitStatusCode(timeoutSeconds, TimeUnit.SECONDS);
            } catch (Exception e) {
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
            future.completeExceptionally(e);
            return;
        } finally {
            if (containerId != null) {
                try {
                    dockerClient.removeContainerCmd(containerId).withForce(true).exec();
                } catch (Exception ignored) {}
            }
            if (tempDir != null) {
                // Delete children first, then the directory itself
                try (var paths = Files.walk(tempDir)) {
                    paths.sorted(Comparator.reverseOrder())
                            .forEach(p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (IOException ignored) {}
                            });
                } catch (IOException ignored) {}
            }
        }

        String result = output.toString().replaceAll(
                "Godot Engine v4\\.7\\.2\\.stable\\.official\\.ed1daf0bf - https://godotengine\\.org\\n\\n", "");
        result = truncate(result,5000);
        System.out.println("Final result: " + result);
        if (timedOut && script.contains("process(delta)")) {
            future.complete(result);
            return;
        }
        future.complete(timedOut ? "TIMEOUT\n" + result : result);
    }
    public static String truncate(String str, int maxLength) {
        if (str == null) {
            return null;
        }
        return str.substring(0, Math.min(str.length(), maxLength));
    }
}