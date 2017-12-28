package jaa.internal.runner;

import java.io.*;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static java.nio.file.Files.createDirectories;

public class Unzipper {
    private static final int BUFFER_SIZE = 4096 * 4;

    public void unzip(Path zipFilePath, Path destDirectory) throws IOException {
        createDirectories(destDirectory);

        ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath.toFile()));
        ZipEntry entry = zipIn.getNextEntry();
        while (entry != null) {
            Path filePath = destDirectory.resolve(entry.getName());
            if (!entry.isDirectory()) {
                extractFile(zipIn, filePath);
            } else {
                createDirectories(filePath);
            }
            zipIn.closeEntry();
            entry = zipIn.getNextEntry();
        }
        zipIn.close();
    }

    private void extractFile(ZipInputStream zipIn, Path filePath) throws IOException {
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath.toFile()));
        byte[] bytesIn = new byte[BUFFER_SIZE];
        int read = 0;
        while ((read = zipIn.read(bytesIn)) != -1) {
            bos.write(bytesIn, 0, read);
        }
        bos.close();
    }
}
