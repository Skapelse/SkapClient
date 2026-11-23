package com.skapclient;

import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;

public final class Config {
    public static Theme theme = Theme.PURPLE; // default: Dark Purple
    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("skapclient.txt"); }

    public static void load() {
        try {
            if (Files.exists(file())) theme = Theme.valueOf(Files.readString(file()).trim());
        } catch (Exception ignored) { theme = Theme.PURPLE; }
    }

    public static void save() {
        try { Files.writeString(file(), theme.name()); } catch (Exception ignored) { }
    }
}
