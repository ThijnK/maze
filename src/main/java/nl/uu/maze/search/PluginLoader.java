package nl.uu.maze.search;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

/** One parent-first loader per run; no class or instance registry. */
final class PluginLoader implements AutoCloseable {
    private final List<Path> jars;
    private final URLClassLoader loader;
    private final List<Map<String, String>> artifacts;

    PluginLoader(List<Path> paths) throws IOException {
        var normalized = new ArrayList<Path>();
        var identities = new ArrayList<Map<String, String>>();
        for (Path path : paths) {
            Path jar = path.toRealPath();
            if (normalized.contains(jar)) throw new IllegalArgumentException("Plugin JAR supplied twice: " + jar);
            try (var ignored = new JarFile(jar.toFile())) {
                identities.add(Map.of("path", jar.toString(), "sha256", sha256(jar)));
            }
            normalized.add(jar);
        }
        jars = List.copyOf(normalized);
        artifacts = List.copyOf(identities);
        var urls = new java.net.URL[jars.size()];
        for (int i = 0; i < urls.length; i++) urls[i] = jars.get(i).toUri().toURL();
        loader = new URLClassLoader(urls, nl.uu.maze.search.strategy.SearchStrategy.class.getClassLoader());
    }

    List<Map<String, String>> artifacts() { return artifacts; }

    @SuppressWarnings("removal") // ThreadDeath is fatal even on JVMs where it is deprecated.
    Object construct(String name, Class<?> base, Class<?>[] parameters, Object[] arguments, String location) {
        try {
            String entry = name.replace('.', '/') + ".class";
            var definitions = new ArrayList<Path>();
            for (Path path : jars) {
                try (var jar = new JarFile(path.toFile(), true, JarFile.OPEN_READ, Runtime.version())) {
                    if (jar.getJarEntry(entry) != null) definitions.add(path);
                }
            }
            if (definitions.size() > 1) throw new IllegalArgumentException("ambiguous implementation in " + definitions);
            Class<?> type = Class.forName(name, false, loader);
            if (!base.isAssignableFrom(type)) throw new IllegalArgumentException("must extend " + base.getName());
            return type.getConstructor(parameters).newInstance(arguments);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof VirtualMachineError fatal) throw fatal;
            if (cause instanceof ThreadDeath fatal) throw fatal;
            throw new IllegalArgumentException(location + " (" + name + "): constructor failed: " + cause, cause);
        } catch (ReflectiveOperationException | IOException | Error | IllegalArgumentException e) {
            if (e instanceof VirtualMachineError fatal) throw fatal;
            if (e instanceof ThreadDeath fatal) throw fatal;
            throw new IllegalArgumentException(location + " (" + name + "): cannot construct " + base.getSimpleName()
                    + "; expected public constructor " + java.util.Arrays.toString(parameters) + ": " + e, e);
        }
    }

    static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[65536];
                for (int n; (n = input.read(buffer)) != -1;) digest.update(buffer, 0, n);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM lacks SHA-256", e);
        }
    }

    @Override public void close() throws IOException { loader.close(); }

}
