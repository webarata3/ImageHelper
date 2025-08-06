package dev.webarata3.imagehelper;

import java.awt.FlowLayout;
import java.awt.Dimension;
import java.awt.Container;

// --- WrapLayout クラス（FlowLayout 拡張：折り返し） ---
// https://tips4java.wordpress.com/2008/11/06/wrap-layout/
public class WrapLayout extends FlowLayout {
    public WrapLayout() {
        super();
    }

    public WrapLayout(int align) {
        super(align);
    }

    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        var minimum = layoutSize(target, false);
        minimum.width -= (getHgap() + 1);
        return minimum;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            var targetWidth = target.getWidth();
            if (targetWidth == 0) {
                targetWidth = Integer.MAX_VALUE;
            }

            var insets = target.getInsets();
            var maxWidth = targetWidth - (insets.left + insets.right + getHgap() * 2);
            var x = 0;
            var y = insets.top + getVgap();
            var rowHeight = 0;

            for (var comp : target.getComponents()) {
                if (!comp.isVisible()) continue;
                var d = preferred ? comp.getPreferredSize() : comp.getMinimumSize();
                if (x + d.width > maxWidth) {
                    x = 0;
                    y += rowHeight + getVgap();
                    rowHeight = 0;
                }
                x += d.width + getHgap();
                rowHeight = Math.max(rowHeight, d.height);
            }

            y += rowHeight + insets.bottom;
            return new Dimension(targetWidth, y);
        }
    }
}
