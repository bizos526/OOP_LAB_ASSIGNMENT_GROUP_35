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

/** Entry point and application controller for the E-Commerce System. */
public class Main {
    private final IOInterface io = IOInterface.getInstance();
    private final UserOperation users = UserOperation.getInstance();
    private final CustomerOperation customers = CustomerOperation.getInstance();
    private final AdminOperation admins = AdminOperation.getInstance();
    private final ProductOperation products = ProductOperation.getInstance();
    private final OrderOperation orders = OrderOperation.getInstance();

    public static void main(String[] args) {
        new Main().run();
    }

    private void run() {
        try {
            admins.registerAdmin();
        } catch (IllegalStateException ex) {
            io.printErrorMessage("Admin setup: " + ex.getMessage());
            io.printMessage("Customer features can still be used. Set ECOM_ADMIN_PASSWORD if creating a new admin.");
        }

        // The product importer preserves existing products when no CSV is present.
        products.extractProductsFromFiles();

        while (true) {
            io.mainMenu();
            String choice = io.getUserInput("Choose an option: ");
            try {
                switch (choice) {
                    case "1": login(); break;
                    case "2": registerCustomer(); break;
                    case "3": browseProducts(); break;
                    case "0": io.printMessage("Goodbye!"); return;
                    default: io.printErrorMessage("Invalid option.");
                }
            } catch (RuntimeException ex) {
                io.printErrorMessage("Operation failed: " + ex.getMessage());
            }
        }
    }

    private void login() {
        String name = io.getUserInput("Username: ");
        String password = io.getUserInput("Password: ");
        User current = users.login(name, password);
        if (current == null) {
            io.printErrorMessage("Incorrect username or password.");
            return;
        }
        io.printMessage("Welcome, " + current.getUserName() + "!");
        if (current instanceof Admin) {
            adminSession();
        } else if (current instanceof Customer) {
            customerSession((Customer) current);
        } else {
            io.printErrorMessage("Unsupported user role.");
        }
    }

    private void registerCustomer() {
        String name = io.getUserInput("Username (letters/_ only, at least 5 chars): ");
        String password = io.getUserInput("Password (at least 5 chars, letters and digits): ");
        String email = io.getUserInput("Email: ");
        String mobile = io.getUserInput("Mobile (10 digits, starts 03 or 04): ");
        if (customers.registerCustomer(name, password, email, mobile)) {
            io.printMessage("Customer registered. You can log in now.");
        } else {
            io.printErrorMessage("Registration failed. Check the details or duplicate username.");
        }
    }

    private void adminSession() {
        while (true) {
            io.adminMenu();
            String choice = io.getUserInput("Choose an option: ");
            try {
                switch (choice) {
                    case "1": showCustomers(); break;
                    case "2": browseProducts(); break;
                    case "3": searchProducts(); break;
                    case "4": deleteProduct(); break;
                    case "5": productCharts(); break;
                    case "6": orderCharts(); break;
                    case "0": io.printMessage("Logged out."); return;
                    default: io.printErrorMessage("Invalid option.");
                }
            } catch (RuntimeException ex) {
                io.printErrorMessage("Operation failed: " + ex.getMessage());
            }
        }
    }

    private void customerSession(Customer current) {
        while (true) {
            io.customerMenu();
            String choice = io.getUserInput("Choose an option: ");
            try {
                switch (choice) {
                    case "1": browseProducts(); break;
                    case "2": searchProducts(); break;
                    case "3": createOrder(current); break;
                    case "4": showOrders(current); break;
                    case "5": deleteOwnOrder(current); break;
                    case "6": updateProfile(current); break;
                    case "0": io.printMessage("Logged out."); return;
                    default: io.printErrorMessage("Invalid option.");
                }
            } catch (RuntimeException ex) {
                io.printErrorMessage("Operation failed: " + ex.getMessage());
            }
        }
    }

