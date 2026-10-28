package org.example.h2;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRegistration;
import org.example.db.Database;
import org.h2.engine.Constants;
import org.h2.server.web.ConnectionInfo;
import org.h2.server.web.JakartaWebServlet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class H2ConsoleSetupTest {

    // ---------- Activación ----------

    @Test
    void isEnabledUnlessExplicitlyTurnedOff() {
        assertTrue(H2ConsoleSetup.isEnabled((String) null));
        assertTrue(H2ConsoleSetup.isEnabled(""));
        assertTrue(H2ConsoleSetup.isEnabled("true"));
        assertFalse(H2ConsoleSetup.isEnabled("false"));
        assertFalse(H2ConsoleSetup.isEnabled("FALSE"));
        assertFalse(H2ConsoleSetup.isEnabled("  false  "));
    }

    // ---------- Archivo de conexión precargada ----------

    /** Lee el archivo igual que lo hace H2 (ISO-8859-1) y devuelve la entrada "0". */
    private static ConnectionInfo readLikeH2(Path file) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(new InputStreamReader(in, StandardCharsets.ISO_8859_1));
        }
        return new ConnectionInfo(properties.getProperty("0"));
    }

    @Test
    void settingsFileUsesTheNameH2Expects(@TempDir Path dir) throws IOException {
        Path file = H2ConsoleSetup.writeSettings(dir, "jdbc:h2:/data/taskflow", "sa");

        assertEquals(Constants.SERVER_PROPERTIES_NAME, file.getFileName().toString());
        assertEquals(dir, file.getParent());
    }

    @Test
    void settingsFileIsParsedCorrectlyByH2(@TempDir Path dir) throws IOException {
        String url = "jdbc:h2:/data/taskflow;AUTO_SERVER=TRUE";

        ConnectionInfo info = readLikeH2(H2ConsoleSetup.writeSettings(dir, url, "sa"));

        assertEquals("org.h2.Driver", info.driver);
        assertEquals(url, info.url);
        assertEquals("sa", info.user);
    }

    @Test
    void pathsWithAccentsSurviveTheRoundTrip(@TempDir Path dir) throws IOException {
        String url = "jdbc:h2:C:/Users/José Núñez/TaskFlow/data/taskflow;AUTO_SERVER=TRUE";

        ConnectionInfo info = readLikeH2(H2ConsoleSetup.writeSettings(dir, url, "sa"));

        assertEquals(url, info.url);
    }

    // ---------- Registro del servlet (con un ServletContext simulado) ----------

    @Test
    void registersTheConsoleServletWithTheExpectedConfiguration() throws IOException {
        FakeContext context = new FakeContext();

        H2ConsoleSetup.register(context.proxy(), true);

        assertEquals("h2-console", context.servletName);
        assertInstanceOf(JakartaWebServlet.class, context.servlet);
        assertEquals(List.of("/h2-console/*"), context.mappings);
        assertEquals("", context.initParams.get("webAllowOthers"));
        assertEquals("", context.initParams.get("ifExists"));

        // La carpeta indicada contiene la conexión con la URL real de la aplicación
        Path settings = Path.of(context.initParams.get("properties")).resolve(Constants.SERVER_PROPERTIES_NAME);
        assertTrue(Files.exists(settings));
        assertEquals(Database.url(), readLikeH2(settings).url);
    }

    @Test
    void logsWhereTheConsoleCanBeOpened() {
        FakeContext context = new FakeContext();

        H2ConsoleSetup.register(context.proxy(), true);

        assertTrue(context.logs.stream().anyMatch(line -> line.contains("/TaskFlow/h2-console/")), context.logs.toString());
    }

    @Test
    void registersNothingWhenDisabled() {
        FakeContext context = new FakeContext();

        H2ConsoleSetup.register(context.proxy(), false);

        assertNull(context.servletName);
        assertTrue(context.mappings.isEmpty());
    }

    @Test
    void doesNotBreakStartupIfTheServletNameIsAlreadyTaken() {
        FakeContext context = new FakeContext();
        context.returnNullRegistration = true;   // así responde un contenedor si el nombre ya existe

        H2ConsoleSetup.register(context.proxy(), true);   // no debe lanzar ninguna excepción

        assertTrue(context.mappings.isEmpty());
        assertTrue(context.logs.stream().anyMatch(line -> line.contains("already exists")), context.logs.toString());
    }

    /** ServletContext mínimo, creado con un proxy: guarda lo que la aplicación registra. */
    private static final class FakeContext {
        final List<String> logs = new ArrayList<>();
        final Map<String, String> initParams = new LinkedHashMap<>();
        final List<String> mappings = new ArrayList<>();
        String servletName;
        Servlet servlet;
        boolean returnNullRegistration;

        ServletContext proxy() {
            return (ServletContext) Proxy.newProxyInstance(
                    ServletContext.class.getClassLoader(), new Class<?>[] {ServletContext.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "addServlet" -> {
                            servletName = (String) args[0];
                            servlet = (Servlet) args[1];
                            yield returnNullRegistration ? null : registration();
                        }
                        case "log" -> {
                            logs.add(String.valueOf(args[0]));
                            yield null;
                        }
                        case "getContextPath" -> "/TaskFlow";
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private ServletRegistration.Dynamic registration() {
            return (ServletRegistration.Dynamic) Proxy.newProxyInstance(
                    ServletRegistration.Dynamic.class.getClassLoader(), new Class<?>[] {ServletRegistration.Dynamic.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "setInitParameter" -> {
                            initParams.put((String) args[0], (String) args[1]);
                            yield true;
                        }
                        case "addMapping" -> {
                            mappings.addAll(List.of((String[]) args[0]));
                            yield Set.of();
                        }
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive() || type == void.class) return null;
            if (type == boolean.class) return false;
            if (type == char.class) return '\0';
            if (type == long.class) return 0L;
            if (type == float.class) return 0f;
            if (type == double.class) return 0d;
            if (type == short.class) return (short) 0;
            if (type == byte.class) return (byte) 0;
            return 0;
        }
    }
}
