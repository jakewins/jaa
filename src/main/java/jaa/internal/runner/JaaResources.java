package jaa.internal.runner;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class JaaResources {
    private final Path jvmDownloadDir;

    private final DownloadableJdk[] jdkDownloads = new DownloadableJdk[]{
        new DownloadableJdk(
                os -> os.equalsIgnoreCase("linux"),
                arch -> arch.equalsIgnoreCase("amd64"),
                java -> java.startsWith("1.8.0"),
                jaaHome -> {
                    URL jdkUrl = new URL("https://s3-us-west-2.amazonaws.com/jaa-jvms/jdk8u-a0672a294b9a-linux-x86_64-normal-server-fastdebug.zip");
                    Path jdkLocation = jaaHome.resolve("jdk8u-a0672a294b9a-linux-x86_64-normal-server-fastdebug");
                    Path zipLocation = jaaHome.resolve("jdk8u-a0672a294b9a-linux-x86_64-normal-server-fastdebug.zip");

                    download(jdkUrl, zipLocation);
                    unzip(zipLocation, jdkLocation);
                    return jdkLocation;
                })
        // TODO: there are windows builds at https://github.com/ojdkbuild/ojdkbuild/releases/download/1.8.0.111-1/java-1.8.0-openjdk-fastdebug-1.8.0.111-1.b15.ojdkbuild.windows.x86_64.zip
        // TODO: There may be OS X builds via https://github.com/AdoptOpenJDK/openjdk-build/issues/146
    };

    public JaaResources() {
        this(Paths.get(System.getProperty("user.home"), ".jaa"));
    }

    public JaaResources(Path jvmDownloadDir) {
        this.jvmDownloadDir = jvmDownloadDir;
    }

    public Path javaHome() throws IOException, InterruptedException {
        String osName = System.getProperty("os.name");
        String cpuArch = System.getProperty("os.arch");
        String javaVersion = System.getProperty("java.version");

        for (DownloadableJdk jdkDownload : jdkDownloads) {
            if(jdkDownload.supports(osName, cpuArch, javaVersion)) {
                return jdkDownload.ensureDownloadedTo(jvmDownloadDir);
            }
        }

        // Note; It might be better to just use the JVM currently running, and instead print a warning?
        // The escape analysis stuff won't work, but at least the allocation tracking would.. not sure.
        throw new IllegalStateException("JAA depends on a special debug build of the OpenJDK JVM to " +
                "perform analysis on allocations eliminated by the JVM, and there is none registered for automatic " +
                "download for your platform.\n" +
                "For JAA to work, you need to find and install a 'fastdebug' build of the JVM for your platform, and provide " +
                "to the Options you give the JAA runner.\n" +
                "If you would, please file a ticket at https://github.com/jakewins/jaa, " +
                "noting your os.name is '%s', and your os.arch is '%s' to allow automating this in the future.\n");
    }

    public Path javaExecutable(Path javaHome) throws IOException {
        try (Stream<Path> stream = Files.find(javaHome, 5,
                (path, attr) ->
                        path.getFileName().toString().equalsIgnoreCase("java") ||
                        path.getFileName().toString().equalsIgnoreCase("Java.exe") )) {
            Optional<Path> maybeJava = stream.findAny();
            if(maybeJava.isPresent()) {
                Path path = maybeJava.get();
                if(!path.toFile().setExecutable(true)) {
                    throw new IllegalStateException(String.format("Unable to make %s executable. " +
                            "Try manually doing it, something like `chmod +x %s`.", path, path));
                }
                return path;
            }

            throw new IllegalStateException(String.format("Unable to find `java`/`Java.exe` executable in %s. " +
                    "Please specify a direct path to the 'java', or Java.exe on windows, executable file " +
                    "in the Options you give to the JAA runner.", javaHome));
        }
    }

    private static void download(URL from, Path to) throws IOException {
        if(Files.exists(to)) {
            return;
        }
        System.out.println("Downloading debug JDK to: " + to);
        try(ReadableByteChannel rbc = Channels.newChannel(from.openStream());
            FileChannel target = FileChannel.open(to, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            // TODO: This needs to check the return value..
            // but I'm not sure how to know the value of the remote resource..
            target.transferFrom(rbc, 0, Long.MAX_VALUE);
        } catch(FileNotFoundException e) {
            // I'm paranoid about what the AWS bill for this ends up being if someone puts JAA into a CI system,
            // so this is a precaution to allow me to stop the downloads with at least an explanation.
            throw new IOException(
                    "Debug JDK not found at ensureDownloadedTo location. " +
                    "Most likely he JAA maintainer removed the file due to bandwidth cost.\n" +
                    "If you are able to help pay for, or directly provide, hosting of these files, please reach out " +
                    "via a ticket at https://github.com/jakewins/jaa.\n" +
                    "Meanwhile, please see google for how to build a debug jvm on your own, and provide the " +
                    "path to it in Options.");
        }
    }

    private static void unzip(Path zipFile, Path target) throws IOException {
        if(Files.exists(target)) {
            return;
        }
        System.out.println("Unzipping JDK..");
        new Unzipper().unzip(zipFile, target);
    }
}

class DownloadableJdk {
    private final Predicate<String> supportsOs;
    private final Predicate<String> supportsArch;
    private final Predicate<String> supportsJavaVersion;
    private final Downloader downloader;

    interface Downloader {
        Path download(Path jaaHome) throws IOException;
    }

    DownloadableJdk(Predicate<String> supportsOs, Predicate<String> supportsArch, Predicate<String> supportsJavaVersion, Downloader downloader) {
        this.supportsOs = supportsOs;
        this.supportsArch = supportsArch;
        this.supportsJavaVersion = supportsJavaVersion;
        this.downloader = downloader;
    }

    public boolean supports(String osName, String cpuArchitecture, String javaVersion) {
        return supportsArch.test(cpuArchitecture)
                && supportsOs.test(osName)
                && supportsJavaVersion.test(javaVersion);
    }

    public Path ensureDownloadedTo(Path workDir) throws IOException {
        return downloader.download(workDir);
    }
}