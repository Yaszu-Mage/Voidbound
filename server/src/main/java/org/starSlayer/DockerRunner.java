package org.starSlayer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class DockerRunner {
    public DockerRunner() throws Exception {
        Path tempDir = Files.createTempDirectory("docker-build-context");
        Path targetDockerfile = tempDir.resolve("Dockerfile");

        try (InputStream in = DockerRunner.class.getResourceAsStream("Dockerfile")) {
            if (in == null) {
                throw new IOException("Dockerfile resource not found");
            }
            Files.copy(in, targetDockerfile, StandardCopyOption.REPLACE_EXISTING);
        }
        String imageName = "godot:4.7.2";
        runCommand(tempDir, "docker", "build", "-t", imageName, ".");
    }
    private static void runCommand(Path workingDir, String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDir.toFile());
        pb.inheritIO(); // Streams Docker output directly to your Java console

        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed with exit code " + exitCode);
        }
    }




    }




