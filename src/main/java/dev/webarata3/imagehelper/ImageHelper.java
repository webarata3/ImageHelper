package dev.webarata3.imagehelper;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

public class ImageHelper extends JFrame {
    private JPanel thumbnailPanel;
    private JScrollPane scrollPane;
    private JLabel folderPathLabel;
    private static final String PREF_KEY_LAST_DIR = "last_opened_directory";
    private Preferences prefs = Preferences.userNodeForPackage(getClass());

    private final java.util.Set<Path> displayedImages = java.util.Collections
            .synchronizedSet(new java.util.HashSet<>());
    private Path currentFolderPath = null;

    public ImageHelper() {
        super("画像サムネイルビューア");

        var infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        folderPathLabel = new JLabel("フォルダーを選択してください");
        folderPathLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        infoPanel.add(folderPathLabel);

        JButton selectFolderBtn = new JButton("フォルダーを選択");
        selectFolderBtn.addActionListener(a -> chooseFolder());
        infoPanel.add(selectFolderBtn);

        JButton captureBtn = new JButton("画面キャプチャ");
        captureBtn.addActionListener(a -> startCapture());
        infoPanel.add(captureBtn);

        // サムネイル表示パネル
        thumbnailPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 10, 10));
        thumbnailPanel.setPreferredSize(new Dimension(800, 600));
        scrollPane = new JScrollPane(thumbnailPanel);

        // レイアウト設定
        setLayout(new BorderLayout());
        add(infoPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
        openLastFolderIfExists();
        setVisible(true);

        startFolderMonitor();
    }

    private void startCapture() {
        // この JFrame を一時的に非表示
        this.setVisible(false);

        // キャプチャーを開始（CaptureOverlay にこの JFrame を渡す）
        SwingUtilities.invokeLater(() -> {
            new CaptureOverlay(ImageHelper.this, currentFolderPath.toFile());
        });
    }

    // フォルダーを選択してサムネイル表示
    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File folder = chooser.getSelectedFile();
            folderPathLabel.setText("現在のフォルダー: " + folder.getAbsolutePath());
            prefs.put(PREF_KEY_LAST_DIR, folder.getAbsolutePath()); // 保存

            displayedImages.clear();
            showThumbnails(folder.toPath());
        }
    }

    private void openLastFolderIfExists() {
        String lastPath = prefs.get(PREF_KEY_LAST_DIR, null);
        if (lastPath != null) {
            File folder = new File(lastPath);
            if (folder.exists() && folder.isDirectory()) {
                folderPathLabel.setText("現在のフォルダー: " + folder.getAbsolutePath());
                showThumbnails(folder.toPath());
            }
        }
    }

    // フォルダー内の画像をサムネイルで表示
    private void showThumbnails(Path folderPath) {
        this.currentFolderPath = folderPath; // 現在のフォルダー記録

        try {
            List<Path> imagePaths = Files.list(folderPath).filter(Files::isRegularFile).filter(p -> {
                String name = p.getFileName().toString().toLowerCase();
                return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                        || name.endsWith(".gif");
            }).collect(Collectors.toList());

            for (Path path : imagePaths) {
                if (displayedImages.contains(path))
                    continue;

                BufferedImage original = ImageIO.read(path.toFile());
                if (original == null)
                    continue;

                Image thumb = getScaledImageKeepAspectRatio(original, 100, 100);
                JLabel label = new JLabel(new ImageIcon(thumb));
                label.setToolTipText(path.getFileName().toString());

                label.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                            showDraggableResizableImage(path);
                        }
                    }
                });

                thumbnailPanel.add(label);
                displayedImages.add(path); // 表示済みとして記録
            }

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "画像の読み込み中にエラーが発生しました: " + e.getMessage());
        }

        thumbnailPanel.revalidate();
        thumbnailPanel.repaint();
    }

    private void startFolderMonitor() {
        int delayMillis = 5000; // 5秒おきに確認
        new javax.swing.Timer(delayMillis, e -> {
            if (currentFolderPath != null) {
                showThumbnails(currentFolderPath);
            }
        }).start();
    }

    private Image getScaledImageKeepAspectRatio(BufferedImage img, int maxWidth, int maxHeight) {
        int width = img.getWidth();
        int height = img.getHeight();
        double scale = Math.min((double) maxWidth / width, (double) maxHeight / height);
        int newWidth = (int) (width * scale);
        int newHeight = (int) (height * scale);
        return img.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
    }

    private void showDraggableResizableImage(Path imagePath) {
        try {
            BufferedImage originalImage = ImageIO.read(imagePath.toFile());
            if (originalImage == null)
                return;

            JWindow window = new JWindow();
            window.setAlwaysOnTop(true);
            window.setBackground(Color.BLACK);

            JLabel imageLabel = new JLabel();
            imageLabel.setHorizontalAlignment(JLabel.CENTER);
            imageLabel.setVerticalAlignment(JLabel.CENTER);
            imageLabel.setOpaque(true);
            imageLabel.setBackground(Color.BLACK);

            imageLabel.setIcon(new ImageIcon(originalImage));

            JScrollPane scrollPane = new JScrollPane(imageLabel);
            scrollPane.setBorder(null);
            window.getContentPane().setLayout(new BorderLayout());
            window.getContentPane().add(scrollPane, BorderLayout.CENTER);

            window.setSize(originalImage.getWidth(), originalImage.getHeight());
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            window.setLocation((screen.width - window.getWidth()) / 2, (screen.height - window.getHeight()) / 2);

            final int RESIZE_MARGIN = 10;
            final Point[] mouseDownCoords = { null };
            final boolean[] resizing = { false };

            MouseAdapter unifiedMouseHandler = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    Component c = e.getComponent();
                    Dimension size = c.getSize();
                    Point p = e.getPoint();

                    // 右下かどうか
                    resizing[0] = p.x >= size.width - RESIZE_MARGIN && p.y >= size.height - RESIZE_MARGIN;

                    if (!resizing[0]) {
                        mouseDownCoords[0] = SwingUtilities.convertPoint(c, p, window); // 親ウィンドウ基準に変換
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    Component c = e.getComponent();
                    if (resizing[0]) {
                        Point p = e.getPoint();
                        // 基準画像のサイズ
                        double aspect = (double) originalImage.getWidth() / originalImage.getHeight();

                        // マウスの位置を元に仮の幅と高さを計算（縦横比維持）
                        int newWidth = Math.max(100, p.x);
                        int newHeight = (int) (newWidth / aspect);

                        // ウィンドウに反映
                        window.setSize(newWidth, newHeight);

                        // スケーリング再設定（表示サイズに応じて）
                        Dimension viewSize = scrollPane.getViewport().getSize();
                        Image scaled = getScaledImageKeepAspectRatio(originalImage, viewSize.width, viewSize.height);
                        imageLabel.setIcon(new ImageIcon(scaled));
                    } else if (mouseDownCoords[0] != null) {
                        // ウィンドウ移動処理
                        Point currCoords = e.getLocationOnScreen();
                        window.setLocation(currCoords.x - mouseDownCoords[0].x, currCoords.y - mouseDownCoords[0].y);
                    }
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    Component c = e.getComponent();
                    Dimension size = c.getSize();
                    if (e.getX() >= size.width - RESIZE_MARGIN && e.getY() >= size.height - RESIZE_MARGIN) {
                        c.setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
                    } else {
                        c.setCursor(Cursor.getDefaultCursor());
                    }
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                        window.dispose();
                    }
                }
            };

            // imageLabel または scrollPane.getViewport().getView() にリスナーを登録
            Component imageComponent = scrollPane.getViewport().getView();
            imageComponent.addMouseListener(unifiedMouseHandler);
            imageComponent.addMouseMotionListener(unifiedMouseHandler);
            window.setVisible(true);
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "画像を表示できませんでした: " + e.getMessage());
        }
    }

}
