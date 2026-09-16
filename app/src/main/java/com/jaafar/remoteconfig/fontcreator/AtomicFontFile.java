package com.jaafar.remoteconfig.fontcreator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Publishes complete font files without truncating files mapped by Android Typeface. */
final class AtomicFontFile {
    private AtomicFontFile() {}

    static void write(File destination, byte[] bytes) throws IOException {
        // A sibling guarantees the rename stays on the same filesystem. Each writer
        // needs its own temporary file because previews and generation run concurrently.
        File temporary = File.createTempFile("font-", ".tmp", destination.getAbsoluteFile().getParentFile());
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(bytes);
                output.getFD().sync();
            }
            // Android's rename replaces the directory entry atomically. Existing
            // Typeface mappings retain the old inode and its complete bytes.
            // Never fall back to deleting or truncating the destination on failure.
            rename(temporary, destination);
        } finally {
            temporary.delete();
        }
    }

    private static void rename(File temporary, File destination) throws IOException {
        try {
            Class<?> os = Class.forName("android.system.Os");
            Method rename = os.getMethod("rename", String.class, String.class);
            rename.invoke(null, temporary.getAbsolutePath(), destination.getAbsolutePath());
        } catch (ClassNotFoundException ignored) {
            if (!temporary.renameTo(destination)) {
                throw new IOException("Could not publish font: " + destination.getName());
            }
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new IOException("Could not publish font: " + destination.getName(), e);
        } catch (InvocationTargetException e) {
            throw new IOException("Could not publish font: " + destination.getName(), e.getCause());
        }
    }

}
