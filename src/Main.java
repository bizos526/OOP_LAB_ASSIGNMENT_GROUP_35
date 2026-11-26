import io.IOInterface;
import model.Admin;
import model.Customer;
import model.Order;
import model.Product;
import model.User;
import operation.AdminOperation;
import operation.CustomerListResult;
import operation.CustomerOperation;
import operation.OrderListResult;
import operation.OrderOperation;
import operation.ProductListResult;
import operation.ProductOperation;
import operation.UserOperation;
import java.util.ArrayList;
import java.util.List;

/** Coordinates customer, administrator, product and order operations. */
public final class Main {
    private final IOInterface io = IOInterface.getInstance();
    private final UserOperation users = UserOperation.getInstance();
    private final CustomerOperation customers = CustomerOperation.getInstance();
    private final AdminOperation admins = AdminOperation.getInstance();
    private final ProductOperation products = ProductOperation.getInstance();
    private final OrderOperation orders = OrderOperation.getInstance();

    /** Application entry point used by the lecturer. */
    public static void main(String[] args) { new Main().run(); }

    private void run() {
        try {
            admins.registerAdmin();
        } catch (IllegalStateException ex) {
            io.printErrorMessage("Admin setup", ex.getMessage());
            io.printMessage("Existing accounts remain available. Set ECOM_ADMIN_PASSWORD only for initial admin creation.");
        }
        try { products.extractProductsFromFiles(); }
        catch (RuntimeException ex) { io.printErrorMessage("Product import", ex.getMessage()); }
        while (true) {
            io.mainMenu();
            String choice = io.getUserInput("Choose an option: ");
            try {
                switch (choice) {
                    case "1": login(); break;
                    case "2": registerCustomer(); break;
                    case "3": browseProducts("guest"); break;
                    case "0": io.printMessage("Goodbye!"); return;
                    default: io.printErrorMessage("Invalid option.");
                }
            } catch (RuntimeException ex) { io.printErrorMessage("Operation", ex.getMessage()); }
        }
    }

    private void login() {
        String name = io.getUserInput("Username: ");
        String password = io.getUserInput("Password: ");
        User current = users.login(name, password);
        if (current == null) { io.printErrorMessage("Incorrect username or password."); return; }
        io.printMessage("Welcome, " + current.getUserName() + "!");
        if (current instanceof Admin) adminSession();
        else if (current instanceof Customer) customerSession((Customer) current);
        else io.printErrorMessage("Unsupported user role.");
    }

    private void registerCustomer() {
        String name = io.getUserInput("Username (letters/_ only, at least 5 chars): ");
        String password = io.getUserInput("Password (at least 5 chars, letters and digits): ");
        String email = io.getUserInput("Email: ");
        String mobile = io.getUserInput("Mobile (10 digits, starts 03 or 04): ");
        if (customers.registerCustomer(name, password, email, mobile)) io.printMessage("Customer registered. You can log in now.");
        else io.printErrorMessage("Registration failed. Check validation and duplicate username.");
    }

    private void adminSession() {
        while (true) {
            io.adminMenu();
            String choice = io.getUserInput("Choose an option: ");
            try {
                switch (choice) {
                    case "1": showCustomers(); break;
                    case "2": browseProducts("admin"); break;
                    case "3": searchProducts(); break;
                    case "4": deleteProduct(); break;
                    case "5": productCharts(); break;
                    case "6": orderCharts(); break;
                    case "7": registerCustomer(); break;
                    case "8": showAdminOrders(); break;
                    case "9": generateTestData(); break;
                    case "10": deleteAllData(); break;
                    case "0": io.printMessage("Logged out."); return;
                    default: io.printErrorMessage("Invalid option.");
                }
            } catch (RuntimeException ex) { io.printErrorMessage("Admin operation", ex.getMessage()); }
        }
    }

    private void customerSession(Customer current) {
        while (true) {
            io.customerMenu();
            String choice = io.getUserInput("Choose an option: ");
            try {
                switch (choice) {
                    case "1": browseProducts("customer"); break;
                    case "2": searchProducts(); break;
                    case "3": createOrder(current); break;
                    case "4": showOrders(current); break;
                    case "5": deleteOwnOrder(current); break;
                    case "6": updateProfile(current); break;
                    case "7": showProfile(current); break;
                    case "8": customerCharts(current); break;
                    case "0": io.printMessage("Logged out."); return;
                    default: io.printErrorMessage("Invalid option.");
                }
            } catch (RuntimeException ex) { io.printErrorMessage("Customer operation", ex.getMessage()); }
        }
    }

    private String safeCustomer(Customer c) {
        return "ID: " + c.getUserId() + " | Username: " + c.getUserName()
            + " | Email: " + c.getUserEmail() + " | Mobile: " + c.getUserMobile();
    }

    private void showProfile(Customer current) {
        io.printMessage("MY PROFILE");
        io.printMessage(safeCustomer(current));
    }

    private void showCustomers() {
        int page = readPage();
        CustomerListResult result = customers.getCustomerList(page);
        List<String> safe = new ArrayList<>();
        for (Customer c : result.getCustomers()) safe.add(safeCustomer(c));
        io.showList("admin", "CUSTOMERS", safe, result.getCurrentPage(), result.getTotalPages());
    }

    private void browseProducts(String role) {
        int page = readPage();
        ProductListResult result = products.getProductList(page);
        io.showList(role, "PRODUCTS", result.getProducts(), result.getCurrentPage(), result.getTotalPages());
    }

    private int readPage() {
        String input = io.getUserInput("Page number (Enter for 1): ");
        if (input.isEmpty()) return 1;
        try { int value = Integer.parseInt(input); if (value > 0) return value; }
        catch (NumberFormatException ignored) { }
        io.printErrorMessage("Invalid page number. Using page 1.");
        return 1;
    }

