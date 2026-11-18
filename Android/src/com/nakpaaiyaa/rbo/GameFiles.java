package com.nakpaaiyaa.rbo;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

final class GameFiles {
    static final String EXE = "RBO_Ex3.exe";
    private static final String[] KEY_CONFIGS = {
        "KeyConfig.cdt", "KeyConfig.v2", "KeyConfig.v3", "KeyConfig.v4"
    };
    private static final int KEY_CONFIG_SIZE = 328, PLAYER_SIZE = 108;

    static File root(File files) {
        return new File(files, "game");
    }

    static boolean ready(File root) {
        return new File(root, EXE).isFile() && new File(root, "DATA").isDirectory();
    }

    static void recover(File files) {
        File previous = new File(files, "previous-game");
        if (!root(files).exists() && previous.exists()) previous.renameTo(root(files));
    }

    static void importFolder(ContentResolver resolver, Uri tree, File files) throws IOException {
        File staging = new File(files, "game-import");
        File previous = new File(files, "previous-game");
        File current = root(files);
        recover(files);
        deleteTree(staging);
        if (!staging.mkdir()) throw new IOException("Could not create the import directory.");
        try {
            String treeId = DocumentsContract.getTreeDocumentId(tree);
            copyChildren(resolver, tree, treeId, staging, new byte[128 * 1024], 0);
            if (!ready(staging)) {
                throw new IOException("Select the folder that contains RBO_Ex3.exe and DATA.");
            }
            verifyExecutable(new File(staging, EXE));
            patchKeys(new File(staging, "DATA/SaveData"));
            deleteTree(previous);
            if (current.exists() && !current.renameTo(previous)) {
                throw new IOException("Could not back up the installed game.");
            }
            if (!staging.renameTo(current)) {
                recover(files);
                throw new IOException("Could not finish the import.");
            }
        } finally {
            if (staging.exists()) {
                try { deleteTree(staging); } catch (IOException ignored) {}
            }
        }
    }

    private static void copyChildren(ContentResolver resolver, Uri tree, String parentId,
                                     File destination, byte[] buffer, int depth) throws IOException {
        if (depth > 40) throw new IOException("The selected game folder is nested too deeply.");
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        String[] columns = {Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
                            Document.COLUMN_MIME_TYPE};
        try (Cursor cursor = resolver.query(children, columns, null, null, null)) {
            if (cursor == null) throw new IOException("The selected folder could not be read.");
            while (cursor.moveToNext()) {
                String id = cursor.getString(0);
                String name = cursor.getString(1);
                if (name == null || name.isEmpty() || name.equals(".") || name.equals("..")
                        || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0
                        || name.indexOf('\0') >= 0) {
                    throw new IOException("A file or folder has an invalid name.");
                }
                if (depth == 0 && name.equalsIgnoreCase(EXE)) name = EXE;
                if (depth == 0 && name.equalsIgnoreCase("DATA")) name = "DATA";
                File target = new File(destination, name);
                if (target.exists()) throw new IOException("Duplicate name in the folder: " + name);
                if (Document.MIME_TYPE_DIR.equals(cursor.getString(2))) {
                    if (!target.mkdir()) throw new IOException("Could not create " + name);
                    copyChildren(resolver, tree, id, target, buffer, depth + 1);
                } else {
                    Uri document = DocumentsContract.buildDocumentUriUsingTree(tree, id);
                    try (InputStream input = resolver.openInputStream(document)) {
                        if (input == null) throw new IOException("Could not read " + name);
                        try (FileOutputStream output = new FileOutputStream(target)) {
                            int count;
                            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                        }
                    }
                }
            }
        }
    }

