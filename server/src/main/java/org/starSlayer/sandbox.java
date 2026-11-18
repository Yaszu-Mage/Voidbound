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
        Path tempDir = Files.createTempDirectory("sandboxe");
        Path scriptPath = tempDir.resolve("script.gd");
        Files.writeString(scriptPath, script, StandardCharsets.UTF_8);
        String containerId = null;
        StringBuilder output = new StringBuilder();

        try {
            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withNetworkMode("none")
                    .withReadonlyRootfs(true)
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
            CreateContainerResponse container = dockerClient.createContainerCmd("godot-sandbox:4.3")
                    .withHostConfig(hostConfig)
                    .withCmd("run", "/sandbox/script.gd")
                    .exec();
            containerId = container.getId();

            dockerClient.startContainerCmd(containerId).exec();
            dockerClient.waitContainerCmd(containerId)
                    .start()
                    .awaitStatusCode(timeoutSeconds, TimeUnit.SECONDS);
            dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                            output.append(new String(frame.getPayload(), StandardCharsets.UTF_8));
                        }
                    }).awaitCompletion(3, TimeUnit.SECONDS);
            future.complete(output.toString());
        } finally {
            if (containerId != null) {
                try {
                    dockerClient.removeContainerCmd(containerId).withForce(true).exec();
                } catch (Exception ignored) {}
            }
            Files.deleteIfExists(scriptPath);
            Files.deleteIfExists(tempDir);
        }



    }



}
