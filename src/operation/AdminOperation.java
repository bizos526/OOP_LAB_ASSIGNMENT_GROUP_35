
package operation;

import model.Admin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles administrator account initialization.
 * Implements the Singleton design pattern.
 */
public class AdminOperation {

    private static final AdminOperation INSTANCE =
            new AdminOperation();

    private static final Path USER_FILE =
            Path.of("data", "users.txt");

    private static final String ADMIN_USERNAME = "admin";

    private final UserOperation userOperation =
            UserOperation.getInstance();

    // Private constructor
    private AdminOperation() {
    }

    // Return the single instance
    public static AdminOperation getInstance() {
        return INSTANCE;
    }

    /**
     * Registers one administrator if none exists.
     * Existing administrator accounts are preserved.
     */
    public synchronized void registerAdmin() {

        try {
            Files.createDirectories(USER_FILE.getParent());

            List<String> lines = Files.exists(USER_FILE)
                    ? new ArrayList<>(
                        Files.readAllLines(
                            USER_FILE,
                            StandardCharsets.UTF_8))
                    : new ArrayList<>();

            // Do not create a duplicate administrator
            for (String line : lines) {
                if ("admin".equals(
                        getField(line, "user_role"))) {
                    return;
                }
            }

            // Avoid username collisions
            for (String line : lines) {
                if (ADMIN_USERNAME.equals(
                        getField(line, "user_name"))) {
                    throw new IllegalStateException(
                        "Username admin is already used.");
                }
            }

            // Read password from environment
            String password =
                    System.getenv("ECOM_ADMIN_PASSWORD");

            if (!userOperation.validatePassword(password)) {
                throw new IllegalStateException(
                    "ECOM_ADMIN_PASSWORD is missing or invalid.");
            }

            String time = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern(
                    "dd-MM-yyyy_HH:mm:ss"));

            Admin admin = new Admin(
                    userOperation.generateUniqueUserId(),
                    ADMIN_USERNAME,
                    userOperation.encryptPassword(password),
                    time,
                    "admin"
            );

            lines.add(admin.toString());

            // Write to a temporary file first
            Path temporary = Files.createTempFile(
                    USER_FILE.getParent(),
                    "users-admin-",
                    ".tmp");

            try {
                Files.write(
                        temporary,
                        lines,
                        StandardCharsets.UTF_8);

                Files.move(
                        temporary,
                        USER_FILE,
                        StandardCopyOption.REPLACE_EXISTING);

            } finally {
                Files.deleteIfExists(temporary);
            }

        } catch (IOException e) {
            throw new IllegalStateException(
                "Unable to initialize administrator account.",
                e);
        }
    }

    // Read a string field from a JSON-like record
    private String getField(String line, String key) {
        Pattern pattern = Pattern.compile(
            "\"" + Pattern.quote(key)
            + "\"\\s*:\\s*\"([^\"]*)\"");

        Matcher matcher = pattern.matcher(line);

        return matcher.find() ? matcher.group(1) : null;
    }
}
