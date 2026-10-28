package org.example.db;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRegistration;
import org.h2.server.web.JakartaWebServlet;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Publica la consola web de H2 dentro de la propia aplicación, en /h2-console/.
 *
 * Así, con solo arrancar la aplicación se puede ver también la base de datos desde el
 * navegador, sin instalar ningún cliente. La pantalla de acceso ya trae la conexión
 * de TaskFlow cargada: basta con pulsar "Connect".
 *
 * Es una herramienta de desarrollo y de evaluación. Se puede desactivar con la variable
 * de entorno TF_H2_CONSOLE=false (o con -Dtf.h2.console=false).
 */
public final class H2ConsoleSetup {

    static final String SERVLET_NAME = "h2-console";
    static final String MAPPING = "/h2-console/*";

    /** Nombre del archivo de conexiones guardadas que H2 busca dentro de su carpeta. */
    static final String SETTINGS_FILE = ".h2.server.properties";

    private static final String CONNECTION_NAME = "TaskFlow";

    private H2ConsoleSetup() {
    }

    /** Está activada salvo que se desactive de forma explícita. */
    public static boolean isEnabled() {
        String value = System.getProperty("tf.h2.console");
        if (value == null || value.isBlank()) {
            value = System.getenv("TF_H2_CONSOLE");
        }
        return isEnabled(value);
    }

    public static boolean isEnabled(String value) {
        return value == null || !value.trim().equalsIgnoreCase("false");
    }

    public static void register(ServletContext context) {
        register(context, isEnabled());
    }

    public static void register(ServletContext context, boolean enabled) {
        if (!enabled) {
            context.log("H2 web console is disabled (TF_H2_CONSOLE=false)");
            return;
        }

        // La consola es una comodidad: si algo falla, la aplicación debe seguir arrancando.
        try {
            Path dir = Files.createTempDirectory("taskflow-h2-console");
            Path file = writeSettings(dir, Database.url(), Database.user());
            dir.toFile().deleteOnExit();
            file.toFile().deleteOnExit();   // se borra antes que la carpeta (orden inverso de registro)

            ServletRegistration.Dynamic registration = context.addServlet(SERVLET_NAME, new JakartaWebServlet());
            if (registration == null) {
                context.log("H2 web console not registered: a servlet named '" + SERVLET_NAME + "' already exists");
                return;
            }
            registration.setInitParameter("properties", dir.toString());   // carpeta con la conexión precargada
            registration.setInitParameter("webAllowOthers", "");           // necesario para entrar desde fuera de un contenedor Docker
            registration.setInitParameter("ifExists", "");                 // solo abre bases que ya existen
            registration.addMapping(MAPPING);

            context.log("H2 web console available at " + context.getContextPath() + "/h2-console/"
                    + " (JDBC URL: " + Database.url() + ", user: " + Database.user() + ", empty password)");
        } catch (IOException | RuntimeException e) {
            context.log("H2 web console could not be started; the application keeps running", e);
        }
    }

    /**
     * Escribe el archivo de conexiones guardadas que lee la consola de H2.
     * Cada conexión se guarda con el formato: nombre|driver|url|usuario
     */
    public static Path writeSettings(Path dir, String jdbcUrl, String user) throws IOException {
        Properties settings = new Properties();
        settings.setProperty("0", String.join("|", CONNECTION_NAME, "org.h2.Driver", jdbcUrl, user));

        Path file = dir.resolve(SETTINGS_FILE);
        // H2 lee este archivo en ISO-8859-1; store(OutputStream) escribe los caracteres
        // especiales (por ejemplo tildes en la ruta) de forma que se lean bien.
        try (OutputStream out = Files.newOutputStream(file)) {
            settings.store(out, "TaskFlow: connection preloaded in the H2 web console");
        }
        return file;
    }
}
