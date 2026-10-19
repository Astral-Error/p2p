package com.project.p2p.client.ui.common;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/**
 * Cohesive vector icon system based on GitHub Primer and Feather SVG geometry.
 * Scalable, DPI-independent, and accessible without emoji substitutes.
 */
public final class AppIcons {

    private AppIcons() {}

    public enum Icon {
        SHIELD("M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z", false),
        SHIELD_CHECK("M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z M9 12l2 2 4-4", false),
        SHIELD_ALERT("M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z M12 8v4 M12 16h.01", false),
        LOCK("M19 11H5a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7a2 2 0 0 0-2-2zm-12 0V7a5 5 0 0 1 10 0v4", false),
        CHECK("M20 6L9 17l-5-5", false),
        DOUBLE_CHECK("M18 6L8 16l-4-4 M22 10l-8 8", false),
        CLOCK("M12 22c5.523 0 10-4.477 10-10S17.523 2 12 2 2 6.477 2 12s4.477 10 10 10z M12 6v6l4 2", false),
        ALERT_CIRCLE("M12 22c5.523 0 10-4.477 10-10S17.523 2 12 2 2 6.477 2 12s4.477 10 10 10z M12 8v4 M12 16h.01", false),
        REFRESH("M23 4v6h-6 M1 20v-6h6 M3.51 9a9 9 0 0 1 14.85-3.36L23 10 M1 14l4.64 4.36A9 9 0 0 0 20.49 15", false),
        SEARCH("M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16z M21 21l-4.35-4.35", false),
        COPY("M9 9h11a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2V11a2 2 0 0 1 2-2zm-4 4H4a2 2 0 0 1-2-2V3a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v1", false),
        SEND("M22 2L11 13 M22 2l-7 20-4-9-9-4 20-7z", false),
        SIDEBAR("M19 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V5a2 2 0 0 0-2-2zm-5 16V5h5v14h-5z", false),
        SUN("M12 1v2 M12 21v2 M4.22 4.22l1.42 1.42 M18.36 18.36l1.42 1.42 M1 12h2 M21 12h2 M4.22 19.78l1.42-1.42 M18.36 5.64l1.42-1.42 M12 17a5 5 0 1 0 0-10 5 5 0 0 0 0 10z", false),
        MOON("M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z", false),
        KEY("M21 2l-2 2m-1.5 1.5L14 9l-1.5-1.5-2.83 2.83a5 5 0 1 0 2.83 2.83L17 8.5 15.5 7l2-2 3.5 3.5 2-2L21 2z", false),
        RADIO("M4.93 4.93a10 10 0 0 1 14.14 0 M7.76 7.76a6 6 0 0 1 8.48 0 M12 12a1 1 0 1 1 0-2 1 1 0 0 1 0 2z", false),
        USER("M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2 M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z", false),
        USERS("M17 21v-2a4 4 0 0 0-3-3.87 M7 23H3v-2a4 4 0 0 1 4-4h4 M16 3.13a4 4 0 0 1 0 7.75 M8 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z", false),
        CLOSE("M18 6L6 18 M6 6l12 12", false);

        private final String path;
        private final boolean filled;

        Icon(String path, boolean filled) {
            this.path = path;
            this.filled = filled;
        }

        public String getPath() {
            return path;
        }

        public boolean isFilled() {
            return filled;
        }
    }

    public static Node create(Icon icon) {
        return create(icon, 16, null);
    }

    public static Node create(Icon icon, double size) {
        return create(icon, size, null);
    }

    public static Node create(Icon icon, double size, Paint color) {
        SVGPath svg = new SVGPath();
        svg.setContent(icon.getPath());

        Paint paint = (color != null) ? color : Color.web("#8b949e");

        if (icon.isFilled()) {
            svg.setFill(paint);
            svg.setStroke(null);
        } else {
            svg.setFill(Color.TRANSPARENT);
            svg.setStroke(paint);
            svg.setStrokeWidth(1.75);
            svg.setStrokeLineCap(StrokeLineCap.ROUND);
            svg.setStrokeLineJoin(StrokeLineJoin.ROUND);
        }

        // Scale geometry to fit target size assuming standard 24x24 viewBox
        double scale = size / 24.0;
        svg.setScaleX(scale);
        svg.setScaleY(scale);

        StackPane container = new StackPane(svg);
        container.setPrefSize(size, size);
        container.setMinSize(size, size);
        container.setMaxSize(size, size);
        container.setAlignment(Pos.CENTER);
        return container;
    }
}
