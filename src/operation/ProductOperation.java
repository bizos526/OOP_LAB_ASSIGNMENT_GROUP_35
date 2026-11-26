package operation;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import model.Product;

public class ProductOperation {
    private static ProductOperation instance;
    private static final String DATA_FOLDER = "data";
    private static final String PRODUCT_FILE_PATH = "data/products.txt";
    private static final String FIGURE_FOLDER = "data/figure";

    private ProductOperation() {
    }

    public static ProductOperation getInstance() {
        if (instance == null) {
            instance = new ProductOperation();
        }
        return instance;
    }

    public void extractProductsFromFiles() {
        File dataDir = new File(DATA_FOLDER);
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        List<File> csvFiles = new ArrayList<>();
        collectCsvFiles(dataDir, csvFiles);

        if (csvFiles.isEmpty()) {
            ensureProductFileExists();
            return;
        }

        Map<String, Product> productMap = new LinkedHashMap<>();

        for (File file : csvFiles) {
            try (BufferedReader br = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                String headerLine = br.readLine();
                if (headerLine == null) {
                    continue;
                }
                if (headerLine.startsWith("\uFEFF")) {
                    headerLine = headerLine.substring(1);
                }

                List<String> headers = parseCsvLine(headerLine);
                Map<String, Integer> colIndex = new HashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    colIndex.put(headers.get(i).trim().toLowerCase(), i);
                }

                String line;
                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    List<String> cols = parseCsvLine(line);
                    Product product = mapRowToProduct(cols, colIndex);
                    if (product != null && product.getProId() != null && !product.getProId().isEmpty()) {
                        productMap.putIfAbsent(product.getProId(), product);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (productMap.isEmpty()) {
            ensureProductFileExists();
            return;
        }

        saveAllProducts(new ArrayList<>(productMap.values()));
    }

    public ProductListResult getProductList(int pageNumber) {
        List<Product> allProducts = loadAllProducts();
        int pageSize = 10;
        int totalItems = allProducts.size();
        int totalPages = (int) Math.ceil((double) totalItems / pageSize);

        if (totalPages == 0 || pageNumber < 1 || pageNumber > totalPages) {
            return new ProductListResult(new ArrayList<>(), pageNumber, totalPages);
        }

        int startIndex = (pageNumber - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, totalItems);
        List<Product> pageProducts = new ArrayList<>(allProducts.subList(startIndex, endIndex));

        return new ProductListResult(pageProducts, pageNumber, totalPages);
    }

    public boolean deleteProduct(String productId) {
        if (productId == null || productId.isEmpty()) {
            return false;
        }

        List<Product> products = loadAllProducts();
        boolean removed = products.removeIf(p -> productId.equals(p.getProId()));

        if (removed) {
            saveAllProducts(products);
        }
        return removed;
    }

    public List<Product> getProductListByKeyword(String keyword) {
        List<Product> result = new ArrayList<>();
        if (keyword == null) {
            return result;
        }

        String lowerKeyword = keyword.toLowerCase();
        for (Product product : loadAllProducts()) {
            if (product.getProName() != null && product.getProName().toLowerCase().contains(lowerKeyword)) {
                result.add(product);
            }
        }
        return result;
    }

    public Product getProductById(String productId) {
        if (productId == null) {
            return null;
        }

        for (Product product : loadAllProducts()) {
            if (productId.equals(product.getProId())) {
                return product;
            }
        }
        return null;
    }

    public void generateCategoryFigure() {
        List<Product> products = loadAllProducts();
        Map<String, Long> categoryCounts = products.stream()
                .collect(Collectors.groupingBy(
                        p -> (p.getProCategory() == null || p.getProCategory().isEmpty()) ? "Unknown" : p.getProCategory(),
                        Collectors.counting()
                ));

        List<String> categories = new ArrayList<>(categoryCounts.keySet());
        categories.sort((a, b) -> Long.compare(categoryCounts.get(b), categoryCounts.get(a)));

        List<Long> values = new ArrayList<>();
        for (String cat : categories) {
            values.add(categoryCounts.get(cat));
        }

        drawBarChart(
                "Total Products by Category",
                "Category",
                "Number of Products",
                categories,
                values,
                new Color(52, 120, 214),
                "category_figure.png"
        );
    }

    public void generateDiscountFigure() {
        List<Product> products = loadAllProducts();
        int lessThan30 = 0;
        int between30And60 = 0;
        int greaterThan60 = 0;

        for (Product product : products) {
            double discount = product.getProDiscount();
            if (discount < 30) {
                lessThan30++;
            } else if (discount <= 60) {
                between30And60++;
            } else {
                greaterThan60++;
            }
        }

        String[] labels = {"< 30", "30 - 60", "> 60"};
        int[] counts = {lessThan30, between30And60, greaterThan60};
        Color[] colors = {
                new Color(66, 133, 244),
                new Color(52, 168, 83),
                new Color(251, 188, 5)
        };

        drawPieChart("Product Discount Distribution", labels, counts, colors, "discount_figure.png");
    }

    public void generateLikesCountFigure() {
        List<Product> products = loadAllProducts();
        Map<String, Long> categoryLikes = products.stream()
                .collect(Collectors.groupingBy(
                        p -> (p.getProCategory() == null || p.getProCategory().isEmpty()) ? "Unknown" : p.getProCategory(),
                        Collectors.summingLong(Product::getProLikesCount)
                ));

        List<String> categories = new ArrayList<>(categoryLikes.keySet());
        categories.sort((a, b) -> Long.compare(categoryLikes.get(a), categoryLikes.get(b)));

        List<Long> values = new ArrayList<>();
        for (String cat : categories) {
            values.add(categoryLikes.get(cat));
        }

        drawBarChart(
                "Total Likes Count by Category",
                "Category",
                "Total Likes Count",
                categories,
                values,
                new Color(46, 160, 102),
                "likes_count_figure.png"
        );
    }

    public void generateDiscountLikesCountFigure() {
        List<Product> products = loadAllProducts();
        int width = 900;
        int height = 620;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = createGraphics(image, width, height);

        drawTitle(g2, "Relationship Between Discount and Likes Count", width);

        int left = 95;
        int right = width - 50;
        int top = 75;
        int bottom = height - 80;
        int plotW = right - left;
        int plotH = bottom - top;

        if (products.isEmpty()) {
            drawNoData(g2, width, height);
            saveImage(image, "discount_likes_count_figure.png");
            return;
        }

        double maxDiscount = 100.0;
        long maxLikes = 1;
        for (Product p : products) {
            if (p.getProDiscount() > maxDiscount) {
                maxDiscount = p.getProDiscount();
            }
            if (p.getProLikesCount() > maxLikes) {
                maxLikes = p.getProLikesCount();
            }
        }

        g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        FontMetrics fm = g2.getFontMetrics();
        int gridLines = 5;
        for (int i = 0; i <= gridLines; i++) {
            int y = bottom - (int) ((double) i / gridLines * plotH);
            g2.setColor(new Color(230, 230, 230));
            g2.drawLine(left, y, right, y);

            long yVal = Math.round((double) i / gridLines * maxLikes);
            String yLabel = String.valueOf(yVal);
            g2.setColor(Color.DARK_GRAY);
            g2.drawString(yLabel, left - fm.stringWidth(yLabel) - 8, y + 4);

            int x = left + (int) ((double) i / gridLines * plotW);
            g2.setColor(new Color(230, 230, 230));
            g2.drawLine(x, top, x, bottom);

            double xVal = (double) i / gridLines * maxDiscount;
            String xLabel = String.format("%.0f", xVal);
            g2.setColor(Color.DARK_GRAY);
            g2.drawString(xLabel, x - fm.stringWidth(xLabel) / 2, bottom + 20);
        }

        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawLine(left, bottom, right, bottom);
        g2.drawLine(left, top, left, bottom);

        g2.setFont(new Font("SansSerif", Font.BOLD, 13));
        String xTitle = "Discount";
        g2.drawString(xTitle, left + (plotW - g2.getFontMetrics().stringWidth(xTitle)) / 2, height - 25);

        g2.rotate(-Math.PI / 2);
        String yTitle = "Likes Count";
        g2.drawString(yTitle, -(top + (plotH + g2.getFontMetrics().stringWidth(yTitle)) / 2), 25);
        g2.rotate(Math.PI / 2);

        g2.setColor(new Color(220, 68, 55, 160));
        for (Product p : products) {
            double xRatio = Math.max(0, Math.min(1, p.getProDiscount() / maxDiscount));
            double yRatio = Math.max(0, Math.min(1, (double) p.getProLikesCount() / maxLikes));
            double px = left + xRatio * plotW;
            double py = bottom - yRatio * plotH;
            g2.fill(new Ellipse2D.Double(px - 3.5, py - 3.5, 7, 7));
        }

        g2.dispose();
        saveImage(image, "discount_likes_count_figure.png");
    }

    public void deleteAllProducts() {
        File file = new File(PRODUCT_FILE_PATH);
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        try (BufferedWriter bw = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            bw.write("");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void drawBarChart(String title, String xAxisLabel, String yAxisLabel,
                              List<String> labels, List<Long> values, Color barColor, String fileName) {
        int width = 950;
        int height = 650;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = createGraphics(image, width, height);

        drawTitle(g2, title, width);

        if (labels.isEmpty()) {
            drawNoData(g2, width, height);
            saveImage(image, fileName);
            return;
        }

        int left = 95;
        int right = width - 40;
        int top = 75;
        int bottom = height - 130;
        int plotW = right - left;
        int plotH = bottom - top;

        long maxVal = 1;
        for (Long v : values) {
            if (v > maxVal) {
                maxVal = v;
            }
        }

        g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        FontMetrics fm = g2.getFontMetrics();
        int gridLines = 5;
        for (int i = 0; i <= gridLines; i++) {
            int y = bottom - (int) ((double) i / gridLines * plotH);
            g2.setColor(new Color(230, 230, 230));
            g2.drawLine(left, y, right, y);

            long tickVal = Math.round((double) i / gridLines * maxVal);
            String tickStr = String.valueOf(tickVal);
            g2.setColor(Color.DARK_GRAY);
            g2.drawString(tickStr, left - fm.stringWidth(tickStr) - 8, y + 4);
        }

        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawLine(left, bottom, right, bottom);
        g2.drawLine(left, top, left, bottom);

        int count = labels.size();
        double slotW = (double) plotW / count;
        int barW = Math.max(8, Math.min(60, (int) (slotW * 0.6)));

        for (int i = 0; i < count; i++) {
            long val = values.get(i);
            int barH = (int) ((double) val / maxVal * plotH);
            int x = left + (int) (i * slotW + (slotW - barW) / 2);
            int y = bottom - barH;

            g2.setColor(barColor);
            g2.fillRect(x, y, barW, barH);

            g2.setColor(Color.DARK_GRAY);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            String valStr = String.valueOf(val);
            int valX = x + (barW - g2.getFontMetrics().stringWidth(valStr)) / 2;
            g2.drawString(valStr, valX, Math.max(top - 4, y - 5));

            String catLabel = labels.get(i);
            if (catLabel.length() > 16) {
                catLabel = catLabel.substring(0, 14) + "..";
            }
            int labelX = left + (int) (i * slotW + slotW / 2);
            int labelY = bottom + 14;
            g2.rotate(Math.toRadians(35), labelX, labelY);
            g2.drawString(catLabel, labelX, labelY);
            g2.rotate(Math.toRadians(-35), labelX, labelY);
        }

        g2.setColor(Color.BLACK);
        g2.setFont(new Font("SansSerif", Font.BOLD, 13));
        g2.drawString(xAxisLabel, left + (plotW - g2.getFontMetrics().stringWidth(xAxisLabel)) / 2, height - 18);

        g2.rotate(-Math.PI / 2);
        g2.drawString(yAxisLabel, -(top + (plotH + g2.getFontMetrics().stringWidth(yAxisLabel)) / 2), 25);
        g2.rotate(Math.PI / 2);

        g2.dispose();
        saveImage(image, fileName);
    }

    private void drawPieChart(String title, String[] labels, int[] counts, Color[] colors, String fileName) {
        int width = 850;
        int height = 600;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = createGraphics(image, width, height);

        drawTitle(g2, title, width);

        int total = 0;
        for (int c : counts) {
            total += c;
        }

        if (total == 0) {
            drawNoData(g2, width, height);
            saveImage(image, fileName);
            return;
        }

        int diameter = 360;
        int pieX = 110;
        int pieY = 125;
        double startAngle = 0.0;

        for (int i = 0; i < counts.length; i++) {
            double angle = (double) counts[i] / total * 360.0;
            g2.setColor(colors[i % colors.length]);
            g2.fill(new Arc2D.Double(pieX, pieY, diameter, diameter, startAngle, angle, Arc2D.PIE));
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(2f));
            g2.draw(new Arc2D.Double(pieX, pieY, diameter, diameter, startAngle, angle, Arc2D.PIE));
            startAngle += angle;
        }

        int legendX = 530;
        int legendY = 190;
        g2.setFont(new Font("SansSerif", Font.BOLD, 14));
        g2.setColor(Color.BLACK);
        g2.drawString("Discount Range", legendX, legendY - 20);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
        for (int i = 0; i < labels.length; i++) {
            int y = legendY + i * 38;
            g2.setColor(colors[i % colors.length]);
            g2.fillRect(legendX, y, 20, 20);
            g2.setColor(Color.DARK_GRAY);
            g2.drawRect(legendX, y, 20, 20);

            double pct = (double) counts[i] / total * 100.0;
            String text = String.format("%s: %d (%.1f%%)", labels[i], counts[i], pct);
            g2.setColor(Color.BLACK);
            g2.drawString(text, legendX + 30, y + 15);
        }

        g2.dispose();
        saveImage(image, fileName);
    }

    private Graphics2D createGraphics(BufferedImage image, int width, int height) {
        Graphics2D g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, width, height);
        return g2;
    }

    private void drawTitle(Graphics2D g2, String title, int width) {
        g2.setColor(Color.BLACK);
        g2.setFont(new Font("SansSerif", Font.BOLD, 18));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(title, (width - fm.stringWidth(title)) / 2, 42);
    }

    private void drawNoData(Graphics2D g2, int width, int height) {
        g2.setColor(Color.GRAY);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 16));
        String msg = "No product data available";
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(msg, (width - fm.stringWidth(msg)) / 2, height / 2);
        g2.dispose();
    }

