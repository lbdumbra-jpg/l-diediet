package com.lumbra.ldiediet.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class AtomicJsonFile {
    private AtomicJsonFile() {}

    public static void write(Path destination, Gson gson, JsonElement data) throws IOException {
        Files.createDirectories(destination.toAbsolutePath().getParent());
        Path temp = Files.createTempFile(destination.toAbsolutePath().getParent(), "ldiediet-", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) { gson.toJson(data, writer); }
            try {
                Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temp); }
    }
}