    private void searchProducts() {
        String keyword = io.getUserInput("Search product name: ");
        io.showList("SEARCH RESULTS", products.getProductListByKeyword(keyword));
    }

    private void deleteProduct() {
        String id = io.getUserInput("Product ID to delete: ");
        Product product = products.getProductById(id);
        if (product == null) { io.printErrorMessage("Product not found."); return; }
        io.printObject(product);
        if (!confirm("Delete this product?")) return;
        if (products.deleteProduct(id)) io.printMessage("Product deleted. Existing orders referencing it should be reviewed.");
        else io.printErrorMessage("Could not delete product.");
    }

    private void createOrder(Customer current) {
        String id = io.getUserInput("Product ID to order: ");
        Product product = products.getProductById(id);
        if (product == null) { io.printErrorMessage("Product not found."); return; }
        io.printObject(product);
        if (!confirm("Place order for this product?")) return;
        if (orders.createAnOrder(current.getUserId(), id, null)) io.printMessage("Order created successfully.");
        else io.printErrorMessage("Unable to create order. Check customer and product files.");
    }

    private void showOrders(Customer current) {
        int page = readPage();
        OrderListResult result = orders.getOrderList(current.getUserId(), page);
        io.showList("customer", "MY ORDERS", result.getOrderList(), result.getCurrentPage(), result.getTotalPages());
    }

    /** Admin lists one customer's orders, or all current customers' orders. */
    private void showAdminOrders() {
        String id = io.getUserInput("Customer ID (Enter for all customers): ");
        if (!id.isEmpty()) {
            int page = readPage();
            OrderListResult result = orders.getOrderList(id, page);
            io.showList("admin", "ORDERS - " + id, result.getOrderList(), result.getCurrentPage(), result.getTotalPages());
            return;
        }
        List<Order> all = new ArrayList<>();
        for (int p = 1; ; p++) {
            CustomerListResult customersPage = customers.getCustomerList(p);
            if (p > customersPage.getTotalPages()) break;
            for (Customer c : customersPage.getCustomers()) {
                for (int op = 1; ; op++) {
                    OrderListResult page = orders.getOrderList(c.getUserId(), op);
                    if (op > page.getTotalPages()) break;
                    all.addAll(page.getOrderList());
                }
            }
        }
        // Orders belonging to deleted/missing customers are not accessible via the current OrderOperation API.
        io.showList("ALL CURRENT CUSTOMER ORDERS", all);
        io.printMessage("Orders linked to missing customer records are not included.");
    }

    private void deleteOwnOrder(Customer current) {
        String id = io.getUserInput("Order ID to delete: ");
        boolean owned = false;
        for (int p = 1; ; p++) {
            OrderListResult page = orders.getOrderList(current.getUserId(), p);
            if (p > page.getTotalPages()) break;
            for (Order order : page.getOrderList()) if (id.equals(order.getOrderId())) owned = true;
            if (owned) break;
        }
        if (!owned) { io.printErrorMessage("Order not found in your account."); return; }
        if (!confirm("Delete your order?")) return;
        if (orders.deleteOrder(id)) io.printMessage("Order deleted.");
        else io.printErrorMessage("Order deletion failed.");
    }

    private void updateProfile(Customer current) {
        io.printMessage("Editable fields: user_name, user_password, user_email, user_mobile");
        String field = io.getUserInput("Field: ");
        String value = io.getUserInput("New value: ");
        if (customers.updateProfile(field, value, current)) io.printMessage("Profile updated.");
        else io.printErrorMessage("Update failed. Check field, format and uniqueness.");
    }

    private void productCharts() {
        products.generateCategoryFigure();
        products.generateDiscountFigure();
        products.generateLikesCountFigure();
        products.generateDiscountLikesCountFigure();
        io.printMessage("Product charts requested. Check data/figure for PNG files.");
    }

    private void orderCharts() {
        String id = io.getUserInput("Customer ID for single-customer chart (Enter to skip): ");
        if (!id.isEmpty()) orders.generateSingleCustomerConsumptionFigure(id);
        orders.generateAllCustomersConsumptionFigure();
        orders.generateAllTop10BestSellersFigure();
        io.printMessage("Order charts requested. Check data/figure for PNG files.");
    }

    private void customerCharts(Customer current) {
        orders.generateSingleCustomerConsumptionFigure(current.getUserId());
        io.printMessage("Personal consumption chart requested. Check data/figure.");
    }

    private void generateTestData() {
        io.printMessage("This generates 10 new customers and 50-200 orders per customer.");
        io.printMessage("Run only on a backup/copy of the data folder.");
        if (!confirm("Generate large test data?")) return;
        orders.generateTestOrderData();
        io.printMessage("Test data generation completed. Review data/users.txt and data/orders.txt.");
    }

    private void deleteAllData() {
        io.printMessage("DANGER: Clears ALL customers, products and orders; preserves admin records.");
        io.printMessage("Back up data/users.txt, products.txt and orders.txt first.");
        if (!confirm("Delete ALL DATA?")) return;
        String phrase = io.getUserInput("Type DELETE ALL DATA to proceed: ");
        if (!"DELETE ALL DATA".equals(phrase)) { io.printMessage("Deletion cancelled."); return; }
        orders.deleteAllOrders();
        products.deleteAllProducts();
        customers.deleteAllCustomers();
        io.printMessage("Customer, product and order data deletion requested; check files to verify.");
    }

    private boolean confirm(String action) {
        return "YES".equals(io.getUserInput(action + " Type YES to confirm: "));
    }
}