    private static void verifyExecutable(File exe) throws IOException {
        try (RandomAccessFile input = new RandomAccessFile(exe, "r")) {
            if (input.length() >= 256 && input.readUnsignedShort() == 0x4d5a) {
                input.seek(0x3c);
                long header = Integer.toUnsignedLong(Integer.reverseBytes(input.readInt()));
                if (header >= 64 && header <= input.length() - 26) {
                    input.seek(header);
                    if (input.readInt() == 0x50450000 && input.readUnsignedShort() == 0x4c01) {
                        input.seek(header + 24);
                        if (input.readUnsignedShort() == 0x0b01) return;
                    }
                }
            }
        }
        throw new IOException("RBO_Ex3.exe must be the 32-bit Windows game executable.");
    }

    private static void patchKeys(File directory) throws IOException {
        for (String name : KEY_CONFIGS) {
            File file = new File(directory, name);
            if (file.length() != 328) continue;
            byte[] backup = new byte[328];
            try (RandomAccessFile data = new RandomAccessFile(file, "rw")) {
                data.readFully(backup);
                File original = new File(directory, name + ".bak");
                if (!original.exists()) {
                    try (FileOutputStream out = new FileOutputStream(original)) {
                        out.write(backup);
                    }
                }
                byte[] empty = new byte[36];
                data.seek(0xB8);
                data.write(empty);
                data.seek(0x124);
                data.write(empty);
            }
        }
    }

    static void configurePlayerOne(File game, GameControls controls) throws IOException {
        File saves = caseInsensitiveChild(new File(game, "DATA"), "SaveData");
        Files.createDirectories(saves.toPath());
        Path config = caseInsensitiveChild(saves, "KeyConfig.v4").toPath();
        boolean existed = Files.exists(config);
        if (existed && Files.size(config) != KEY_CONFIG_SIZE) {
            throw new IOException("KeyConfig.v4 has an unrecognized format.");
        }
        byte[] original = existed ? Files.readAllBytes(config) : new byte[KEY_CONFIG_SIZE];
        if (original.length != KEY_CONFIG_SIZE || ByteBuffer.wrap(original).getInt() != 0) {
            throw new IOException("KeyConfig.v4 has an unrecognized format.");
        }
        byte[] configured = original.clone();
        ByteBuffer data = ByteBuffer.wrap(configured).order(ByteOrder.LITTLE_ENDIAN);
        // A record is nine input types, nine device indexes, and nine scan codes.
        // When making a file from scratch, leave the other two keyboards unassigned.
        int players = existed ? 1 : 3;
        for (int player = 0; player < players; player++) {
            int offset = 4 + player * PLAYER_SIZE;
            int row = GameControls.ACTIONS.length * Integer.BYTES;
            for (GameControls.Action action : GameControls.ACTIONS) {
                int field = action.ordinal() * Integer.BYTES;
                data.putInt(offset + field, 1);
                data.putInt(offset + row + field, 0);
                data.putInt(offset + row * 2 + field, player == 0 ? controls.scan(action) : 0);
            }
        }
        if (Arrays.equals(original, configured)) return;

        Path backup = config.resolveSibling(config.getFileName() + ".before-p1.bak");
        if (existed && !Files.exists(backup)) Files.copy(config, backup);
        writeAtomically(config, configured);
    }

    static void writeAtomically(Path target, byte[] data) throws IOException {
        Path pending = Files.createTempFile(target.getParent(), target.getFileName() + "-", ".tmp");
        try {
            Files.write(pending, data);
            Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(pending);
        }
    }

    private static File caseInsensitiveChild(File directory, String name) throws IOException {
        File exact = new File(directory, name);
        if (exact.exists()) return exact;
        File[] matches = directory.listFiles((parent, child) -> child.equalsIgnoreCase(name));
        if (matches == null || matches.length == 0) return exact;
        if (matches.length != 1) throw new IOException("Multiple files match " + name + ".");
        return matches[0];
    }

    private static void deleteTree(File file) throws IOException {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Could not list " + file.getName());
            for (File child : children) deleteTree(child);
        }
        if (!file.delete()) throw new IOException("Could not remove " + file.getName());
    }

    private GameFiles() {}
}
