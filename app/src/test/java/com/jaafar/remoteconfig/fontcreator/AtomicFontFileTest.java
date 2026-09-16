package com.jaafar.remoteconfig.fontcreator;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeFalse;
import java.io.*;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.*;

public class AtomicFontFileTest {
    @Test public void replacementPreservesExistingFontMapping() throws Exception {
        assumePosixReplacement();
        File directory = Files.createTempDirectory("font-map").toFile();
        File font = new File(directory, "preview.ttf");
        byte[] original = new byte[8192];
        Arrays.fill(original, (byte) 42);
        write(font, original);
        try (RandomAccessFile reader = new RandomAccessFile(font, "r")) {
            MappedByteBuffer mapping = reader.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, original.length);
            byte[] replacement = new byte[] {1, 2, 3};
            write(font, replacement);
            assertArrayEquals(replacement, Files.readAllBytes(font.toPath()));
            // Reading beyond the replacement length would fault if the old inode
            // had been truncated, as in the production SIGBUS report.
            assertEquals(42, mapping.get(8191));
            assertEquals(1, directory.list().length);
        } finally { font.delete(); directory.delete(); }
    }

    @Test public void concurrentReadersOnlySeeCompleteFiles() throws Exception {
        assumePosixReplacement();
        File directory = Files.createTempDirectory("font-race").toFile();
        File font = new File(directory, "preview.ttf");
        byte[] first = new byte[16384];
        byte[] second = new byte[32768];
        Arrays.fill(first, (byte) 1);
        Arrays.fill(second, (byte) 2);
        write(font, first);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            Future<?> a = pool.submit(() -> { for (int i=0;i<50;i++) write(font, first); });
            Future<?> b = pool.submit(() -> { for (int i=0;i<50;i++) write(font, second); });
            Future<?> reader = pool.submit(() -> {
                for (int i=0;i<300;i++) {
                    try {
                        byte[] actual = Files.readAllBytes(font.toPath());
                        assertTrue(Arrays.equals(first, actual) || Arrays.equals(second, actual));
                    } catch (IOException e) { throw new UncheckedIOException(e); }
                }
            });
            a.get(); b.get(); reader.get();
            assertEquals(1, directory.list().length);
        } finally { pool.shutdownNow(); font.delete(); directory.delete(); }
    }

    @Test public void failedReplacementPreservesDestinationAndCleansTemporaryFile() throws Exception {
        File directory = Files.createTempDirectory("font-failure").toFile();
        File destination = new File(directory, "existing");
        assertTrue(destination.mkdir());
        File sentinel = new File(destination, "keep");
        Files.write(sentinel.toPath(), new byte[] {7});
        try {
            try { AtomicFontFile.write(destination, new byte[] {1}); fail("Expected failure"); }
            catch (IOException expected) { }
            assertArrayEquals(new byte[] {7}, Files.readAllBytes(sentinel.toPath()));
            assertEquals(1, directory.list().length);
        } finally { sentinel.delete(); destination.delete(); directory.delete(); }
    }

    private static void write(File file, byte[] bytes) {
        try { AtomicFontFile.write(file, bytes); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }

    private static void assumePosixReplacement() {
        assumeFalse("Windows locks mapped files, unlike Android/Linux rename semantics.", System.getProperty("os.name").startsWith("Windows"));
    }
}
