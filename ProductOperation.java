package operation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import model.Product;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

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
        Map<String, Product> productMap = new LinkedHashMap<>();

        for (File file : csvFiles) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String headerLine = br.readLine();
                if (headerLine == null) {
                    continue;
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
                        p -> p.getProCategory() == null ? "Unknown" : p.getProCategory(),
                        Collectors.counting()
                ));

        List<String> categories = new ArrayList<>(categoryCounts.keySet());
        categories.sort((a, b) -> Long.compare(categoryCounts.get(b), categoryCounts.get(a)));

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (String category : categories) {
            dataset.addValue(categoryCounts.get(category), "Products", category);
        }

        JFreeChart barChart = ChartFactory.createBarChart(
                "Total Products by Category",
                "Category",
                "Number of Products",
                dataset,
                PlotOrientation.VERTICAL,
                false,
                true,
                false
        );

        saveChart(barChart, "category_figure.png");
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

        DefaultPieDataset<String> dataset = new DefaultPieDataset<>();
        dataset.setValue("< 30", lessThan30);
        dataset.setValue("30 - 60", between30And60);
        dataset.setValue("> 60", greaterThan60);

        JFreeChart pieChart = ChartFactory.createPieChart(
                "Product Discount Distribution",
                dataset,
                true,
                true,
                false
        );

        saveChart(pieChart, "discount_figure.png");
    }

    public void generateLikesCountFigure() {
        List<Product> products = loadAllProducts();
        Map<String, Long> categoryLikes = products.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getProCategory() == null ? "Unknown" : p.getProCategory(),
                        Collectors.summingLong(Product::getProLikesCount)
                ));

        List<String> categories = new ArrayList<>(categoryLikes.keySet());
        categories.sort((a, b) -> Long.compare(categoryLikes.get(a), categoryLikes.get(b)));

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (String category : categories) {
            dataset.addValue(categoryLikes.get(category), "Likes Count", category);
        }

        JFreeChart barChart = ChartFactory.createBarChart(
                "Total Likes Count by Category",
                "Category",
                "Total Likes Count",
                dataset,
                PlotOrientation.VERTICAL,
                false,
                true,
                false
        );

        saveChart(barChart, "likes_count_figure.png");
    }

    public void generateDiscountLikesCountFigure() {
        List<Product> products = loadAllProducts();
        XYSeries series = new XYSeries("Products");

        for (Product product : products) {
            series.add(product.getProDiscount(), product.getProLikesCount());
        }

        XYSeriesCollection dataset = new XYSeriesCollection(series);
        JFreeChart scatterChart = ChartFactory.createScatterPlot(
                "Relationship Between Discount and Likes Count",
                "Discount",
                "Likes Count",
                dataset,
                PlotOrientation.VERTICAL,
                false,
                true,
                false
        );

        saveChart(scatterChart, "discount_likes_count_figure.png");
    }

    public void deleteAllProducts() {
        File file = new File(PRODUCT_FILE_PATH);
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file, false))) {
            bw.write("");
        } catch (IOException e) {
            e.printStackTrace();
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
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
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
            if (c == '"' && (i == 0 || body.charAt(i - 1) != '\\')) {
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
                if (c == '"' && (i == 0 || pair.charAt(i - 1) != '\\')) {
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

    private String unquote(String text) {
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length() - 1)
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

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file, false))) {
            for (Product p : products) {
                bw.write(p.toString());
                bw.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveChart(JFreeChart chart, String fileName) {
        File folder = new File(FIGURE_FOLDER);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        File outputFile = new File(folder, fileName);
        try {
            ChartUtils.saveChartAsPNG(outputFile, chart, 800, 600);
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
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value.trim());
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