package jaa.runner;

import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import static jaa.runner.Proc.exec;

public class JaaResources {
    private final Path jvmDownloadDir;

    public JaaResources() {
        this(Paths.get(System.getProperty("user.home"), ".jaa"));
    }

    public JaaResources(Path jvmDownloadDir) {
        this.jvmDownloadDir = jvmDownloadDir;
    }

    public Path javaHome() throws IOException, InterruptedException {
        String osName = System.getProperty("os.name");
        String cpuArch = System.getProperty("os.arch");

        System.out.println(osName);
        System.out.println(cpuArch);

        if(osName.equalsIgnoreCase("linux")) {
            System.out.println("It's linux");
        }

        Files.createDirectories(jvmDownloadDir);
        Path jdkLocation = jvmDownloadDir.resolve("jdk8-linux-x86_64-fastdebug");
        URL jdkUrl = new URL("..");
        Path tarballDownloadLocation = jvmDownloadDir.resolve("jdk8-linux-x86_64-fastdebug.tar.gz");

        download(jdkUrl, tarballDownloadLocation);
        untar(tarballDownloadLocation, jdkLocation);

        return jdkLocation;
    }

    public Path javaExecutable(Path javaHome)
    {
        return javaHome.resolve("bin").resolve("java");
    }

    private void download(URL from, Path to) throws IOException {
        if(Files.exists(to)) {
            return;
        }
        System.out.println("Downloading debug JDK to: " + to);
        try(ReadableByteChannel rbc = Channels.newChannel(from.openStream());
            FileChannel target = FileChannel.open(to, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            // TODO: This needs to check the return value..
            // but I'm not sure how to know the value of the remote resource..
            target.transferFrom(rbc, 0, Long.MAX_VALUE);
        }
    }

    private void untar(Path tarball, Path untarLocation) throws IOException, InterruptedException {
        if(Files.exists(untarLocation)) {
            return;
        }
        exec("tar",
                "-xvf", tarball.toAbsolutePath().toString(),
                "--directory", untarLocation.toAbsolutePath().toString())
                .awaitSuccessfulExit();
    }
}
