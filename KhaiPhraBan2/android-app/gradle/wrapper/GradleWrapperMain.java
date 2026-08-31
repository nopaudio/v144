package org.gradle.wrapper;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Small transparent Gradle bootstrap used for this generated project.
 * It follows gradle-wrapper.properties, verifies SHA-256, caches/unzips Gradle,
 * and starts the requested Gradle executable. Requires Java 17+, as does AGP 8.13.
 */
public final class GradleWrapperMain {
    public static void main(String[] args) throws Exception {
        Path appHome = findAppHome();
        Path propsPath = appHome.resolve("gradle/wrapper/gradle-wrapper.properties");
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(propsPath)) { props.load(reader); }

        String distributionUrl = required(props, "distributionUrl");
        String expectedSha = required(props, "distributionSha256Sum").toLowerCase(Locale.ROOT);
        String archiveName = Path.of(URI.create(distributionUrl).getPath()).getFileName().toString();
        String distributionName = archiveName.replaceFirst("\\.zip$", "");
        String gradleDirName = distributionName.replaceFirst("-(bin|all)$", "");

        String gradleUserHomeEnv = System.getenv("GRADLE_USER_HOME");
        Path gradleUserHome = gradleUserHomeEnv == null || gradleUserHomeEnv.isBlank()
                ? Path.of(System.getProperty("user.home"), ".gradle")
                : Path.of(gradleUserHomeEnv);
        Path installRoot = gradleUserHome.resolve("wrapper/dists").resolve(distributionName).resolve("khaiphraban");
        Path gradleHome = installRoot.resolve(gradleDirName);
        Path zipFile = installRoot.resolve(archiveName);
        Path lockFile = installRoot.resolve("bootstrap.lock");
        Files.createDirectories(installRoot);

        try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            Path executable = gradleExecutable(gradleHome);
            if (!Files.isRegularFile(executable)) {
                if (!Files.isRegularFile(zipFile) || !sha256(zipFile).equals(expectedSha)) {
                    Files.deleteIfExists(zipFile);
                    download(distributionUrl, zipFile, props.getProperty("networkTimeout", "10000"));
                    String actual = sha256(zipFile);
                    if (!actual.equals(expectedSha)) {
                        Files.deleteIfExists(zipFile);
                        throw new IOException("Gradle ZIP checksum mismatch. Expected " + expectedSha + " but got " + actual);
                    }
                }
                deleteRecursively(gradleHome);
                unzip(zipFile, installRoot);
                executable = gradleExecutable(gradleHome);
                if (!Files.isRegularFile(executable)) throw new IOException("Gradle executable not found after extraction: " + executable);
                executable.toFile().setExecutable(true, false);
            }
        }

        List<String> command = new ArrayList<>();
        command.add(gradleExecutable(gradleHome).toString());
        command.addAll(Arrays.asList(args));
        Process process = new ProcessBuilder(command)
                .directory(Path.of(System.getProperty("user.dir")).toFile())
                .inheritIO()
                .start();
        System.exit(process.waitFor());
    }

    private static Path findAppHome() throws Exception {
        Path jar = Path.of(GradleWrapperMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path parent = jar.toAbsolutePath().getParent();
        if (parent != null && parent.getFileName().toString().equals("wrapper")) {
            return parent.getParent().getParent();
        }
        return Path.of(System.getProperty("user.dir")).toAbsolutePath();
    }

    private static String required(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key + " in gradle-wrapper.properties");
        return value.trim();
    }

    private static Path gradleExecutable(Path gradleHome) {
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        return gradleHome.resolve("bin").resolve(windows ? "gradle.bat" : "gradle");
    }

    private static void download(String url, Path destination, String timeoutMs) throws Exception {
        Path temp = destination.resolveSibling(destination.getFileName() + ".part");
        Files.deleteIfExists(temp);
        long timeout = Math.max(10_000L, Long.parseLong(timeoutMs));
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofMillis(timeout))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(10))
                .header("User-Agent", "Gradle-Wrapper/8.13 KhaiPhraBan")
                .GET().build();
        System.out.println("Downloading Gradle 8.13...");
        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(temp));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            Files.deleteIfExists(temp);
            throw new IOException("Gradle download failed with HTTP " + response.statusCode());
        }
        try {
            Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void unzip(Path zipFile, Path targetRoot) throws IOException {
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                Path out = targetRoot.resolve(entry.getName()).normalize();
                if (!out.startsWith(targetRoot)) throw new IOException("Unsafe ZIP entry: " + entry.getName());
                if (entry.isDirectory()) Files.createDirectories(out);
                else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zin, out, StandardCopyOption.REPLACE_EXISTING);
                }
                zin.closeEntry();
            }
        }
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) >= 0) if (n > 0) digest.update(buffer, 0, n);
        }
        StringBuilder out = new StringBuilder();
        for (byte b : digest.digest()) out.append(String.format("%02x", b));
        return out.toString();
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException e) { throw new UncheckedIOException(e); }
            });
        } catch (UncheckedIOException e) { throw e.getCause(); }
    }
}
