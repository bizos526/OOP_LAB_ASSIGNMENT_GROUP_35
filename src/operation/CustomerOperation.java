
package operation;

import model.Customer;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages customer registration, profiles and records.
 * Uses the Singleton design pattern.
 */
public class CustomerOperation {

    private static final CustomerOperation INSTANCE =
            new CustomerOperation();

    private static final String USER_FILE = "data/users.txt";

    private final UserOperation userOp =
            UserOperation.getInstance();

    private CustomerOperation() {
    }

    public static CustomerOperation getInstance() {
        return INSTANCE;
    }

    // 2.7.2 - Validate email
    public boolean validateEmail(String email) {
        return email != null &&
                email.matches(
                    "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    // 2.7.3 - Validate mobile
    public boolean validateMobile(String mobile) {
        return mobile != null &&
                mobile.matches("^(03|04)[0-9]{8}$");
    }

    // 2.7.4 - Register customer
    public synchronized boolean registerCustomer(
            String userName,
            String password,
            String email,
            String mobile) {

        if (!userOp.validateUsername(userName) ||
            !userOp.validatePassword(password) ||
            !validateEmail(email) ||
            !validateMobile(mobile)) {
            return false;
        }

        try {
            List<String> lines = readLines();

            for (String line : lines) {
                if (userName.equals(field(line, "user_name"))) {
                    return false;
                }
            }

            String time = LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern(
                        "dd-MM-yyyy_HH:mm:ss"));

            Customer customer = new Customer(
                    userOp.generateUniqueUserId(),
                    userName,
                    userOp.encryptPassword(password),
                    time,
                    "customer",
                    email,
                    mobile
            );

            lines.add(serialize(customer));
            return writeLines(lines);

        } catch (IOException e) {
            return false;
        }
    }

    // 2.7.5 - Update profile
    public synchronized boolean updateProfile(
            String attributeName,
            String value,
            Customer customerObject) {

        if (customerObject == null ||
            attributeName == null ||
            value == null) {
            return false;
        }

        try {
            List<String> lines = readLines();

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);

                if (!customerObject.getUserId().equals(
                        field(line, "user_id")) ||
                    !"customer".equals(
                        field(line, "user_role"))) {
                    continue;
                }

                Customer updated = parseCustomer(line);

                if (updated == null) {
                    return false;
                }

                switch (attributeName) {
                    case "user_name":
                        if (!userOp.validateUsername(value)) {
                            return false;
                        }

                        for (String other : lines) {
                            if (!customerObject.getUserId().equals(
                                    field(other, "user_id")) &&
                                value.equals(
                                    field(other, "user_name"))) {
                                return false;
                            }
                        }

                        updated.setUserName(value);
                        break;

                    case "user_password":
                        if (!userOp.validatePassword(value)) {
                            return false;
                        }

                        updated.setUserPassword(
                                userOp.encryptPassword(value));
                        break;

                    case "user_email":
                        if (!validateEmail(value)) {
                            return false;
                        }

                        updated.setUserEmail(value);
                        break;

                    case "user_mobile":
                        if (!validateMobile(value)) {
                            return false;
                        }

                        updated.setUserMobile(value);
                        break;

                    default:
                        return false;
                }

                lines.set(i, serialize(updated));

                if (!writeLines(lines)) {
                    return false;
                }

                customerObject.setUserName(
                        updated.getUserName());
                customerObject.setUserPassword(
                        updated.getUserPassword());
                customerObject.setUserEmail(
                        updated.getUserEmail());
                customerObject.setUserMobile(
                        updated.getUserMobile());

                return true;
            }

        } catch (IOException e) {
            return false;
        }

