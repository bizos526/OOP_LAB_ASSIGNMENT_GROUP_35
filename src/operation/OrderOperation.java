package operation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import model.Order;

public class OrderOperation {
    private static OrderOperation instance;

    private static final String DATA_DIR = "data";
    private static final String ORDERS_FILE_PATH = "data/orders.txt";
    private static final String USERS_FILE_PATH = "data/users.txt";
    private static final String PRODUCTS_FILE_PATH = "data/products.txt";
    private static final String FIGURE_DIR = "data/figure";
    private static final int PAGE_SIZE = 10;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy_HH:mm:ss");
    private static final String ALPHANUMERIC = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private final Random random = new Random();

    private OrderOperation() {
        ensureDirectoryExists(DATA_DIR);
        ensureDirectoryExists(FIGURE_DIR);
    }

    public static synchronized OrderOperation getInstance() {
        if (instance == null) {
            instance = new OrderOperation();
        }
        return instance;
    }

    public String generateUniqueOrderId() {
        Set<String> existingIds = new HashSet<>();
        for (Order order : loadAllOrders()) {
            if (order.getOrderId() != null) {
                existingIds.add(order.getOrderId());
            }
        }

        String candidate;
        do {
            int num = random.nextInt(100000);
            candidate = String.format(Locale.ROOT, "o_%05d", num);
        } while (existingIds.contains(candidate));

        return candidate;
    }

    public boolean createAnOrder(String customerId, String productId, String createTime) {
        if (customerId == null || customerId.trim().isEmpty()
                || productId == null || productId.trim().isEmpty()) {
            return false;
        }

        String trimmedCustomerId = customerId.trim();
        String trimmedProductId = productId.trim();

        if (!isCustomerExists(trimmedCustomerId) || !isProductExists(trimmedProductId)) {
            return false;
        }

        String orderTime = (createTime == null || createTime.trim().isEmpty())
                ? LocalDateTime.now().format(TIME_FORMATTER)
                : createTime.trim();

        String orderId = generateUniqueOrderId();
        Order newOrder = new Order(orderId, trimmedCustomerId, trimmedProductId, orderTime);

        List<Order> allOrders = loadAllOrders();
        allOrders.add(newOrder);
        return saveAllOrders(allOrders);
    }

    public boolean deleteOrder(String orderId) {
        if (orderId == null || orderId.trim().isEmpty()) {
            return false;
        }

        List<Order> allOrders = loadAllOrders();
        boolean removed = allOrders.removeIf(o -> orderId.trim().equals(o.getOrderId()));
        if (!removed) {
            return false;
        }

        return saveAllOrders(allOrders);
    }

    public OrderListResult getOrderList(String customerId, int pageNumber) {
        List<Order> customerOrders = new ArrayList<>();
        if (customerId != null && !customerId.trim().isEmpty()) {
            String targetId = customerId.trim();
            for (Order order : loadAllOrders()) {
                if (targetId.equals(order.getUserId())) {
                    customerOrders.add(order);
                }
            }
        }

        int totalItems = customerOrders.size();
        int totalPages = (int) Math.ceil((double) totalItems / PAGE_SIZE);

        if (totalItems == 0 || pageNumber < 1 || pageNumber > totalPages) {
            return new OrderListResult(new ArrayList<>(), pageNumber, totalPages);
        }

        int startIndex = (pageNumber - 1) * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, totalItems);
        List<Order> pageItems = new ArrayList<>(customerOrders.subList(startIndex, endIndex));

