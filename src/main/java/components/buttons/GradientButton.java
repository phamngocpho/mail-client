package components.buttons;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import javax.swing.JButton;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

public class GradientButton extends JButton {

    public float getSizeSpeed() {
        return sizeSpeed;
    }

    public void setSizeSpeed(float sizeSpeed) {
        this.sizeSpeed = sizeSpeed;
    }

    public Color getColor1() {
        return color1;
    }

    public void setColor1(Color color1) {
        this.color1 = color1;
    }

    public Color getColor2() {
        return color2;
    }

    public void setColor2(Color color2) {
        this.color2 = color2;
    }

    private Color color1 = Color.decode("#1A1A1A");
    private Color color2 = Color.decode("#1A1A1A");
    private final Timer timer;
    private final Timer timerPressed;
    private float alpha = 0.3f;
    private boolean mouseOver;
    private boolean pressed;
    private Point pressedLocation;
    private float pressedSize;
    private float sizeSpeed = 12f;
    private float alphaPressed = 0.5f;
    private boolean customAnimation = true;

    public boolean isCustomAnimation() {
        return customAnimation;
    }

    public void setCustomAnimation(boolean customAnimation) {
        this.customAnimation = customAnimation;
        if (!customAnimation) {
            // Dừng tất cả animation khi tắt
            timer.stop();
            timerPressed.stop();
            alpha = 0.3f;
            pressed = false;
            mouseOver = false;
            // Bật contentAreaFilled để Swing vẽ hiệu ứng mặc định
            setContentAreaFilled(true);
        } else {
            setContentAreaFilled(false);
        }
        repaint();
    }

    public GradientButton() {
        setContentAreaFilled(false);
        setForeground(Color.WHITE);
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setBorder(new EmptyBorder(10, 20, 10, 20));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent me) {
                if (customAnimation) {
                    mouseOver = true;
                    timer.start();
                }
            }

            @Override
            public void mouseExited(MouseEvent me) {
                if (customAnimation) {
                    mouseOver = false;
                    timer.start();
                }
            }

            @Override
            public void mousePressed(MouseEvent me) {
                if (customAnimation) {
                    pressedSize = 0;
                    alphaPressed = 0.5f;
                    pressed = true;
                    pressedLocation = me.getPoint();
                    timerPressed.setDelay(0);
                    timerPressed.start();
                }
            }
        });
        timer = new Timer(40, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                if (mouseOver) {
                    if (alpha < 0.6f) {
                        alpha += 0.05f;
                        repaint();
                    } else {
                        alpha = 0.6f;
                        timer.stop();
                        repaint();
                    }
                } else {
                    if (alpha > 0.3f) {
                        alpha -= 0.05f;
                        repaint();
                    } else {
                        alpha = 0.3f;
                        timer.stop();
                        repaint();
                    }
                }
            }
        });
        timerPressed = new Timer(0, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                pressedSize += getSizeSpeed();
                if (alphaPressed <= 0) {
                    pressed = false;
                    timerPressed.stop();
                } else {
                    repaint();
                }
            }
        });
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g2d = (Graphics2D) graphics;
        int width = getWidth();
        int height = getHeight();

        // Nếu không dùng custom animation, vẽ theo cách khác
        if (!customAnimation) {
            // Cài đặt rendering hints chất lượng cao nhất
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

            // Vẽ gradient background với bo tròn
            RoundRectangle2D.Float roundRect = new RoundRectangle2D.Float(0, 0, width, height, height, height);
            GradientPaint gra = new GradientPaint(0, 0, color1, width, 0, color2);
            g2d.setPaint(gra);
            g2d.fill(roundRect);

            // Set clip để Swing chỉ vẽ trong vùng bo tròn
            g2d.setClip(roundRect);
            super.paintComponent(graphics);
            return;
        }

        // Code cũ cho custom animation
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();

        // Cài đặt rendering hints chất lượng cao nhất
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        // Tạo gradient color
        GradientPaint gra = new GradientPaint(0, 0, color1, width, 0, color2);
        g2.setPaint(gra);

        // Sử dụng RoundRectangle2D.Float để vẽ bo tròn mượt mà hơn
        RoundRectangle2D.Float roundRect = new RoundRectangle2D.Float(0, 0, width, height, height, height);
        g2.fill(roundRect);

        // Add Style
        createStyle(g2);

        if (pressed) {
            paintPressed(g2);
        }

        g2.dispose();

        // Vẽ lên component với rendering hints tốt nhất
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.drawImage(img, 0, 0, null);

        super.paintComponent(graphics);
    }

    private void createStyle(Graphics2D g2) {
        if (!color1.equals(color2)) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, alpha));
            int width = getWidth();
            int height = getHeight();
            GradientPaint gra = new GradientPaint(0, 0, Color.WHITE, 0, height, new Color(255, 255, 255, 60));
            g2.setPaint(gra);

            // Vẽ curve với độ mượt cao hơn
            Path2D.Float f = new Path2D.Float();
            f.moveTo(0, 0);
            int control = height + height / 2;
            f.curveTo(0, 0, (float) width / 2, control, width, 0);

            // Clip theo hình dạng của button để curve không vượt ra ngoài
            RoundRectangle2D.Float clip = new RoundRectangle2D.Float(0, 0, width, height, height, height);
            g2.setClip(clip);
            g2.fill(f);
            g2.setClip(null);
        }
    }

    private void paintPressed(Graphics2D g2) {
        if (pressedLocation.x - (pressedSize / 2) < 0 && pressedLocation.x + (pressedSize / 2) > getWidth()) {
            timerPressed.setDelay(20);
            alphaPressed -= 0.05f;
            if (alphaPressed < 0) {
                alphaPressed = 0;
            }
        }
        g2.setColor(new Color(150, 150, 150));
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, alphaPressed));
        float x = pressedLocation.x - (pressedSize / 2);
        float y = pressedLocation.y - (pressedSize / 2);

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.fillOval((int) x, (int) y, (int) pressedSize, (int) pressedSize);
    }
}