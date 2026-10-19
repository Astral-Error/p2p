package com.project.p2p.client.ui.common;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/**
 * Modern initials-based avatar component with deterministic palette coloring
 * and optional presence indicator badge.
 */
public class AvatarView extends StackPane {

    private static final String[] PALETTE = {
            "#2f81f7", // Primer Blue
            "#3fb950", // Green
            "#d29922", // Amber
            "#a371f7", // Purple
            "#f778ba", // Pink
            "#79c0ff", // Sky
            "#56d364", // Lime
            "#db6d28"  // Orange
    };

    private final Circle backgroundCircle;
    private final Label initialsLabel;
    private Circle presenceDot;

    public AvatarView(String name, double size) {
        this(name, size, false, false);
    }

    public AvatarView(String name, double size, boolean showPresence, boolean isOnline) {
        setPrefSize(size, size);
        setMinSize(size, size);
        setMaxSize(size, size);

        double radius = size / 2.0;
        String colorHex = getColorForName(name);

        backgroundCircle = new Circle(radius);
        backgroundCircle.setFill(Color.web(colorHex));
        backgroundCircle.setStroke(Color.web("rgba(240, 246, 252, 0.1)"));
        backgroundCircle.setStrokeWidth(1.0);

        String initials = computeInitials(name);
        initialsLabel = new Label(initials);
        initialsLabel.setStyle(String.format(
                "-fx-font-size: %.1fpx; -fx-font-weight: bold; -fx-text-fill: #ffffff;",
                Math.max(10, size * 0.40)
        ));

        getChildren().addAll(backgroundCircle, initialsLabel);

        if (showPresence) {
            setPresence(isOnline, size);
        }
    }

    public void setPresence(boolean isOnline, double size) {
        if (presenceDot == null) {
            double dotRadius = Math.max(3.5, size * 0.16);
            presenceDot = new Circle(dotRadius);
            presenceDot.setStroke(Color.web("#161b22")); // Canvas subtle border
            presenceDot.setStrokeWidth(1.5);
            StackPane.setAlignment(presenceDot, Pos.BOTTOM_RIGHT);
            getChildren().add(presenceDot);
        }
        presenceDot.setFill(isOnline ? Color.web("#3fb950") : Color.web("#6e7681"));
    }

    private static String computeInitials(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        String clean = name.trim().replaceAll("[^a-zA-Z0-9]", "");
        if (clean.isEmpty()) return "?";
        if (clean.length() == 1) return clean.toUpperCase();
        return clean.substring(0, 2).toUpperCase();
    }

    private static String getColorForName(String name) {
        if (name == null || name.isBlank()) {
            return PALETTE[0];
        }
        int hash = Math.abs(name.trim().toLowerCase().hashCode());
        return PALETTE[hash % PALETTE.length];
    }
}
