package org.example.db;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/**
 * Tomcat ejecuta esta clase una sola vez al arrancar la aplicación.
 * Aquí aprovechamos para crear la tabla si no existe.
 */
@WebListener
public class AppInitializer implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        Database.init();
    }
}