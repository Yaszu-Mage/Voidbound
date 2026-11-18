package org.starSlayer;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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


    public void runScript(String script, long timeoutSeconds,CompletableFuture<String> future) throws Exception {
        System.out.println("Running script: " + script);
        Path tempDir = Files.createTempDirectory("sandbox");
        Path scriptPath = tempDir.resolve("script.gd");
        Files.writeString(scriptPath, script, StandardCharsets.UTF_8);
        String containerId = null;
        StringBuilder output = new StringBuilder();
        tempDir.toFile().setReadable(true, false);
        tempDir.toFile().setExecutable(true, false);
        scriptPath.toFile().setReadable(true, false);
        scriptPath.toFile().setExecutable(true, false);
        try {
            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withNetworkMode("none")
                    .withReadonlyRootfs(true)
                    .withTmpFs(Map.of("/tmp", "rw,nosuid,size=64m"))
                    .withMemory(128L * 1024L * 1024L) // 128MB
                    .withMemorySwap(128L * 1024L * 1024L)
                    .withNanoCPUs(500_000_000L) // 0.5 CPU Cores
                    .withPidsLimit(32L) //No no fork bombs
                    .withCapDrop(Capability.ALL)
                    .withBinds(new Bind(
                            scriptPath.toAbsolutePath().toString(),
                            new Volume("/sandbox/script.gd"),
                            AccessMode.ro
                    ));
            CreateContainerResponse container = dockerClient.createContainerCmd("godot:4.7.2")
                    .withHostConfig(hostConfig)
                    .withEnv("HOME=/tmp", "XDG_DATA_HOME=/tmp", "XDG_CONFIG_HOME=/tmp", "XDG_CACHE_HOME=/tmp")
                    .withCmd("--headless", "--script", "/sandbox/script.gd")
                    .exec();
            containerId = container.getId();

            dockerClient.startContainerCmd(containerId).exec();
            boolean timedOut = false;
            try {
                dockerClient.waitContainerCmd(containerId).start()
                        .awaitStatusCode(timeoutSeconds, TimeUnit.SECONDS);
            } catch (Exception e) {
                timedOut = true;
                try { dockerClient.killContainerCmd(containerId).exec(); } catch (Exception ignored) {}
            }
            if (timedOut) {
                future.complete(timedOut ? "TIMEOUT\n" + output : output.toString());
            }
            dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)

                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                            output.append(new String(frame.getPayload(), StandardCharsets.UTF_8));
                        }
                    }).awaitCompletion(3, TimeUnit.SECONDS);

        } finally {
            if (containerId != null) {
                try {
                    dockerClient.removeContainerCmd(containerId).withForce(true).exec();
                } catch (Exception ignored) {}
            }
            Files.deleteIfExists(scriptPath);
            Files.deleteIfExists(tempDir);
        }

        String result = output.toString();
        result = result.replaceAll("Godot Engine v4.7.2.stable.official.ed1daf0bf - https://godotengine.org\\n\\n", "");
        future.complete(result);



    }



}