    private void saveImage(BufferedImage image, String fileName) {
        File folder = new File(FIGURE_FOLDER);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        File outputFile = new File(folder, fileName);
        try {
            ImageIO.write(image, "png", outputFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void ensureProductFileExists() {
        File file = new File(PRODUCT_FILE_PATH);
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void collectCsvFiles(File dir, List<File> csvFiles) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                collectCsvFiles(file, csvFiles);
            } else if (file.getName().toLowerCase().endsWith(".csv")) {
                csvFiles.add(file);
            }
        }
    }

    private List<Product> loadAllProducts() {
        List<Product> products = new ArrayList<>();
        File file = new File(PRODUCT_FILE_PATH);
        if (!file.exists()) {
            return products;
        }

        Set<String> seenIds = new LinkedHashSet<>();
        try (BufferedReader br = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.startsWith("\uFEFF")) {
                    trimmed = trimmed.substring(1).trim();
                }
                Product product = parseStoredProductLine(trimmed);
                if (product != null && !product.getProId().isEmpty() && !seenIds.contains(product.getProId())) {
                    seenIds.add(product.getProId());
                    products.add(product);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return products;
    }

    private Product parseStoredProductLine(String line) {
        if (line.startsWith("{") && line.endsWith("}")) {
            Map<String, String> map = parseJsonObject(line);
            String id = map.getOrDefault("pro_id", "");
            String model = map.getOrDefault("pro_model", "");
            String category = map.getOrDefault("pro_category", "");
            String name = map.getOrDefault("pro_name", "");
            double currentPrice = parseDoubleSafe(map.getOrDefault("pro_current_price", "0"));
            double rawPrice = parseDoubleSafe(map.getOrDefault("pro_raw_price", "0"));
            double discount = parseDoubleSafe(map.getOrDefault("pro_discount", "0"));
            int likesCount = parseIntSafe(map.getOrDefault("pro_likes_count", "0"));
            return new Product(id, model, category, name, currentPrice, rawPrice, discount, likesCount);
        }

        List<String> cols = parseCsvLine(line);
        if (cols.size() >= 8) {
            return new Product(
                    cols.get(0).trim(),
                    cols.get(1).trim(),
                    cols.get(2).trim(),
                    cols.get(3).trim(),
                    parseDoubleSafe(cols.get(4)),
                    parseDoubleSafe(cols.get(5)),
                    parseDoubleSafe(cols.get(6)),
                    parseIntSafe(cols.get(7))
            );
        }
        return null;
    }

    private Map<String, String> parseJsonObject(String json) {
        Map<String, String> result = new HashMap<>();
        String body = json.substring(1, json.length() - 1).trim();
        if (body.isEmpty()) {
            return result;
        }

        List<String> pairs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '"' && !isEscaped(body, i)) {
                inQuotes = !inQuotes;
                current.append(c);
            } else if (c == ',' && !inQuotes) {
                pairs.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        pairs.add(current.toString());

        for (String pair : pairs) {
            inQuotes = false;
            int colonIdx = -1;
            for (int i = 0; i < pair.length(); i++) {
                char c = pair.charAt(i);
                if (c == '"' && !isEscaped(pair, i)) {
                    inQuotes = !inQuotes;
                } else if (c == ':' && !inQuotes) {
                    colonIdx = i;
                    break;
                }
            }
            if (colonIdx != -1) {
                String key = unquote(pair.substring(0, colonIdx).trim());
                String val = unquote(pair.substring(colonIdx + 1).trim());
                result.put(key, val);
            }
        }
        return result;
    }

    private boolean isEscaped(String text, int quoteIndex) {
        int backslashes = 0;
        int i = quoteIndex - 1;
        while (i >= 0 && text.charAt(i) == '\\') {
            backslashes++;
            i--;
        }
        return backslashes % 2 == 1;
    }

    private String unquote(String text) {
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length() - 1)
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return text;
    }

    private void saveAllProducts(List<Product> products) {
        File file = new File(PRODUCT_FILE_PATH);
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        try (BufferedWriter bw = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            for (Product p : products) {
                bw.write(p.toString());
                bw.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Product mapRowToProduct(List<String> cols, Map<String, Integer> colIndex) {
        String id = getColValue(cols, colIndex, "id", "product_id", "pro_id");
        String model = getColValue(cols, colIndex, "model", "product_model", "pro_model");
        String category = getColValue(cols, colIndex, "category", "pro_category");
        String name = getColValue(cols, colIndex, "name", "product_name", "pro_name");
        double currentPrice = parseDoubleSafe(getColValue(cols, colIndex, "current_price", "pro_current_price"));
        double rawPrice = parseDoubleSafe(getColValue(cols, colIndex, "raw_price", "pro_raw_price"));
        double discount = parseDoubleSafe(getColValue(cols, colIndex, "discount", "pro_discount"));
        int likesCount = parseIntSafe(getColValue(cols, colIndex, "likes_count", "pro_likes_count"));

        if (id.isEmpty() && cols.size() >= 8) {
            id = cols.get(0).trim();
            model = cols.get(1).trim();
            category = cols.get(2).trim();
            name = cols.get(3).trim();
            currentPrice = parseDoubleSafe(cols.get(4));
            rawPrice = parseDoubleSafe(cols.get(5));
            discount = parseDoubleSafe(cols.get(6));
            likesCount = parseIntSafe(cols.get(7));
        }

        return new Product(id, model, category, name, currentPrice, rawPrice, discount, likesCount);
    }

    private String getColValue(List<String> cols, Map<String, Integer> colIndex, String... keys) {
        for (String key : keys) {
            Integer idx = colIndex.get(key);
            if (idx != null && idx >= 0 && idx < cols.size()) {
                return cols.get(idx).trim();
            }
        }
        return "";
    }

    private double parseDoubleSafe(String value) {
        if (value == null) {
            return 0.0;
        }
        try {
            String cleaned = value.trim().replace("%", "").replace("$", "");
            return Double.parseDouble(cleaned);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private int parseIntSafe(String value) {
        if (value == null) {
            return 0;
        }
        try {
            String cleaned = value.trim().replace("%", "").replace("$", "");
            return (int) Math.round(Double.parseDouble(cleaned));
        } catch (Exception e) {
            return 0;
        }
    }

    private List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result;
    }
}
