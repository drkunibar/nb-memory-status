package io.github.drkunibar.netbeans.nb.memory.status;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import org.openide.actions.GarbageCollectAction;
import org.openide.util.NbBundle;

/**
 * Mostly taken from {@code org.openide.actions.HeapView}.
 */
@NbBundle.Messages({ "StatuslineMemoryUsagePanel.used=used: ", "StatuslineMemoryUsagePanel.free=free: ",
        "StatuslineMemoryUsagePanel.total=total: ", "StatuslineMemoryUsagePanel.max=max: " })
public class StatuslineMemoryUsagePanel extends JComponent {

    private static final int REFRESH_TIME_IN_MS = 1500;
    private static final Color COLOR_BACKGROUND = getColor("nb.heapview.background", new Color(0xCEDBE6));
    private static final Color COLOR_CHART = getColor("nb.heapview.chart", new Color(0x2E90E8));
    private static final Color COLOR_FOURGROUND = getColor("nb.heapview.foreground", Color.DARK_GRAY);
    private static final Color COLOR_HIGHTLIGHT = getColor("nb.heapview.highlight", COLOR_BACKGROUND.brighter());
    private final DecimalFormat FORMAT;

    private static final int GRAPH_COUNT = 100;
    private final long[] graph = new long[GRAPH_COUNT];
    private String heapSizeText = "";
    private String tooltipText = "";
    private int graphIndex = 0;
    private long lastTotal = 0;
    private Timer refreshTimer;

    public StatuslineMemoryUsagePanel() {
        this.FORMAT = new DecimalFormat("#,##0.0");
    }

    private static Color getColor(String uiManagerProperty, Color defaultColor) {
        Color result = UIManager.getColor(uiManagerProperty);
        return result == null ? defaultColor : result;
    }

    @Override
    public void updateUI() {
        Font f = UIManager.getFont("Label.font");
        setFont(f);
        /*
         * Setting this true seems to cause some painting artifacts on 150% scaling, as we don't always manage to fill
         * every device pixel with the background color. So leave it off.
         */
        setOpaque(false);
    }

    private void startTimer() {
        if (refreshTimer == null) {
            refreshTimer = new Timer(REFRESH_TIME_IN_MS, a -> update());
            refreshTimer.setRepeats(true);
            refreshTimer.start();
        }
    }

    private void stopTimer() {
        if (refreshTimer != null) {
            refreshTimer.stop();
            refreshTimer = null;
        }
    }

    private void update() {
        if (isShowing()) {
            Runtime r = Runtime.getRuntime();
            long total = r.totalMemory();
            long used = total - r.freeMemory();
            graph[graphIndex] = used;
            lastTotal = total;
            ++graphIndex;
            if (graphIndex >= GRAPH_COUNT) {
                graphIndex = 0;
            }
            heapSizeText = FORMAT.format(calcMB(used)) + " / " + FORMAT.format(calcMB(total)) + " MB";
            tooltipText = "<html>"
                    + NbBundle.getMessage(StatuslineMemoryUsagePanel.class, "StatuslineMemoryUsagePanel.used")
                    + FORMAT.format(calcMB(used)) + " MB<br/>"
                    + NbBundle.getMessage(StatuslineMemoryUsagePanel.class, "StatuslineMemoryUsagePanel.free")
                    + FORMAT.format(calcMB(r.freeMemory())) + " MB<br/>"
                    + NbBundle.getMessage(StatuslineMemoryUsagePanel.class, "StatuslineMemoryUsagePanel.total")
                    + FORMAT.format(calcMB(total)) + " MB<br/>"
                    + NbBundle.getMessage(StatuslineMemoryUsagePanel.class, "StatuslineMemoryUsagePanel.max")
                    + FORMAT.format(calcMB(r.maxMemory())) + " MB<br/>"
                    + NbBundle.getMessage(GarbageCollectAction.class, "CTL_GC");
            setToolTipText(tooltipText);
            repaint();
        } else {
            // Either we've become invisible, or one of our ancestors has.
            // Stop the timer and bale. Next paint will trigger timer to
            // restart.
            stopTimer();
        }
    }

    private double calcMB(long bytes) {
        return bytes / (1024.0d * 1024);
    }

    @Override
    protected void processMouseEvent(MouseEvent e) {
        super.processMouseEvent(e);
        if (e.getID() == MouseEvent.MOUSE_CLICKED && SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
            GarbageCollectAction.get(GarbageCollectAction.class)
                    .performAction();
        }
    }

    @Override
    public Dimension getPreferredSize() {
        int height = getFontMetrics(getFont()).getHeight() + 8;
        int width = getFontMetrics(getFont()).stringWidth("8,888.8 / 8,888.8 MB") + 4;
        return new Dimension(width, height);
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        stopTimer();
    }

    /**
     * Paints the component.
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int width = getWidth();
        int height = getHeight();

        if (width > 0 && height > 0) {
            startTimer();
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g2.clipRect(0, 0, width, height);
                // Draw background.
                g2.setColor(COLOR_BACKGROUND);
                g2.fillRect(0, 0, width, height);
                // Draw samples
                g2.setColor(COLOR_CHART);
                paintSamples(g2, width, height);
                paintText(g2, width, height);
            } finally {
                g2.dispose();
            }
        } else {
            stopTimer();
        }
    }

    /**
     * Renders the text using an optional drop shadow.
     */
    private void paintText(Graphics2D g, int w, int h) {
        Font font = getFont();
        String text = heapSizeText;
        GlyphVector gv = font.createGlyphVector(g.getFontRenderContext(), text);
        FontMetrics fm = g.getFontMetrics(font);
        Shape outline = gv.getOutline();
        Rectangle2D bounds = outline.getBounds2D();
        double x = Math.max(0, (w - bounds.getWidth()) / 2.0);
        double y = h / 2.0 + fm.getAscent() / 2.0 - 2.0;
        AffineTransform oldTransform = g.getTransform();
        g.translate(x, y);
        g.setColor(COLOR_HIGHTLIGHT);
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(outline);
        g.setColor(COLOR_FOURGROUND);
        g.fill(outline);
        g.setTransform(oldTransform);
    }

    private void paintSamples(Graphics2D g, int width, int height) {
        Path2D path = new Path2D.Double();
        path.moveTo(0, height);
        for (int i = 0; i < GRAPH_COUNT; ++i) {
            int index = (i + graphIndex) % GRAPH_COUNT;
            double x = (double) i / (double) (GRAPH_COUNT - 1) * (double) width;
            double y = (double) height * (1.0 - (double) graph[index] / (double) lastTotal);
            path.lineTo(x, y);
        }
        path.lineTo(width, height);
        path.closePath();
        g.fill(path);
    }
}