        return new OrderListResult(pageItems, pageNumber, totalPages);
    }

    public void generateTestOrderData() {
        ensureDirectoryExists(DATA_DIR);
        // Test orders must refer to products that already exist.
        if (loadProductMetaMap().isEmpty()) {
            return;
        }

        List<String> existingUserLines = loadRawLines(USERS_FILE_PATH);
        Set<String> existingUserIds = new HashSet<>();
        Set<String> existingUserNames = new HashSet<>();

        for (String line : existingUserLines) {
            Map<String, String> map = parseJsonObjectToMap(line);
            String uid = map.get("user_id");
            String uname = map.get("user_name");
            if (uid != null && !uid.isEmpty()) {
                existingUserIds.add(uid);
            }
            if (uname != null && !uname.isEmpty()) {
                existingUserNames.add(uname.toLowerCase(Locale.ROOT));
            }
        }

        List<String> generatedCustomerIds = new ArrayList<>();
        int suffixIndex = 1;
        while (generatedCustomerIds.size() < 10) {
            String userName = "test_customer_" + indexToLetters(suffixIndex);
            suffixIndex++;
            if (existingUserNames.contains(userName.toLowerCase(Locale.ROOT))) {
                continue;
            }

            String userId;
            do {
                long digits = 1000000000L + (Math.abs(random.nextLong()) % 9000000000L);
                userId = "u_" + digits;
            } while (existingUserIds.contains(userId));

            existingUserIds.add(userId);
            existingUserNames.add(userName.toLowerCase(Locale.ROOT));
            generatedCustomerIds.add(userId);

            String encryptedPassword = encryptPassword("Pass12345");
            String registerTime = LocalDateTime.now().format(TIME_FORMATTER);
            String email = userName + "@gmail.com";
            String mobile = String.format(Locale.ROOT, "04%08d", random.nextInt(100000000));

            String customerJson = String.format(
                    "{\"user_id\":\"%s\", \"user_name\":\"%s\", \"user_password\":\"%s\", "
                            + "\"user_register_time\":\"%s\", \"user_role\":\"customer\", "
                            + "\"user_email\":\"%s\", \"user_mobile\":\"%s\"}",
                    userId, userName, encryptedPassword, registerTime, email, mobile);
            existingUserLines.add(customerJson);
        }
        if (!saveRawLines(USERS_FILE_PATH, existingUserLines)) {
            return;
        }

        Map<String, ProductMeta> productMap = loadProductMetaMap();
        if (productMap.isEmpty()) {
            return;
        }

        List<String> productIds = new ArrayList<>(productMap.keySet());
        List<Order> allOrders = loadAllOrders();
        Set<String> existingOrderIds = new HashSet<>();
        for (Order o : allOrders) {
            existingOrderIds.add(o.getOrderId());
        }

        int currentYear = LocalDateTime.now().getYear();
        for (String custId : generatedCustomerIds) {
            int orderCount = 50 + random.nextInt(151);
            for (int i = 0; i < orderCount; i++) {
                String orderId;
                do {
                    orderId = String.format(Locale.ROOT, "o_%05d", random.nextInt(100000));
                } while (existingOrderIds.contains(orderId));
                existingOrderIds.add(orderId);

                String proId = productIds.get(random.nextInt(productIds.size()));
                int month = 1 + random.nextInt(12);
                int day = 1 + random.nextInt(28);
                int hour = random.nextInt(24);
                int minute = random.nextInt(60);
                int second = random.nextInt(60);

                String orderTime = String.format(
                        Locale.ROOT,
                        "%02d-%02d-%04d_%02d:%02d:%02d",
                        day, month, currentYear, hour, minute, second);

                allOrders.add(new Order(orderId, custId, proId, orderTime));
            }
        }

        saveAllOrders(allOrders);
    }

    public void generateSingleCustomerConsumptionFigure(String customerId) {
        double[] monthlyTotals = new double[12];
        Map<String, ProductMeta> productMap = loadProductMetaMap();

        if (customerId != null && !customerId.trim().isEmpty()) {
            String targetId = customerId.trim();
            for (Order order : loadAllOrders()) {
                if (targetId.equals(order.getUserId())) {
                    int month = extractMonth(order.getOrderTime());
                    if (month >= 1 && month <= 12) {
                        ProductMeta meta = productMap.get(order.getProId());
                        double price = (meta != null) ? meta.price : 0.0;
                        monthlyTotals[month - 1] += price;
                    }
                }
            }
        }

        List<ChartEntry> entries = buildMonthlyChartEntries(monthlyTotals);
        renderBarChart(
                "MONTHLY CONSUMPTION - " + (customerId != null ? customerId.toUpperCase(Locale.ROOT) : "CUSTOMER"),
                "MONTH",
                "CONSUMPTION",
                entries,
                0xFF2B7DE9,
                FIGURE_DIR + "/single_customer_consumption_figure.png");
    }

    public void generateAllCustomersConsumptionFigure() {
        double[] monthlyTotals = new double[12];
        Map<String, ProductMeta> productMap = loadProductMetaMap();

        for (Order order : loadAllOrders()) {
            int month = extractMonth(order.getOrderTime());
            if (month >= 1 && month <= 12) {
                ProductMeta meta = productMap.get(order.getProId());
                double price = (meta != null) ? meta.price : 0.0;
                monthlyTotals[month - 1] += price;
            }
        }

        List<ChartEntry> entries = buildMonthlyChartEntries(monthlyTotals);
        renderBarChart(
                "ALL CUSTOMERS MONTHLY CONSUMPTION",
                "MONTH",
                "CONSUMPTION",
                entries,
                0xFF0F9D58,
                FIGURE_DIR + "/all_customers_consumption_figure.png");
    }

    public void generateAllTop10BestSellersFigure() {
        Map<String, Integer> salesCount = new HashMap<>();
        for (Order order : loadAllOrders()) {
            String proId = order.getProId();
            if (proId != null && !proId.isEmpty()) {
                salesCount.put(proId, salesCount.getOrDefault(proId, 0) + 1);
            }
        }

        Map<String, ProductMeta> productMap = loadProductMetaMap();
        List<ChartEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : salesCount.entrySet()) {
            String proId = entry.getKey();
            ProductMeta meta = productMap.get(proId);
            String label = (meta != null && !meta.name.isEmpty()) ? meta.name : proId;
            entries.add(new ChartEntry(label, entry.getValue()));
        }

        entries.sort((a, b) -> Double.compare(b.value, a.value));
        if (entries.size() > 10) {
            entries = new ArrayList<>(entries.subList(0, 10));
        }

        renderBarChart(
                "TOP 10 BEST-SELLING PRODUCTS",
                "PRODUCT",
                "SOLD QTY",
                entries,
                0xFFEA4335,
                FIGURE_DIR + "/top_10_best_sellers_figure.png");
    }

    public void deleteAllOrders() {
        saveAllOrders(new ArrayList<>());
    }

    private boolean isCustomerExists(String customerId) {
        for (String line : loadRawLines(USERS_FILE_PATH)) {
            Map<String, String> map = parseJsonObjectToMap(line);
            String uid = map.getOrDefault("user_id", "");
            String role = map.getOrDefault("user_role", "");
            if (customerId.equals(uid) && "customer".equalsIgnoreCase(role)) {
                return true;
            }
        }
        return false;
    }

    private boolean isProductExists(String productId) {
        return loadProductMetaMap().containsKey(productId);
    }

    private String indexToLetters(int index) {
        StringBuilder sb = new StringBuilder();
        int n = index;
        while (n > 0) {
            n--;
            sb.insert(0, (char) ('a' + (n % 26)));
            n /= 26;
        }
        return sb.toString();
    }

    private void ensureDirectoryExists(String dirPath) {
        File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    private List<Order> loadAllOrders() {
        List<Order> orders = new ArrayList<>();
        for (String line : loadRawLines(ORDERS_FILE_PATH)) {
            Map<String, String> map = parseJsonObjectToMap(line);
            String orderId = map.getOrDefault("order_id", "");
            String userId = map.getOrDefault("user_id", "");
            String proId = map.getOrDefault("pro_id", "");
            String orderTime = map.getOrDefault("order_time", "");
            if (!orderId.isEmpty()) {
                orders.add(new Order(orderId, userId, proId, orderTime));
            }
        }
        return orders;
    }

    private boolean saveAllOrders(List<Order> orders) {
        List<String> lines = new ArrayList<>();
        for (Order order : orders) {
            lines.add(order.toString());
        }
        return saveRawLines(ORDERS_FILE_PATH, lines);
    }

    private List<String> loadRawLines(String filePath) {
        List<String> lines = new ArrayList<>();
        File file = new File(filePath);
        if (!file.exists()) {
            return lines;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    lines.add(trimmed);
                }
            }
        } catch (IOException e) {
            return new ArrayList<>();
        }
        return lines;
    }

    private boolean saveRawLines(String filePath, List<String> lines) {
        ensureDirectoryExists(DATA_DIR);
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filePath, false), "UTF-8"))) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private Map<String, ProductMeta> loadProductMetaMap() {
        Map<String, ProductMeta> map = new LinkedHashMap<>();
        for (String line : loadRawLines(PRODUCTS_FILE_PATH)) {
            Map<String, String> json = parseJsonObjectToMap(line);
            String proId = json.getOrDefault("pro_id", "");
            if (proId.isEmpty()) {
                continue;
            }
            String proName = json.getOrDefault("pro_name", proId);
            double price = parseDoubleSafe(json.getOrDefault("pro_current_price", "0"), 0.0);
            map.put(proId, new ProductMeta(proName, price));
        }
        return map;
    }

    private String encryptPassword(String rawPassword) {
        int len = rawPassword.length();
        StringBuilder randomStr = new StringBuilder(len * 2);
        for (int i = 0; i < len * 2; i++) {
            randomStr.append(ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length())));
        }

        StringBuilder combined = new StringBuilder("^^");
        for (int i = 0; i < len; i++) {
            combined.append(randomStr.charAt(i * 2));
            combined.append(randomStr.charAt(i * 2 + 1));
            combined.append(rawPassword.charAt(i));
        }
        combined.append("$$");
        return combined.toString();
    }

    private int extractMonth(String orderTime) {
        if (orderTime == null || orderTime.isEmpty()) {
            return -1;
        }
        try {
            String datePart = orderTime.split("_")[0];
            String[] parts = datePart.split("-");
            if (parts.length >= 2) {
                return Integer.parseInt(parts[1]);
            }
        } catch (NumberFormatException ignored) {
        }
        return -1;
    }

    private List<ChartEntry> buildMonthlyChartEntries(double[] monthlyTotals) {
        String[] monthNames = {
                "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
                "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"
        };
        List<ChartEntry> entries = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            entries.add(new ChartEntry(monthNames[i], monthlyTotals[i]));
        }
        return entries;
    }

    private Map<String, String> parseJsonObjectToMap(String json) {
        Map<String, String> result = new HashMap<>();
        if (json == null) {
            return result;
        }
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return result;
        }

        String body = trimmed.substring(1, trimmed.length() - 1);
        int n = body.length();
        int i = 0;

        while (i < n) {
            while (i < n && (Character.isWhitespace(body.charAt(i)) || body.charAt(i) == ',')) {
                i++;
            }
            if (i >= n || body.charAt(i) != '"') {
                break;
            }

            int[] nextPos = new int[1];
            String key = readJsonQuotedString(body, i, nextPos);
            i = nextPos[0];

            while (i < n && (Character.isWhitespace(body.charAt(i)) || body.charAt(i) == ':')) {
                i++;
            }
            if (i >= n) {
                break;
            }

            String value;
            if (body.charAt(i) == '"') {
                value = readJsonQuotedString(body, i, nextPos);
                i = nextPos[0];
            } else {
                int start = i;
                while (i < n && body.charAt(i) != ',') {
                    i++;
                }
                value = body.substring(start, i).trim();
                if ("null".equalsIgnoreCase(value)) {
                    value = "";
                }
            }

            result.put(key.trim().toLowerCase(Locale.ROOT), value);
        }
        return result;
    }

    private String readJsonQuotedString(String text, int openQuoteIndex, int[] outNextIndex) {
        StringBuilder sb = new StringBuilder();
        int i = openQuoteIndex + 1;
        int n = text.length();
        while (i < n) {
            char ch = text.charAt(i);
            if (ch == '\\' && i + 1 < n) {
                char next = text.charAt(i + 1);
                if (next == '"' || next == '\\' || next == '/') {
                    sb.append(next);
                } else if (next == 'n' || next == 'r' || next == 't') {
                    sb.append(' ');
                } else {
                    sb.append(next);
                }
                i += 2;
            } else if (ch == '"') {
                i++;
                break;
            } else {
                sb.append(ch);
                i++;
            }
        }
        outNextIndex[0] = i;
        return sb.toString();
    }

    private double parseDoubleSafe(String value, double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            String cleaned = value.replaceAll("[^0-9.-]", "");
            return cleaned.isEmpty() ? defaultValue : Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void renderBarChart(String title, String xLabel, String yLabel,
            List<ChartEntry> entries,
            int barColor, String outputPath) {
        ensureDirectoryExists(FIGURE_DIR);
        int width = 960;
        int height = 600;
        PixelCanvas canvas = new PixelCanvas(width, height, 0xFFFFFFFF);

        canvas.drawTextCentered(title, width / 2, 24, 2, 0xFF1F2937);

        if (entries == null || entries.isEmpty()) {
            canvas.drawTextCentered("NO ORDER DATA AVAILABLE", width / 2, height / 2, 2, 0xFF6B7280);
            saveCanvasToPng(canvas, outputPath);
            return;
        }

        int left = 115;
        int right = width - 50;
        int top = 75;
        int bottom = height - 110;
        int chartWidth = right - left;
        int chartHeight = bottom - top;

        double maxVal = 1.0;
        for (ChartEntry e : entries) {
            if (e.value > maxVal) {
                maxVal = e.value;
            }
        }

        int ticks = 5;
        for (int t = 0; t <= ticks; t++) {
            int y = bottom - (int) ((long) chartHeight * t / ticks);
            canvas.drawLine(left, y, right, y, 0xFFE5E7EB);
            double tickValue = maxVal * t / ticks;
            String label = String.format(Locale.ROOT, "%.0f", tickValue);
            canvas.drawText(label, left - (label.length() * 6 + 10), y - 3, 1, 0xFF374151);
        }

        canvas.drawLine(left, top, left, bottom, 0xFF111827);
        canvas.drawLine(left, bottom, right, bottom, 0xFF111827);
        canvas.drawText(yLabel, 20, top - 20, 1, 0xFF111827);
        canvas.drawTextCentered(xLabel, (left + right) / 2, height - 25, 2, 0xFF111827);

        int count = Math.min(entries.size(), 12);
        int slotWidth = chartWidth / count;
        int barWidth = Math.max(12, (int) (slotWidth * 0.6));

        for (int i = 0; i < count; i++) {
            ChartEntry entry = entries.get(i);
            double val = entry.value;
            int barHeight = (int) (val / maxVal * chartHeight);
            int barX = left + i * slotWidth + (slotWidth - barWidth) / 2;
            int barY = bottom - barHeight;

            canvas.fillRect(barX, barY, barWidth, barHeight, barColor);

            String valText = (val == Math.rint(val))
                    ? String.format(Locale.ROOT, "%.0f", val)
                    : String.format(Locale.ROOT, "%.1f", val);
            canvas.drawTextCentered(valText, barX + barWidth / 2, Math.max(top - 12, barY - 12), 1, 0xFF111827);

            String itemLabel = truncateLabel(entry.label.toUpperCase(Locale.ROOT), 10);
            int labelY = bottom + 10 + (i % 2) * 16;
            canvas.drawTextCentered(itemLabel, barX + barWidth / 2, labelY, 1, 0xFF374151);
        }

        saveCanvasToPng(canvas, outputPath);
    }

    private String truncateLabel(String text, int maxLen) {
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen - 2) + "..";
    }

    private void saveCanvasToPng(PixelCanvas canvas, String outputPath) {
        try {
            File file = new File(outputPath);
            ByteArrayOutputStream rawScanlines = new ByteArrayOutputStream();
            for (int y = 0; y < canvas.height; y++) {
                rawScanlines.write(0);
                for (int x = 0; x < canvas.width; x++) {
                    int argb = canvas.pixels[y * canvas.width + x];
                    rawScanlines.write((argb >> 16) & 0xFF);
                    rawScanlines.write((argb >> 8) & 0xFF);
                    rawScanlines.write(argb & 0xFF);
                    rawScanlines.write((argb >> 24) & 0xFF);
                }
            }

            byte[] uncompressed = rawScanlines.toByteArray();
            Deflater deflater = new Deflater();
            deflater.setInput(uncompressed);
            deflater.finish();

            ByteArrayOutputStream compressedStream = new ByteArrayOutputStream();
            try {
                deflater.setInput(uncompressed);
                deflater.finish();

                byte[] buffer = new byte[8192];
                while (!deflater.finished()) {
                    int count = deflater.deflate(buffer);
                    compressedStream.write(buffer, 0, count);
                }
            } finally {
                deflater.end();
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(new byte[] { (byte) 137, 80, 78, 71, 13, 10, 26, 10 });

                ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
                writeIntBigEndian(ihdr, canvas.width);
                writeIntBigEndian(ihdr, canvas.height);
                ihdr.write(8);
                ihdr.write(6);
                ihdr.write(0);
                ihdr.write(0);
                ihdr.write(0);
                writePngChunk(fos, "IHDR", ihdr.toByteArray());
                writePngChunk(fos, "IDAT", compressedStream.toByteArray());
                writePngChunk(fos, "IEND", new byte[0]);
            }
        } catch (IOException ignored) {
        }
    }

    private void writePngChunk(FileOutputStream fos, String type, byte[] data) throws IOException {
        writeIntBigEndian(fos, data.length);
        byte[] typeBytes = type.getBytes("US-ASCII");
        fos.write(typeBytes);
        fos.write(data);

        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        writeIntBigEndian(fos, (int) crc.getValue());
    }

    private void writeIntBigEndian(OutputStream out, int value) throws IOException {
        out.write((value >> 24) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static class ProductMeta {
        final String name;
        final double price;

        ProductMeta(String name, double price) {
            this.name = name;
            this.price = price;
        }
    }

    private static class ChartEntry {
        final String label;
        final double value;

        ChartEntry(String label, double value) {
            this.label = label;
            this.value = value;
        }
    }

    private static class PixelCanvas {
        final int width;
        final int height;
        final int[] pixels;

        private static final Map<Character, int[]> FONT_5X7 = createFontMap();

        PixelCanvas(int width, int height, int backgroundColor) {
            this.width = width;
            this.height = height;
            this.pixels = new int[width * height];
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = backgroundColor;
            }
        }

        void setPixel(int x, int y, int color) {
            if (x >= 0 && x < width && y >= 0 && y < height) {
                pixels[y * width + x] = color;
            }
        }

        void fillRect(int x, int y, int w, int h, int color) {
            for (int dy = 0; dy < h; dy++) {
                for (int dx = 0; dx < w; dx++) {
                    setPixel(x + dx, y + dy, color);
                }
            }
        }

        void drawLine(int x0, int y0, int x1, int y1, int color) {
            int dx = Math.abs(x1 - x0);
            int dy = Math.abs(y1 - y0);
            int sx = x0 < x1 ? 1 : -1;
            int sy = y0 < y1 ? 1 : -1;
            int err = dx - dy;

            while (true) {
                setPixel(x0, y0, color);
                if (x0 == x1 && y0 == y1) {
                    break;
                }
                int e2 = 2 * err;
                if (e2 > -dy) {
                    err -= dy;
                    x0 += sx;
                }
                if (e2 < dx) {
                    err += dx;
                    y0 += sy;
                }
            }
        }

        void drawTextCentered(String text, int centerX, int y, int scale, int color) {
            if (text == null) {
                return;
            }
            int textWidth = text.length() * 6 * scale;
            drawText(text, centerX - textWidth / 2, y, scale, color);
        }

        void drawText(String text, int x, int y, int scale, int color) {
            if (text == null) {
                return;
            }
            int cursorX = x;
            for (int i = 0; i < text.length(); i++) {
                char ch = Character.toUpperCase(text.charAt(i));
                int[] glyph = FONT_5X7.getOrDefault(ch, FONT_5X7.get(' '));
                for (int row = 0; row < 7; row++) {
                    int rowBits = glyph[row];
                    for (int col = 0; col < 5; col++) {
                        if (((rowBits >> (4 - col)) & 1) == 1) {
                            fillRect(cursorX + col * scale, y + row * scale, scale, scale, color);
                        }
                    }
                }
                cursorX += 6 * scale;
            }
        }

        private static Map<Character, int[]> createFontMap() {
            Map<Character, int[]> m = new HashMap<>();
            m.put(' ', new int[] { 0, 0, 0, 0, 0, 0, 0 });
            m.put('A', new int[] { 14, 17, 17, 31, 17, 17, 17 });
            m.put('B', new int[] { 30, 17, 17, 30, 17, 17, 30 });
            m.put('C', new int[] { 14, 17, 16, 16, 16, 17, 14 });
            m.put('D', new int[] { 28, 18, 17, 17, 17, 18, 28 });
            m.put('E', new int[] { 31, 16, 16, 30, 16, 16, 31 });
            m.put('F', new int[] { 31, 16, 16, 30, 16, 16, 16 });
            m.put('G', new int[] { 14, 17, 16, 23, 17, 17, 15 });
            m.put('H', new int[] { 17, 17, 17, 31, 17, 17, 17 });
            m.put('I', new int[] { 14, 4, 4, 4, 4, 4, 14 });
            m.put('J', new int[] { 7, 2, 2, 2, 2, 18, 12 });
            m.put('K', new int[] { 17, 18, 20, 24, 20, 18, 17 });
            m.put('L', new int[] { 16, 16, 16, 16, 16, 16, 31 });
            m.put('M', new int[] { 17, 27, 21, 21, 17, 17, 17 });
            m.put('N', new int[] { 17, 25, 21, 19, 17, 17, 17 });
            m.put('O', new int[] { 14, 17, 17, 17, 17, 17, 14 });
            m.put('P', new int[] { 30, 17, 17, 30, 16, 16, 16 });
            m.put('Q', new int[] { 14, 17, 17, 17, 21, 18, 13 });
            m.put('R', new int[] { 30, 17, 17, 30, 20, 18, 17 });
            m.put('S', new int[] { 15, 16, 16, 14, 1, 1, 30 });
            m.put('T', new int[] { 31, 4, 4, 4, 4, 4, 4 });
            m.put('U', new int[] { 17, 17, 17, 17, 17, 17, 14 });
            m.put('V', new int[] { 17, 17, 17, 17, 17, 10, 4 });
            m.put('W', new int[] { 17, 17, 17, 21, 21, 27, 17 });
            m.put('X', new int[] { 17, 17, 10, 4, 10, 17, 17 });
            m.put('Y', new int[] { 17, 17, 10, 4, 4, 4, 4 });
            m.put('Z', new int[] { 31, 1, 2, 4, 8, 16, 31 });
            m.put('0', new int[] { 14, 17, 19, 21, 25, 17, 14 });
            m.put('1', new int[] { 4, 12, 4, 4, 4, 4, 14 });
            m.put('2', new int[] { 14, 17, 1, 2, 4, 8, 31 });
            m.put('3', new int[] { 30, 1, 1, 14, 1, 1, 30 });
            m.put('4', new int[] { 2, 6, 10, 18, 31, 2, 2 });
            m.put('5', new int[] { 31, 16, 30, 1, 1, 17, 14 });
            m.put('6', new int[] { 6, 8, 16, 30, 17, 17, 14 });
            m.put('7', new int[] { 31, 1, 2, 4, 8, 8, 8 });
            m.put('8', new int[] { 14, 17, 17, 14, 17, 17, 14 });
            m.put('9', new int[] { 14, 17, 17, 15, 1, 2, 12 });
            m.put('.', new int[] { 0, 0, 0, 0, 0, 12, 12 });
            m.put('-', new int[] { 0, 0, 0, 31, 0, 0, 0 });
            m.put('_', new int[] { 0, 0, 0, 0, 0, 0, 31 });
            m.put(':', new int[] { 0, 12, 12, 0, 12, 12, 0 });
            m.put('%', new int[] { 25, 25, 2, 4, 8, 19, 19 });
            m.put('(', new int[] { 2, 4, 8, 8, 8, 4, 2 });
            m.put(')', new int[] { 8, 4, 2, 2, 2, 4, 8 });
            return m;
        }
    }
}
