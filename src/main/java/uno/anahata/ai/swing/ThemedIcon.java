/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.swing;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.Icon;
import uno.anahata.ai.swing.SwingChatConfig.UITheme;

/**
 * A programmatically drawn, theme-aware icon for the Anahata toolbar.
 */
public class ThemedIcon implements Icon {

    public enum Type {
        RESTART, ATTACH, SCREENSHOT, FRAMES, SAVE, LOAD, JAVA, GOOGLE, LIVE, KILL, BELL, BELL_MUTE
    }

    private final Type type;
    private final int size;

    public ThemedIcon(Type type, int size) {
        this.type = type;
        this.size = size;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        
        Color color = UITheme.get().getFontColor();
        if (!c.isEnabled()) {
            color = UITheme.get().getSecondaryFontColor();
        }
        g2.setColor(color);
        g2.translate(x, y);

        float s = size;
        float p = s * 0.15f; // Padding
        float w = s - (p * 2);
        float h = s - (p * 2);

        switch (type) {
            case RESTART:
                g2.setStroke(new BasicStroke(s * 0.08f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new Arc2D.Float(p, p, w, h, 45, 270, Arc2D.OPEN));
                // Arrow head
                Path2D arrow = new Path2D.Float();
                arrow.moveTo(s * 0.7f, s * 0.1f);
                arrow.lineTo(s * 0.85f, s * 0.25f);
                arrow.lineTo(s * 0.6f, s * 0.35f);
                g2.fill(arrow);
                break;

            case ATTACH:
                g2.setStroke(new BasicStroke(s * 0.06f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D clip = new Path2D.Float();
                clip.moveTo(s * 0.75f, s * 0.35f);
                clip.lineTo(s * 0.75f, s * 0.7f);
                clip.curveTo(s * 0.75f, s * 0.9f, s * 0.25f, s * 0.9f, s * 0.25f, s * 0.7f);
                clip.lineTo(s * 0.25f, s * 0.25f);
                clip.curveTo(s * 0.25f, s * 0.05f, s * 0.65f, s * 0.05f, s * 0.65f, s * 0.25f);
                clip.lineTo(s * 0.65f, s * 0.65f);
                clip.curveTo(s * 0.65f, s * 0.75f, s * 0.35f, s * 0.75f, s * 0.35f, s * 0.65f);
                clip.lineTo(s * 0.35f, s * 0.35f);
                g2.draw(clip);
                break;

            case SCREENSHOT:
                g2.setStroke(new BasicStroke(s * 0.06f));
                g2.draw(new RoundRectangle2D.Float(p, p, w, h * 0.7f, 2, 2)); // Screen
                g2.fill(new Rectangle2D.Float(s * 0.4f, s * 0.75f, s * 0.2f, s * 0.05f)); // Neck
                g2.fill(new Rectangle2D.Float(s * 0.3f, s * 0.8f, s * 0.4f, s * 0.05f)); // Base
                break;

            case FRAMES:
                g2.setStroke(new BasicStroke(s * 0.05f));
                // Back frame
                g2.draw(new Rectangle2D.Float(p, p, w * 0.7f, h * 0.7f));
                g2.fill(new Rectangle2D.Float(p, p, w * 0.7f, h * 0.15f)); // Back title bar
                
                // Front frame
                float fx = p + w * 0.3f;
                float fy = p + h * 0.3f;
                float fw = w * 0.7f;
                float fh = h * 0.7f;
                
                g2.setColor(c.getBackground());
                g2.fill(new Rectangle2D.Float(fx, fy, fw, fh));
                g2.setColor(color);
                g2.draw(new Rectangle2D.Float(fx, fy, fw, fh));
                g2.fill(new Rectangle2D.Float(fx, fy, fw, fh * 0.15f)); // Front title bar
                break;

            case SAVE:
                g2.setStroke(new BasicStroke(s * 0.06f));
                Path2D disk = new Path2D.Float();
                disk.moveTo(p, p);
                disk.lineTo(s - p - (w * 0.2f), p);
                disk.lineTo(s - p, p + (h * 0.2f));
                disk.lineTo(s - p, s - p);
                disk.lineTo(p, s - p);
                disk.closePath();
                g2.draw(disk);
                g2.fill(new Rectangle2D.Float(s * 0.3f, p, s * 0.4f, s * 0.25f)); // Top label
                g2.draw(new Rectangle2D.Float(s * 0.25f, s * 0.55f, s * 0.5f, s * 0.3f)); // Bottom shutter
                break;

            case LOAD:
                g2.setStroke(new BasicStroke(s * 0.06f));
                Path2D folder = new Path2D.Float();
                folder.moveTo(p, p + h * 0.2f);
                folder.lineTo(p + w * 0.4f, p + h * 0.2f);
                folder.lineTo(p + w * 0.5f, p);
                folder.lineTo(s - p, p);
                folder.lineTo(s - p, s - p);
                folder.lineTo(p, s - p);
                folder.closePath();
                g2.draw(folder);
                // Arrow out
                g2.fill(new Rectangle2D.Float(s * 0.45f, s * 0.35f, s * 0.1f, s * 0.4f));
                Path2D up = new Path2D.Float();
                up.moveTo(s * 0.35f, s * 0.45f);
                up.lineTo(s * 0.5f, s * 0.3f);
                up.lineTo(s * 0.65f, s * 0.45f);
                g2.fill(up);
                break;

            case JAVA:
                g2.setStroke(new BasicStroke(s * 0.06f));
                g2.draw(new Arc2D.Float(p, s * 0.3f, w * 0.8f, h * 0.5f, 180, 180, Arc2D.OPEN)); // Cup bottom
                g2.draw(new Ellipse2D.Float(p, s * 0.3f, w * 0.8f, h * 0.2f)); // Cup top
                g2.draw(new Arc2D.Float(s * 0.65f, s * 0.4f, s * 0.25f, s * 0.3f, -90, 180, Arc2D.OPEN)); // Handle
                // Steam
                g2.draw(new Arc2D.Float(s * 0.3f, s * 0.1f, s * 0.1f, s * 0.2f, 0, 180, Arc2D.OPEN));
                g2.draw(new Arc2D.Float(s * 0.5f, s * 0.1f, s * 0.1f, s * 0.2f, 180, 180, Arc2D.OPEN));
                break;

            case GOOGLE:
                g2.setStroke(new BasicStroke(s * 0.12f));
                g2.draw(new Arc2D.Float(p, p, w, h, 45, 280, Arc2D.OPEN)); // The 'G' curve
                g2.fill(new Rectangle2D.Float(s * 0.5f, s * 0.45f, s * 0.35f, s * 0.1f)); // The 'G' bar
                break;

            case LIVE:
                g2.setStroke(new BasicStroke(s * 0.08f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                // Four arrows pointing to center
                float len = s * 0.25f;
                // Top Left
                g2.drawLine((int)p, (int)p, (int)(p+len), (int)(p+len));
                // Top Right
                g2.drawLine((int)(s-p), (int)p, (int)(s-p-len), (int)(p+len));
                // Bottom Left
                g2.drawLine((int)p, (int)(s-p), (int)(p+len), (int)(s-p-len));
                // Bottom Right
                g2.drawLine((int)(s-p), (int)(s-p), (int)(s-p-len), (int)(s-p-len));
                break;
                
            case KILL:
                g2.setStroke(new BasicStroke(s * 0.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine((int)p, (int)p, (int)(s-p), (int)(s-p));
                g2.drawLine((int)(s-p), (int)p, (int)p, (int)(s-p));
                break;
                
            case BELL:
                g2.setStroke(new BasicStroke(s * 0.06f));
                Path2D bell = new Path2D.Float();
                bell.moveTo(s * 0.5f, p);
                bell.curveTo(s * 0.2f, p, s * 0.2f, s * 0.7f, p, s * 0.7f);
                bell.lineTo(s - p, s * 0.7f);
                bell.curveTo(s * 0.8f, s * 0.7f, s * 0.8f, p, s * 0.5f, p);
                g2.draw(bell);
                g2.fill(new Ellipse2D.Float(s * 0.4f, s * 0.75f, s * 0.2f, s * 0.15f)); // Clapper
                break;
                
            case BELL_MUTE:
                this.paintIcon(c, g, 0, 0); // Draw normal bell first
                g2.setStroke(new BasicStroke(s * 0.08f));
                g2.drawLine((int)s, 0, 0, (int)s); // Strike through
                break;
        }

        g2.dispose();
    }

    @Override
    public int getIconWidth() { return size; }

    @Override
    public int getIconHeight() { return size; }
}
