package operation;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/** Small self-contained PNG chart generator using java.io and java.util only. */
public final class PngChart {
    private static final int BG = 0xFFFFFFFF;
    private static final int INK = 0xFF27313E;
    private static final int GRID = 0xFFE4E8EE;
    private static final int[] COLORS = {0xFF3678D6, 0xFF33A06B, 0xFFF0A62E,
            0xFFE65A70, 0xFF865BCB, 0xFF22A8A8, 0xFFB06D33};
    private final int w, h;
    private final int[] pixels;

    private PngChart(int w, int h) {
        this.w = w;
        this.h = h;
        this.pixels = new int[w * h];
        for (int i = 0; i < pixels.length; i++) pixels[i] = BG;
    }

    private void pixel(int x, int y, int argb) {
        if (x >= 0 && x < w && y >= 0 && y < h) pixels[y * w + x] = argb;
    }

    private void rect(int x, int y, int ww, int hh, int color) {
        for (int yy = Math.max(0, y); yy < Math.min(h, y + hh); yy++)
            for (int xx = Math.max(0, x); xx < Math.min(w, x + ww); xx++) pixel(xx, yy, color);
    }

    private void line(int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            pixel(x0, y0, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    private void circle(int cx, int cy, int radius, int color) {
        int r2 = radius * radius;
        for (int y = -radius; y <= radius; y++) {
            int d = (int) Math.sqrt(r2 - y * y);
            rect(cx - d, cy + y, 2 * d + 1, 1, color);
        }
    }

    // Built-in 5x7 bitmap font, no AWT or ImageIO required.
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 .,:_-/()%<>?+";
    private static final int[] GLYPHS = {14,17,17,31,17,17,17,30,17,17,30,17,17,30,15,16,16,16,16,16,15,30,17,17,17,17,17,30,31,16,16,30,16,16,31,31,16,16,30,16,16,16,15,16,16,23,17,17,15,17,17,17,31,17,17,17,31,4,4,4,4,4,31,7,2,2,2,18,18,12,17,18,20,24,20,18,17,16,16,16,16,16,16,31,17,27,21,21,17,17,17,17,25,21,19,17,17,17,14,17,17,17,17,17,14,30,17,17,30,16,16,16,14,17,17,17,21,18,13,30,17,17,30,20,18,17,15,16,16,14,1,1,30,31,4,4,4,4,4,4,17,17,17,17,17,17,14,17,17,17,17,17,10,4,17,17,17,21,21,21,10,17,17,10,4,10,17,17,17,17,10,4,4,4,4,31,1,2,4,8,16,31,14,17,19,21,25,17,14,4,12,4,4,4,4,14,14,17,1,2,4,8,31,30,1,1,14,1,1,30,2,6,10,18,31,2,2,31,16,16,30,1,1,30,14,16,16,30,17,17,14,31,1,2,4,8,8,8,14,17,17,14,17,17,14,14,17,17,15,1,1,14,0,0,0,0,0,0,0,0,0,0,0,0,6,6,0,0,0,0,6,6,12,0,6,6,0,6,6,0,0,0,0,0,0,0,31,0,0,0,31,0,0,0,1,1,2,4,8,16,16,2,4,8,8,8,4,2,8,4,2,2,2,4,8,25,26,4,8,22,6,0,2,4,8,16,8,4,2,8,4,2,1,2,4,8,14,17,1,2,4,0,4,0,4,4,31,4,4,0};

    private static int[] glyph(char c) {
        int i = CHARS.indexOf(Character.toUpperCase(c));
        if (i < 0) i = CHARS.indexOf('?');
        int[] rows = new int[7];
        System.arraycopy(GLYPHS, i * 7, rows, 0, 7);
        return rows;
    }

    private void text(int x, int y, String value, int scale, int color) {
        if (value == null) return;
        String s = value.toUpperCase();
        for (int i = 0; i < s.length(); i++) {
            int[] rows = glyph(s.charAt(i));
            for (int yy = 0; yy < 7; yy++)
                for (int xx = 0; xx < 5; xx++)
                    if (((rows[yy] >> (4 - xx)) & 1) != 0)
                        rect(x + i * 6 * scale + xx * scale, y + yy * scale, scale, scale, color);
        }
    }

    private void header(String title) {
        text(45, 26, truncate(title, 53), 2, INK);
        line(44, 54, w - 44, 54, GRID);
    }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, Math.max(0, n - 2)) + "..";
    }

    public static void bar(String title, List<String> labels, List<Long> values, String output) throws IOException {
        PngChart ch = new PngChart(960, 600);
        ch.header(title);
        int left = 85, top = 90, bottom = 490, right = 910;
        long max = 1;
        for (Long v : values) if (v != null) max = Math.max(max, v);
        for (int i = 0; i <= 5; i++) {
            int y = bottom - (bottom - top) * i / 5;
            ch.line(left, y, right, y, GRID);
            ch.text(8, y - 5, String.valueOf(max * i / 5), 1, INK);
        }
        ch.line(left, bottom, right, bottom, INK);
        if (labels.isEmpty()) ch.text(350, 270, "NO DATA", 3, INK);
        int n = Math.max(1, labels.size());
        int slot = Math.max(1, (right - left) / n);
        for (int i = 0; i < labels.size(); i++) {
            long v = i < values.size() && values.get(i) != null ? Math.max(0, values.get(i)) : 0;
            int hh = (int) Math.round((bottom - top) * (double) v / max);
            int bw = Math.max(1, (int) (slot * 0.7));
            int x = left + i * slot + (slot - bw) / 2;
            ch.rect(x, bottom - hh, bw, hh, COLORS[i % COLORS.length]);
            String label = truncate(labels.get(i), Math.max(3, Math.min(10, slot / 6)));
            ch.text(x, bottom + 12, label, 1, INK);
        }
        ch.save(output);
    }

