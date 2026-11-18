package dev.chysalis.propagate.config;

import dev.chysalis.propagate.Propagate;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes {@code config/propagate.json}. */
public final class ConfigFile {
    private ConfigFile() {}

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("propagate.json");
    }

    /** Loads the file (writing the defaults first if it is missing). Problems are logged and returned. */
    public static Settings load(List<String> problems) {
        Path p = path();
        try {
            if (!Files.exists(p)) {
                Settings d = Settings.defaults();
                save(d);
                return d;
            }
            Settings s = Settings.parse(Files.readString(p, StandardCharsets.UTF_8), problems);
            for (String msg : problems) Propagate.LOG.warn("Propagate config: {}", msg);
            return s;
        } catch (IOException e) {
            problems.add("could not read " + p + ": " + e.getMessage());
            Propagate.LOG.warn("Propagate: could not read {}, using the defaults", p, e);
            return Settings.defaults();
        }
    }

    public static Settings load() {
        return load(new ArrayList<>());
    }

    public static boolean save(Settings s) {
        Path p = path();
        try {
            Files.createDirectories(p.getParent());
            Files.writeString(p, s.toFileText(), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            Propagate.LOG.warn("Propagate: could not write {}", p, e);
            return false;
        }
    }
}
