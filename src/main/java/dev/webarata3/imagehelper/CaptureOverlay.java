package dev.webarata3.imagehelper;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

class CaptureOverlay extends JWindow {
    private Point start;
    private Point end;
    private final File saveFolder;

    public CaptureOverlay(JFrame parent, File saveFolder) {
        super(parent);
        this.saveFolder = saveFolder;

        setBounds(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds());
        setBackground(new Color(0, 0, 0, 50));
        setAlwaysOnTop(true);
        setVisible(true);

        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                start = e.getPoint();
            }

            public void mouseReleased(MouseEvent e) {
                end = e.getPoint();
                captureScreen();
                dispose();
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                end = e.getPoint();
                repaint();
            }
        });
    }

    public void paint(Graphics g) {
        super.paint(g);
        if (start != null && end != null) {
            var x = Math.min(start.x, end.x);
            var y = Math.min(start.y, end.y);
            var w = Math.abs(start.x - end.x);
            var h = Math.abs(start.y - end.y);
            g.setColor(Color.RED);
            g.drawRect(x, y, w, h);
        }
    }

    private void captureScreen() {
        if (start == null || end == null) return;

        var x = Math.min(start.x, end.x);
        var y = Math.min(start.y, end.y);
        var w = Math.abs(start.x - end.x);
        var h = Math.abs(start.y - end.y);

        try {
            // オーバーレイを非表示（Robot の前に必要）
            setVisible(false);

            // 十分に非表示になるのを待つ（描画のタイミングを考慮）
            Thread.sleep(200);

            var robot = new Robot();
            var image = robot.createScreenCapture(new Rectangle(x, y, w, h));
            var timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            var output = new File(saveFolder, "screenshot_" + timestamp + ".png");
            ImageIO.write(image, "png", output);
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            dispose(); // オーバーレイを破棄

            // 親ウィンドウを再表示
            SwingUtilities.invokeLater(() -> {
                if (getOwner() != null) {
                    getOwner().setVisible(true);
                }
            });
        }
    }
}