    private void showCustomers() {
        int page = readPage();
        CustomerListResult result = customers.getCustomerList(page);

        java.util.List<String> safeCustomers = new java.util.ArrayList<>();
        for (Customer customer : result.getCustomers()) {
            safeCustomers.add(
                "ID: " + customer.getUserId()
                + " | Username: " + customer.getUserName()
                + " | Email: " + customer.getUserEmail()
                + " | Mobile: " + customer.getUserMobile()
            );
        }

        io.showList("CUSTOMERS", safeCustomers);
        io.printMessage("Page " + result.getCurrentPage() + " / " + result.getTotalPages());
    }

    private void browseProducts() {
        int page = readPage();
        ProductListResult result = products.getProductList(page);
        io.showList("PRODUCTS", result.getProducts());
        io.printMessage("Page " + result.getCurrentPage() + " / " + result.getTotalPages());
    }

    private int readPage() {
        String input = io.getUserInput("Page number (Enter for 1): ");
        if (input.isEmpty()) return 1;
        try {
            int page = Integer.parseInt(input);
            if (page > 0) return page;
        } catch (NumberFormatException ignored) {
            // Fall through to a friendly error.
        }
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
        if (product == null) {
            io.printErrorMessage("Product not found.");
            return;
        }
        io.printObject(product);
        if (!confirm("Delete this product?")) return;
        if (products.deleteProduct(id)) {
            io.printMessage("Product deleted. Existing orders referencing it should be reviewed.");
        } else {
            io.printErrorMessage("Could not delete product.");
        }
    }

    private void createOrder(Customer current) {
        String id = io.getUserInput("Product ID to order: ");
        Product product = products.getProductById(id);
        if (product == null) {
            io.printErrorMessage("Product not found.");
            return;
        }
        io.printObject(product);
        if (!confirm("Place order for this product?")) return;
        if (orders.createAnOrder(current.getUserId(), id, null)) {
            io.printMessage("Order created successfully.");
        } else {
            io.printErrorMessage("Unable to create order. Check customer/product data files.");
        }
    }

    private void showOrders(Customer current) {
        int page = readPage();
        OrderListResult result = orders.getOrderList(current.getUserId(), page);
        io.showList("MY ORDERS", result.getOrderList());
        io.printMessage("Page " + result.getCurrentPage() + " / " + result.getTotalPages());
    }

    private void deleteOwnOrder(Customer current) {
        String id = io.getUserInput("Order ID to delete: ");
        boolean owned = false;
        int page = 1;
        while (true) {
            OrderListResult result = orders.getOrderList(current.getUserId(), page);
            for (Order order : result.getOrderList()) {
                if (id.equals(order.getOrderId())) {
                    owned = true;
                    break;
                }
            }
            if (owned || page >= result.getTotalPages()) break;
            page++;
        }
        if (!owned) {
            io.printErrorMessage("Order not found in your account.");
            return;
        }
        if (!confirm("Delete your order?")) return;
        if (orders.deleteOrder(id)) {
            io.printMessage("Order deleted.");
        } else {
            io.printErrorMessage("Order deletion failed.");
        }
    }

    private void updateProfile(Customer current) {
        io.printMessage("Editable fields: user_name, user_password, user_email, user_mobile");
        String field = io.getUserInput("Field: ");
        String value = io.getUserInput("New value: ");
        if (customers.updateProfile(field, value, current)) {
            io.printMessage("Profile updated.");
        } else {
            io.printErrorMessage("Update failed. Check field, format and uniqueness.");
        }
    }

    private void productCharts() {
        products.generateCategoryFigure();
        products.generateDiscountFigure();
        products.generateLikesCountFigure();
        products.generateDiscountLikesCountFigure();
        io.printMessage("Product charts requested. Check data/figure for PNG outputs.");
    }

    private void orderCharts() {
        String customerId = io.getUserInput("Customer ID for single-customer chart (Enter to skip): ");
        if (!customerId.isEmpty()) {
            orders.generateSingleCustomerConsumptionFigure(customerId);
        }
        orders.generateAllCustomersConsumptionFigure();
        orders.generateAllTop10BestSellersFigure();
        io.printMessage("Order charts requested. Check data/figure for PNG outputs.");
    }

    private boolean confirm(String action) {
        String answer = io.getUserInput(action + " Type YES to confirm: ");
        return "YES".equals(answer);
    }
}