    public static void pie(String title, String[] labels, int[] values, String output) throws IOException {
        PngChart ch = new PngChart(960, 600);
        ch.header(title);
        int total = 0;
        for (int v : values) total += Math.max(0, v);
        if (total == 0) {
            ch.text(340, 270, "NO DATA", 3, INK);
        } else {
            int cx = 320, cy = 310, radius = 185;
            double[] endpoints = new double[values.length];
            double acc = 0;
            for (int i = 0; i < values.length; i++) {
                acc += Math.max(0, values[i]) / (double) total;
                endpoints[i] = acc;
            }
            for (int y = -radius; y <= radius; y++) {
                for (int x = -radius; x <= radius; x++) {
                    if (x * x + y * y > radius * radius) continue;
                    double angle = (Math.atan2(y, x) + Math.PI / 2 + Math.PI * 2) % (Math.PI * 2);
                    double share = angle / (Math.PI * 2);
                    int ix = 0;
                    while (ix < endpoints.length - 1 && share >= endpoints[ix]) ix++;
                    ch.pixel(cx + x, cy + y, COLORS[ix % COLORS.length]);
                }
            }
            for (int i = 0; i < labels.length && i < values.length; i++) {
                int y = 190 + i * 55;
                ch.rect(610, y, 23, 23, COLORS[i % COLORS.length]);
                ch.text(646, y + 5, truncate(labels[i], 20) + " " + values[i], 2, INK);
            }
        }
        ch.save(output);
    }

    public static void scatter(String title, List<double[]> xy, String output) throws IOException {
        PngChart ch = new PngChart(960, 600);
        ch.header(title);
        int left = 100, right = 900, top = 90, bottom = 490;
        double maxX = 100, maxY = 1;
        for (double[] v : xy) { maxX = Math.max(maxX, v[0]); maxY = Math.max(maxY, v[1]); }
        for (int i = 0; i <= 5; i++) {
            int x = left + (right - left) * i / 5;
            int y = bottom - (bottom - top) * i / 5;
            ch.line(left, y, right, y, GRID);
            ch.line(x, top, x, bottom, GRID);
            ch.text(x - 10, bottom + 18, String.valueOf((int) (maxX * i / 5)), 1, INK);
            ch.text(8, y - 5, String.valueOf((long) (maxY * i / 5)), 1, INK);
        }
        ch.line(left, bottom, right, bottom, INK);
        ch.line(left, top, left, bottom, INK);
        ch.text(385, 550, "DISCOUNT (%)", 2, INK);
        ch.text(5, 70, "LIKES COUNT", 1, INK);
        if (xy.isEmpty()) ch.text(365, 275, "NO DATA", 3, INK);
        for (double[] v : xy) {
            int x = left + (int) Math.round((right - left) * Math.max(0, v[0]) / maxX);
            int y = bottom - (int) Math.round((bottom - top) * Math.max(0, v[1]) / maxY);
            ch.circle(x, y, 4, COLORS[3]);
        }
        ch.save(output);
    }

    private static void writeChunk(DataOutputStream output, String type, byte[] data) throws IOException {
        output.writeInt(data.length);
        byte[] name = type.getBytes("US-ASCII");
        output.write(name);
        output.write(data);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data);
        output.writeInt((int) crc.getValue());
    }

    private void save(String output) throws IOException {
        File file = new File(output);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs())
            throw new IOException("Cannot create chart directory: " + parent);
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        for (int y = 0; y < h; y++) {
            raw.write(0); // PNG filter None
            for (int x = 0; x < w; x++) {
                int v = pixels[y * w + x];
                raw.write((v >>> 16) & 255);
                raw.write((v >>> 8) & 255);
                raw.write(v & 255);
                raw.write((v >>> 24) & 255);
            }
        }
        byte[] input = raw.toByteArray();
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        deflater.setInput(input);
        deflater.finish();
        ByteArrayOutputStream zipped = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        while (!deflater.finished()) zipped.write(buf, 0, deflater.deflate(buf));
        deflater.end();
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
            out.write(new byte[]{(byte)137, 80, 78, 71, 13, 10, 26, 10});
            ByteArrayOutputStream meta = new ByteArrayOutputStream();
            try (DataOutputStream info = new DataOutputStream(meta)) {
                info.writeInt(w); info.writeInt(h); info.writeByte(8);
                info.writeByte(6); info.writeByte(0); info.writeByte(0); info.writeByte(0);
            }
            writeChunk(out, "IHDR", meta.toByteArray());
            writeChunk(out, "IDAT", zipped.toByteArray());
            writeChunk(out, "IEND", new byte[0]);
        }
    }
}
