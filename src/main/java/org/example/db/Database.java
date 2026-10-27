package org.example.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {

    private static final String DB_PATH = resolveDbPath();

    // AUTO_SERVER=TRUE permite que IntelliJ y la app usen el archivo a la vez.
    private static final String URL = "jdbc:h2:" + DB_PATH + ";AUTO_SERVER=TRUE";

    private static final String USER =
            System.getenv().getOrDefault("TF_DB_USER", "sa");
    private static final String PASSWORD =
            System.getenv().getOrDefault("TF_DB_PASSWORD", "");

    // Dentro de Tomcat, el driver está en WEB-INF/lib y DriverManager no lo detecta
    // solo: hay que cargarlo explícitamente para que se registre.
    static {
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("No se encontró el driver de H2 en el classpath", e);
        }
    }

    private Database() {
    }

    private static String resolveDbPath() {
        String path = System.getProperty("tf.db.path");
        if (path == null || path.isBlank()) {
            path = System.getenv().getOrDefault("TF_DB_PATH", "./data/taskflow");
        }
        return path.replace('\\', '/');   // H2 prefiere "/" también en Windows
    }

    public static Connection get() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    /** Crea la tabla solo si no existe (los datos NO se borran al reiniciar). */
    public static void init() {
        String sql = """
                CREATE TABLE IF NOT EXISTS tasks (
                    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                    title       VARCHAR(200) NOT NULL,
                    description VARCHAR(1000),
                    status      VARCHAR(20)  NOT NULL DEFAULT 'Pending',
                    priority    VARCHAR(10)  NOT NULL DEFAULT 'Medium',
                    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT chk_status   CHECK (status IN ('Pending', 'In Progress', 'Completed')),
                    CONSTRAINT chk_priority CHECK (priority IN ('Low', 'Medium', 'High'))
                )
                """;
        try (Connection con = get(); Statement st = con.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo inicializar la base de datos", e);
        }
    }
}