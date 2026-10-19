package com.project.p2p.client.ui.common;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Theme;
import javafx.application.Application;
import javafx.scene.Scene;

import java.net.URL;
import java.util.prefs.Preferences;

/**
 * Manages GitHub Primer application themes (Primer Dark default, Primer Light optional).
 * Applies user-agent stylesheets and custom app design tokens safely.
 */
public final class ThemeManager {

    private static final String PREF_DARK_MODE = "p2p_dark_mode_enabled";
    private static final Preferences PREFS = Preferences.userNodeForPackage(ThemeManager.class);

    private static boolean darkMode = PREFS.getBoolean(PREF_DARK_MODE, true);

    private ThemeManager() {}

    /**
     * Initializes the default theme on application startup.
     */
    public static void initialize() {
        applyTheme(darkMode);
    }

    public static boolean isDarkMode() {
        return darkMode;
    }

    public static void toggleTheme(Scene... scenes) {
        setDarkMode(!darkMode, scenes);
    }

    public static void setDarkMode(boolean isDark, Scene... scenes) {
        darkMode = isDark;
        PREFS.putBoolean(PREF_DARK_MODE, isDark);
        applyTheme(isDark);
        for (Scene s : scenes) {
            if (s != null) {
                applyCustomStylesheet(s);
            }
        }
    }

    private static void applyTheme(boolean isDark) {
        Theme theme = isDark ? new PrimerDark() : new PrimerLight();
        Application.setUserAgentStylesheet(theme.getUserAgentStylesheet());
    }

    /**
     * Attaches the app's custom theme token stylesheet and active theme class to a scene.
     */
    public static void applyCustomStylesheet(Scene scene) {
        if (scene == null) return;
        URL resource = ThemeManager.class.getResource("/styles/theme.css");
        if (resource != null) {
            String cssUrl = resource.toExternalForm();
            if (!scene.getStylesheets().contains(cssUrl)) {
                scene.getStylesheets().add(cssUrl);
            }
        }
        if (scene.getRoot() != null) {
            scene.getRoot().getStyleClass().removeAll("theme-dark", "theme-light");
            scene.getRoot().getStyleClass().add(darkMode ? "theme-dark" : "theme-light");
        }
    }
}
