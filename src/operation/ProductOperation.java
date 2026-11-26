package operation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
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

        if (!saveAllProducts(new ArrayList<>(productMap.values()))) {
            throw new IllegalStateException("Cannot write product data: " + PRODUCT_FILE_PATH);
        }
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

        if (!removed) return false;
        return saveAllProducts(products);
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
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Product p : loadAllProducts()) {
            String cat = p.getProCategory() == null || p.getProCategory().trim().isEmpty()
                    ? "Unknown" : p.getProCategory();
            counts.put(cat, counts.getOrDefault(cat, 0L) + 1);
        }
        List<String> labels = new ArrayList<>(counts.keySet());
        labels.sort((a, b) -> Long.compare(counts.get(b), counts.get(a)));
        List<Long> values = new ArrayList<>();
        for (String label : labels) values.add(counts.get(label));
        try { PngChart.bar("Total Products by Category", labels, values,
                FIGURE_FOLDER + "/category_figure.png"); }
        catch (IOException ex) { throw new IllegalStateException("Cannot create category chart", ex); }
    }

    public void generateDiscountFigure() {
        int low = 0, middle = 0, high = 0;
        for (Product p : loadAllProducts()) {
            if (p.getProDiscount() < 30) low++;
            else if (p.getProDiscount() <= 60) middle++;
            else high++;
        }
        try { PngChart.pie("Product Discount Distribution",
                new String[]{"< 30", "30 - 60", "> 60"},
                new int[]{low, middle, high}, FIGURE_FOLDER + "/discount_figure.png"); }
        catch (IOException ex) { throw new IllegalStateException("Cannot create discount chart", ex); }
    }

    public void generateLikesCountFigure() {
        Map<String, Long> totals = new LinkedHashMap<>();
        for (Product p : loadAllProducts()) {
            String cat = p.getProCategory() == null || p.getProCategory().trim().isEmpty()
                    ? "Unknown" : p.getProCategory();
            totals.put(cat, totals.getOrDefault(cat, 0L) + p.getProLikesCount());
        }
        List<String> labels = new ArrayList<>(totals.keySet());
        labels.sort((a, b) -> Long.compare(totals.get(b), totals.get(a)));
        List<Long> values = new ArrayList<>();
        for (String label : labels) values.add(totals.get(label));
        try { PngChart.bar("Total Likes by Category", labels, values,
                FIGURE_FOLDER + "/likes_count_figure.png"); }
        catch (IOException ex) { throw new IllegalStateException("Cannot create likes chart", ex); }
    }

    public void generateDiscountLikesCountFigure() {
        List<double[]> points = new ArrayList<>();
        for (Product p : loadAllProducts())
            points.add(new double[]{p.getProDiscount(), p.getProLikesCount()});
        try { PngChart.scatter("Relationship Between Discount and Likes Count", points,
                FIGURE_FOLDER + "/discount_likes_count_figure.png"); }
        catch (IOException ex) { throw new IllegalStateException("Cannot create scatter chart", ex); }
    }

    public void deleteAllProducts() {
        if (!saveAllProducts(new ArrayList<>()))
            throw new IllegalStateException("Cannot delete all products: failed writing " + PRODUCT_FILE_PATH);
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
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
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
            throw new IllegalStateException("Cannot read product data: " + PRODUCT_FILE_PATH, e);
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

    private boolean saveAllProducts(List<Product> products) {
        File file = new File(PRODUCT_FILE_PATH);
        if (file.getParentFile() != null && !file.getParentFile().exists()
                && !file.getParentFile().mkdirs()) return false;

        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, false), "UTF-8"))) {
            for (Product p : products) {
                bw.write(p.toString());
                bw.newLine();
            }
            return true;
        } catch (IOException e) {
            return false;
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
