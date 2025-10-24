package components.panels.dashboard;

import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;

/**
 * Skeleton loading panel - modern placeholder animation
 * Shows email list skeleton while loading data
 */
public class Loading extends JPanel {
    private Timer shimmerTimer;
    private float shimmerPosition = 0f;
    private long showStartTime = 0;
    private static final int MIN_DISPLAY_DURATION_MS = 400;
    private Runnable pendingHideCallback = null;

    public Loading() {
        setLayout(new MigLayout("fill, insets 0", "[grow]", ""));
        setOpaque(true);
        setBackground(UIManager.getColor("Panel.background"));

        // Create skeleton items
        for (int i = 0; i < 15; i++) {
            add(createSkeletonEmailRow(), "growx, wrap, h 48!");
        }

        // Start shimmer animation
        startShimmerAnimation();
    }

    /**
     * Create a skeleton row that looks like an email item
     */
    private JPanel createSkeletonEmailRow() {
        JPanel row = new JPanel(new MigLayout("insets 12 16", "[20!]10[20!]10[120!]10[grow]10[60!]", "center"));
        row.setOpaque(true);
        row.setBackground(UIManager.getColor("Panel.background"));
        row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0,
                UIManager.getColor("Separator.foreground")));

        // Checkbox
        row.add(createSkeletonIcon());

        // Star icon
        row.add(createSkeletonIcon());

        // Sender name
        row.add(createSkeletonShape(120, 16), "");

        // Subject (takes remaining space)
        row.add(createSkeletonShape(400, 16), "growx");

        // Time/Date
        row.add(createSkeletonShape(60, 16), "");

        return row;
    }

    /**
     * Create a skeleton shape with custom dimensions
     */
    private JPanel createSkeletonShape(int width, int height) {
        JPanel shape = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Base skeleton color
                Color baseColor = UIManager.getColor("Component.borderColor");
                if (baseColor == null) {
                    baseColor = new Color(200, 200, 200);
                }

                // Draw rounded rectangle
                g2.setColor(baseColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());

                // Shimmer effect
                if (shimmerPosition > 0 && getWidth() > 0) {
                    LinearGradientPaint shimmer = getLinearGradientPaint();
                    g2.setPaint(shimmer);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                }

                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(width, height);
            }

            @Override
            public Dimension getMinimumSize() {
                return new Dimension(width, height);
            }

            @Override
            public Dimension getMaximumSize() {
                return new Dimension(width, height);
            }
        };
        shape.setOpaque(false);
        return shape;
    }

    /**
     * Create a skeleton icon (20x20 square) for checkbox/star icons
     */
    private JPanel createSkeletonIcon() {
        return createSkeletonShape(20, 20);
    }

    private LinearGradientPaint getLinearGradientPaint() {
        float[] fractions = {0.0f, 0.5f, 1.0f};
        Color[] colors = {
                new Color(255, 255, 255, 0),
                new Color(255, 255, 255, 20),
                new Color(255, 255, 255, 0)
        };

        return new LinearGradientPaint(
                shimmerPosition - 100, 0,
                shimmerPosition + 100, 0,
                fractions, colors
        );
    }

    /**
     * Start shimmer animation effect
     */
    private void startShimmerAnimation() {
        shimmerTimer = new Timer(30, e -> {
            shimmerPosition += 10;
            if (shimmerPosition > getWidth() + 200) {
                shimmerPosition = -200;
            }
            repaint();
        });
        shimmerTimer.start();
    }

    /**
     * Stop animation when component is no longer visible
     */
    @Override
    public void setVisible(boolean visible) {
        if (visible) {
            showStartTime = System.currentTimeMillis();
            pendingHideCallback = null;
            super.setVisible(true);
            if (shimmerTimer != null) {
                shimmerTimer.start();
            }
        } else {
            hideWithMinimumDuration(null);
        }
    }

    /**
     * Hide with minimum display duration to prevent flicker
     */
    public void hideWithMinimumDuration(Runnable callback) {
        if (!isVisible()) {
            if (callback != null) callback.run();
            return;
        }

        long elapsedTime = System.currentTimeMillis() - showStartTime;
        long remainingTime = MIN_DISPLAY_DURATION_MS - elapsedTime;

        if (remainingTime > 0) {
            pendingHideCallback = callback;
            Timer delayTimer = new Timer((int) remainingTime, e -> {
                actuallyHide();
                if (pendingHideCallback != null) {
                    pendingHideCallback.run();
                    pendingHideCallback = null;
                }
            });
            delayTimer.setRepeats(false);
            delayTimer.start();
        } else {
            actuallyHide();
            if (callback != null) callback.run();
        }
    }

    /**
     * Internal method to actually hide the panel
     */
    private void actuallyHide() {
        if (shimmerTimer != null) {
            shimmerTimer.stop();
        }
        super.setVisible(false);
    }

    /**
     * Cleanup
     */
    @Override
    public void removeNotify() {
        super.removeNotify();
        if (shimmerTimer != null) {
            shimmerTimer.stop();
            shimmerTimer = null;
        }
    }

    // Backward compatibility methods
    public void setMessage(String message) {}
    public void setSubMessage(String subMessage) {}
    public void setMessages(String message, String subMessage) {}
}