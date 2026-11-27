package com.nakpaaiyaa.rbo;

import android.content.Context;
import android.net.Uri;
import android.os.Build;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class DriverStore {
    private static final long MAX_BYTES = 256L * 1024 * 1024;
    private static final int MAX_METADATA = 64 * 1024;
    private static final String PACKAGE_ID = "[0-9a-f-]{36}";

    final String id, name, library;
    final File directory;

    private DriverStore(File directory, String name, String library) {
        this.directory = directory;
        this.id = directory.getName();
        this.name = name;
        this.library = library;
    }

    private static File root(Context context) {
        return new File(context.getFilesDir(), "vulkan-drivers");
    }

    static DriverStore get(Context context, String id) {
        if (id == null || !id.matches(PACKAGE_ID)) return null;
        try {
            return read(new File(root(context), id));
        } catch (IOException | JSONException invalid) {
            return null;
        }
    }

    static List<DriverStore> installed(Context context) {
        List<DriverStore> drivers = new ArrayList<>();
        File[] folders = root(context).listFiles();
        if (folders != null) for (File folder : folders) {
            DriverStore driver = get(context, folder.getName());
            if (driver != null) drivers.add(driver);
        }
        drivers.sort(Comparator.comparing(d -> d.name, String.CASE_INSENSITIVE_ORDER));
        return drivers;
    }

    static DriverStore install(Context context, Uri uri) throws IOException, JSONException {
        File root = root(context);
        if (!root.isDirectory() && !root.mkdirs() && !root.isDirectory())
            throw new IOException("Could not create driver folder.");
        String id = UUID.randomUUID().toString();
        File staging = new File(root, ".import-" + id);
        if (!staging.mkdir()) throw new IOException("Could not create temporary folder.");

        try (InputStream source = context.getContentResolver().openInputStream(uri)) {
            if (source == null) throw new IOException("Could not read the ZIP.");
            extract(source, staging);
            DriverStore driver = read(staging);
            File installed = new File(root, id);
            if (!staging.renameTo(installed)) throw new IOException("Could not save the driver.");
            return new DriverStore(installed, driver.name, driver.library);
        } finally {
            File[] files = staging.listFiles();
            if (files != null) for (File file : files) file.delete();
            staging.delete();
        }
    }

    private static void extract(InputStream source, File staging) throws IOException {
        long total = 0;
        int files = 0;
        byte[] buffer = new byte[32 * 1024];
        try (ZipInputStream zip = new ZipInputStream(source)) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                if (++files > 128) throw new IOException("Too many entries in driver ZIP.");
                String name = entry.getName();
                boolean meta = "meta.json".equals(name);
                boolean keep = !entry.isDirectory() && (meta || validLibraryName(name));
                long limit = meta ? MAX_METADATA : MAX_BYTES;
                long written = 0;
                File destination = new File(staging, keep ? name : "unused");
                if (keep && !destination.createNewFile()) throw new IOException("Duplicate file: " + name);
                try (FileOutputStream output = keep ? new FileOutputStream(destination) : null) {
                    for (int size; (size = zip.read(buffer)) != -1; ) {
                        total += size;
                        written += size;
                        if (total > MAX_BYTES || written > limit) throw new IOException("Driver ZIP is too large.");
                        if (output != null) output.write(buffer, 0, size);
                    }
                }
                if (keep && !meta) validateElf(destination);
            }
        }
    }

    private static boolean validLibraryName(String name) {
        return name.matches("[A-Za-z0-9_][A-Za-z0-9_.+-]{0,126}\\.so");
    }

    private static DriverStore read(File folder) throws IOException, JSONException {
        File manifest = new File(folder, "meta.json");
        if (!manifest.isFile() || manifest.length() > MAX_METADATA)
            throw new IOException("The ZIP needs meta.json at its root.");
        JSONObject meta = new JSONObject(new String(Files.readAllBytes(manifest.toPath()), StandardCharsets.UTF_8));
        String name = meta.getString("name").trim();
        String library = meta.getString("libraryName");
        if (name.isEmpty() || name.length() > 160 || !validLibraryName(library))
            throw new IOException("Invalid driver name or libraryName in meta.json.");
        if (meta.optInt("minApi", 28) > Build.VERSION.SDK_INT)
            throw new IOException("This driver requires a newer Android version.");
        validateElf(new File(folder, library));
        return new DriverStore(folder, name, library);
    }

    private static void validateElf(File library) throws IOException {
        byte[] header = new byte[20];
        try (FileInputStream input = new FileInputStream(library)) {
            int offset = 0;
            while (offset < header.length) {
                int count = input.read(header, offset, header.length - offset);
                if (count < 0) break;
                offset += count;
            }
            if (offset == header.length && header[0] == 0x7f && header[1] == 'E' &&
                    header[2] == 'L' && header[3] == 'F' && header[4] == 2 && header[5] == 1 &&
                    header[16] == 3 && header[17] == 0 && (header[18] & 255) == 183 && header[19] == 0)
                return;
        }
        throw new IOException(library.getName() + " is not an ARM64 Android shared library.");
    }
}