        return false;
    }

    // 2.7.6 - Delete one customer
    public synchronized boolean deleteCustomer(String customerId) {

        if (customerId == null) {
            return false;
        }

        try {
            List<String> lines = readLines();
            boolean removed = lines.removeIf(
                line -> customerId.equals(
                            field(line, "user_id")) &&
                        "customer".equals(
                            field(line, "user_role"))
            );

            return removed && writeLines(lines);

        } catch (IOException e) {
            return false;
        }
    }

    // 2.7.7 - Get paginated customers
    public CustomerListResult getCustomerList(int pageNumber) {

        List<Customer> customers = new ArrayList<>();

        try {
            for (String line : readLines()) {
                if ("customer".equals(
                        field(line, "user_role"))) {

                    Customer customer = parseCustomer(line);

                    if (customer != null) {
                        customers.add(customer);
                    }
                }
            }
        } catch (IOException e) {
            return new CustomerListResult(
                    new ArrayList<>(), pageNumber, 0);
        }

        int totalPages =
                (customers.size() + 9) / 10;

        if (pageNumber < 1 || pageNumber > totalPages) {
            return new CustomerListResult(
                    new ArrayList<>(),
                    pageNumber,
                    totalPages);
        }

        int start = (pageNumber - 1) * 10;
        int end = Math.min(start + 10, customers.size());

        return new CustomerListResult(
                new ArrayList<>(
                        customers.subList(start, end)),
                pageNumber,
                totalPages
        );
    }

    // 2.7.8 - Delete all customers; preserve admins
    public synchronized void deleteAllCustomers() {

        try {
            List<String> lines = readLines();

            lines.removeIf(line ->
                    "customer".equals(
                        field(line, "user_role")));

            if (!writeLines(lines)) {
                throw new IOException(
                        "Unable to save users.txt");
            }

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Read UTF-8 lines
    private List<String> readLines() throws IOException {
        List<String> lines = new ArrayList<>();
        File file = new File(USER_FILE);

        if (!file.exists()) {
            return lines;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                    new FileInputStream(file), "UTF-8"))) {

            String line;

            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
        }

        return lines;
    }

    // Write via a temporary file to protect existing data
    private boolean writeLines(List<String> lines) {

        File target = new File(USER_FILE);
        File folder = target.getParentFile();

        if (!folder.exists() && !folder.mkdirs()) {
            return false;
        }

        File temp = null;

        try {
            temp = File.createTempFile(
                    "users-", ".tmp", folder);

            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(
                        new FileOutputStream(temp), "UTF-8"))) {

                for (String line : lines) {
                    writer.write(line);
                    writer.newLine();
                }
            }

            File backup = new File(folder, "users.txt.bak");

            if (backup.exists()) {
                return false;
            }

            boolean hadOriginal = target.exists();

            if (hadOriginal && !target.renameTo(backup)) {
                return false;
            }

            if (!temp.renameTo(target)) {
                if (hadOriginal && !backup.renameTo(target)) {
                    throw new IOException(
                        "Restore users.txt from users.txt.bak");
                }
                return false;
            }

            if (hadOriginal && !backup.delete()) {
                // Save succeeded; old backup remains.
            }

            return true;

        } catch (IOException e) {
            return false;

        } finally {
            if (temp != null && temp.exists()) {
                temp.delete();
            }
        }
    }

    // Read a simple JSON-like string field
    private String field(String line, String key) {
        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(key) +
                "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

        Matcher matcher = pattern.matcher(line);

        if (!matcher.find()) {
            return null;
        }

        return unescape(matcher.group(1));
    }

    private String unescape(String text) {
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(++i);

                switch (next) {
                    case 'n': result.append('\n'); break;
                    case 'r': result.append('\r'); break;
                    case 't': result.append('\t'); break;
                    default: result.append(next); break;
                }
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    private String json(String value) {
        return "\"" + escape(value) + "\"";
    }

    // Convert a stored record into a Customer
    private Customer parseCustomer(String line) {
        if (!"customer".equals(field(line, "user_role"))) {
            return null;
        }

        return new Customer(
                field(line, "user_id"),
                field(line, "user_name"),
                field(line, "user_password"),
                field(line, "user_register_time"),
                "customer",
                field(line, "user_email"),
                field(line, "user_mobile")
        );
    }

    // Serialize a Customer as one JSON-like line
    private String serialize(Customer c) {
        return "{"
            + "\"user_id\":" + json(c.getUserId()) + ","
            + "\"user_name\":" + json(c.getUserName()) + ","
            + "\"user_password\":" + json(c.getUserPassword()) + ","
            + "\"user_register_time\":" + json(c.getUserRegisterTime()) + ","
            + "\"user_role\":\"customer\","
            + "\"user_email\":" + json(c.getUserEmail()) + ","
            + "\"user_mobile\":" + json(c.getUserMobile())
            + "}";
    }
}
