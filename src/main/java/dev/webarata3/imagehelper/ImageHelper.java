package dev.webarata3.imagehelper;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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
import javax.swing.Timer;

public class ImageHelper extends JFrame {
    private JPanel thumbnailPanel;
    private JScrollPane scrollPane;
    private JLabel folderPathLabel;
    private static final String PREF_KEY_LAST_DIR = "last_opened_directory";
    private static final String PREF_KEY_WINDOW_WIDTH = "window_width";
    private static final String PREF_KEY_WINDOW_HEIGHT = "window_height";
    private static final String PREF_KEY_WINDOW_X = "window_x";
    private static final String PREF_KEY_WINDOW_Y = "window_y";
    private Preferences prefs = Preferences.userNodeForPackage(getClass());

    private final Set<Path> displayedImages = Collections.synchronizedSet(new java.util.HashSet<>());
    private final Map<Path, JLabel> imageLabels = new HashMap<>();
    private Path currentFolderPath = null;

    private JPanel noFolderPanel;
    private JPanel selectedFolderPanel;
    private JLabel selectedLabel = null;

    public ImageHelper() {
        super("画像サムネイルビューア");

        var icon = new ImageIcon(getClass().getResource("/icon.png")).getImage();
        setIconImage(icon);

        createNoFolderLayout();
        createSelectedFolderLayout();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        openLastFolderIfExists();

        if (currentFolderPath == null) {
            setContentPane(noFolderPanel);
        } else {
            setContentPane(selectedFolderPanel);
        }

        Preferences prefs = Preferences.userNodeForPackage(getClass());

        var width = prefs.getInt(PREF_KEY_WINDOW_WIDTH, 800);
        var height = prefs.getInt(PREF_KEY_WINDOW_HEIGHT, 600);
        var x = prefs.getInt(PREF_KEY_WINDOW_X, 100);
        var y = prefs.getInt(PREF_KEY_WINDOW_Y, 100);
        setSize(width, height);
        setLocation(x, y);

        // 終了時に保存
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                var size = getSize();
                prefs.putInt(PREF_KEY_WINDOW_WIDTH, size.width);
                prefs.putInt(PREF_KEY_WINDOW_HEIGHT, size.height);

                var location = getLocation();
                prefs.putInt(PREF_KEY_WINDOW_X, location.x);
                prefs.putInt(PREF_KEY_WINDOW_Y, location.y);
            }
        });

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (selectedLabel != null && e.getKeyCode() == KeyEvent.VK_DELETE) {
                    thumbnailPanel.remove(selectedLabel);
                    try {
                        Files.deleteIfExists(currentFolderPath.resolve(selectedLabel.getToolTipText()));
                    } catch (IOException ex) {
                    }
                    displayedImages.remove(currentFolderPath.resolve(selectedLabel.getToolTipText()));
                    imageLabels.remove(currentFolderPath.resolve(selectedLabel.getToolTipText()));
                    selectedLabel = null;
                    thumbnailPanel.revalidate();
                    thumbnailPanel.repaint();
                }
            }
        });

        setVisible(true);

        startFolderMonitor();
    }

    private void createNoFolderLayout() {
        noFolderPanel = new JPanel();
        noFolderPanel.setLayout(new BorderLayout());

        var messageLabel = new JLabel("フォルダーを選択してください");
        noFolderPanel.add(messageLabel, BorderLayout.NORTH);

        var selectFolderBtn = new JButton("フォルダーを選択");
        selectFolderBtn.addActionListener(a -> chooseFolder());
        noFolderPanel.add(selectFolderBtn, BorderLayout.CENTER);
    }

    private void createSelectedFolderLayout() {
        selectedFolderPanel = new JPanel();
        selectedFolderPanel.setLayout(new BorderLayout());

        var infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        folderPathLabel = new JLabel("フォルダーを選択してください");
        folderPathLabel.setFont(new Font("BIZ UDゴシック", Font.PLAIN, 20));
        folderPathLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        infoPanel.add(folderPathLabel);

        var selectFolderBtn = new JButton("フォルダーを選択");
        selectFolderBtn.addActionListener(a -> chooseFolder());
        infoPanel.add(selectFolderBtn);

        var captureBtn = new JButton("画面キャプチャ");
        captureBtn.addActionListener(a -> startCapture());
        infoPanel.add(captureBtn);

        // サムネイル表示パネル
        thumbnailPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 10, 10));
        // thumbnailPanel.setPreferredSize(new Dimension(800, 600));
        // scrollPane = new JScrollPane(thumbnailPanel);
        thumbnailPanel = new ScrollablePanel(new WrapLayout(FlowLayout.LEFT, 10, 10));
        scrollPane = new JScrollPane(thumbnailPanel);

        // レイアウト設定
        selectedFolderPanel.add(infoPanel, BorderLayout.NORTH);
        selectedFolderPanel.add(scrollPane, BorderLayout.CENTER);
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
        var chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            var folder = chooser.getSelectedFile();
            folderPathLabel.setText("現在のフォルダー: " + folder.getAbsolutePath());
            setContentPane(selectedFolderPanel);
            pack();
            setLocationRelativeTo(null);
            prefs.put(PREF_KEY_LAST_DIR, folder.getAbsolutePath()); // 保存

            displayedImages.clear();
            imageLabels.clear();
            showThumbnails(folder.toPath());
        }
    }

    private void openLastFolderIfExists() {
        var lastPath = prefs.get(PREF_KEY_LAST_DIR, null);
        if (lastPath != null) {
            var folder = new File(lastPath);
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
            var imagePaths = Files.list(folderPath).filter(Files::isRegularFile).filter(p -> {
                var name = p.getFileName().toString().toLowerCase();
                return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                        || name.endsWith(".gif");
            }).collect(Collectors.toList());

            displayedImages.stream().filter(displayedImage -> !imagePaths.contains(displayedImage))
                    .forEach(displayImage -> {
                        thumbnailPanel.remove(imageLabels.get(displayImage));
                    });

            for (var path : imagePaths) {
                if (displayedImages.contains(path)) continue;

                var original = ImageIO.read(path.toFile());
                if (original == null) continue;

                var thumb = getScaledImageKeepAspectRatio(original, 100, 100);
                var label = new JLabel(new ImageIcon(thumb));
                label.setToolTipText(path.getFileName().toString());
                label.setBorder(BorderFactory.createLineBorder(new Color(0, 0, 0, 0), 1));

                label.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        for (var comp : thumbnailPanel.getComponents()) {
                            if (comp instanceof JLabel) {
                                ((JLabel) comp).setBorder(BorderFactory.createLineBorder(new Color(0, 0, 0, 0), 1));
                            }
                        }
                        if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                            showDraggableResizableImage(path);
                        } else {
                            selectedLabel = (JLabel) e.getComponent();
                            selectedLabel.setBorder(BorderFactory.createLineBorder(new Color(255, 0, 0), 1));

                            // フォーカスをフレームに戻してキーを受け取れるように
                            ImageHelper.this.requestFocusInWindow();
                        }
                    }
                });

                thumbnailPanel.add(label);
                displayedImages.add(path); // 表示済みとして記録
                imageLabels.put(path, label); // ラベルを記録
            }

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "画像の読み込み中にエラーが発生しました: " + e.getMessage());
        }

        thumbnailPanel.revalidate();
        thumbnailPanel.repaint();
    }

    private void startFolderMonitor() {
        var delayMillis = 5000; // 5秒おきに確認
        new Timer(delayMillis, e -> {
            if (currentFolderPath != null) {
                showThumbnails(currentFolderPath);
            }
        }).start();
    }

    private Image getScaledImageKeepAspectRatio(BufferedImage img, int maxWidth, int maxHeight) {
        var width = img.getWidth();
        var height = img.getHeight();
        var scale = Math.min((double) maxWidth / width, (double) maxHeight / height);
        var newWidth = (int) (width * scale);
        var newHeight = (int) (height * scale);
        return img.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
    }

    private void showDraggableResizableImage(Path imagePath) {
        try {
            var originalImage = ImageIO.read(imagePath.toFile());
            if (originalImage == null) return;

            var window = new JWindow();
            window.setAlwaysOnTop(true);
            window.setBackground(Color.BLACK);

            var imageLabel = new JLabel();
            imageLabel.setHorizontalAlignment(JLabel.CENTER);
            imageLabel.setVerticalAlignment(JLabel.CENTER);
            imageLabel.setOpaque(true);
            imageLabel.setBackground(Color.BLACK);

            imageLabel.setIcon(new ImageIcon(originalImage));

            var scrollPane = new JScrollPane(imageLabel);
            scrollPane.setBorder(null);
            window.getContentPane().setLayout(new BorderLayout());
            window.getContentPane().add(scrollPane, BorderLayout.CENTER);

            window.setSize(originalImage.getWidth(), originalImage.getHeight());
            var screen = Toolkit.getDefaultToolkit().getScreenSize();
            window.setLocation((screen.width - window.getWidth()) / 2, (screen.height - window.getHeight()) / 2);

            final int RESIZE_MARGIN = 10;
            final Point[] mouseDownCoords = { null };
            final boolean[] resizing = { false };

            var unifiedMouseHandler = new MouseAdapter() {
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
                    if (resizing[0]) {
                        var p = e.getPoint();
                        // 基準画像のサイズ
                        var aspect = (double) originalImage.getWidth() / originalImage.getHeight();

                        // マウスの位置を元に仮の幅と高さを計算（縦横比維持）
                        var newWidth = Math.max(100, p.x);
                        var newHeight = (int) (newWidth / aspect);

                        // ウィンドウに反映
                        window.setSize(newWidth, newHeight);

                        // スケーリング再設定（表示サイズに応じて）
                        var viewSize = scrollPane.getViewport().getSize();
                        var scaled = getScaledImageKeepAspectRatio(originalImage, viewSize.width, viewSize.height);
                        imageLabel.setIcon(new ImageIcon(scaled));
                    } else if (mouseDownCoords[0] != null) {
                        // ウィンドウ移動処理
                        var currCoords = e.getLocationOnScreen();
                        window.setLocation(currCoords.x - mouseDownCoords[0].x, currCoords.y - mouseDownCoords[0].y);
                    }
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    var c = e.getComponent();
                    var size = c.getSize();
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
            var imageComponent = scrollPane.getViewport().getView();
            imageComponent.addMouseListener(unifiedMouseHandler);
            imageComponent.addMouseMotionListener(unifiedMouseHandler);
            window.setVisible(true);
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "画像を表示できませんでした: " + e.getMessage());
        }
    }
}
