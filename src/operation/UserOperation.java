
package operation;

import model.User;
import model.Customer;
import model.Admin;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles user-related operations.
 * Implements the Singleton design pattern.
 */
public class UserOperation {

    private static final UserOperation INSTANCE =
            new UserOperation();

    private static final String USER_FILE =
            "data/users.txt";

    private final Random random = new Random();

    // Private constructor for Singleton
    private UserOperation() {
    }

    public static UserOperation getInstance() {
        return INSTANCE;
    }

    // Generate a unique ID: u_ followed by 10 digits
    public String generateUniqueUserId() {
        String id;

        do {
            StringBuilder builder = new StringBuilder("u_");

            for (int i = 0; i < 10; i++) {
                builder.append(random.nextInt(10));
            }

            id = builder.toString();
        } while (userIdExists(id));

        return id;
    }

    // Encrypt password using the assignment algorithm
    public String encryptPassword(String userPassword) {
        if (userPassword == null) {
            return null;
        }

        String chars =
                "abcdefghijklmnopqrstuvwxyz" +
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
                "0123456789";

        StringBuilder result = new StringBuilder("^^");

        for (int i = 0; i < userPassword.length(); i++) {

            for (int j = 0; j < 2; j++) {
                int index = random.nextInt(chars.length());
                result.append(chars.charAt(index));
            }

            result.append(userPassword.charAt(i));
        }

        result.append("$$");
        return result.toString();
    }

    // Reverse the assignment password transformation
    public String decryptPassword(String encryptedPassword) {
        if (encryptedPassword == null ||
            !encryptedPassword.startsWith("^^") ||
            !encryptedPassword.endsWith("$$")) {
            return null;
        }

        String content = encryptedPassword.substring(
                2, encryptedPassword.length() - 2);

        if (content.length() % 3 != 0) {
            return null;
        }

        StringBuilder password = new StringBuilder();

        for (int i = 2; i < content.length(); i += 3) {
            password.append(content.charAt(i));
        }

        return password.toString();
    }

    // Check username format
    public boolean validateUsername(String userName) {
        return userName != null &&
                userName.matches("[a-zA-Z_]{5,}");
    }

    // Check password format
    public boolean validatePassword(String userPassword) {
        return userPassword != null &&
                userPassword.length() >= 5 &&
                userPassword.matches("(?s).*[a-zA-Z].*") &&
                userPassword.matches("(?s).*\\d.*");
    }

    // Check whether a username already exists
    public boolean checkUsernameExist(String userName) {
        if (userName == null) {
            return false;
        }

        try (BufferedReader reader =
                new BufferedReader(new FileReader(USER_FILE))) {

            String line;

            while ((line = reader.readLine()) != null) {
                String storedName = getField(line, "user_name");

                if (userName.equals(storedName)) {
                    return true;
                }
            }

        } catch (IOException e) {
            return false;
        }

        return false;
    }

    // Authenticate a customer or admin
    public User login(String userName, String userPassword) {

        if (userName == null || userPassword == null) {
            return null;
        }

        try (BufferedReader reader =
                new BufferedReader(new FileReader(USER_FILE))) {

            String line;

            while ((line = reader.readLine()) != null) {

                String storedName = getField(line, "user_name");

                if (!userName.equals(storedName)) {
                    continue;
                }

                String encrypted =
                        getField(line, "user_password");

                String decrypted = decryptPassword(encrypted);

                if (!userPassword.equals(decrypted)) {
                    return null;
                }

                String id = getField(line, "user_id");
                String time =
                        getField(line, "user_register_time");
                String role = getField(line, "user_role");

                if ("admin".equals(role)) {
                    return new Admin(
                            id, storedName, encrypted,
                            time, role);
                }

                if ("customer".equals(role)) {
                    return new Customer(
                            id, storedName, encrypted,
                            time, role,
                            getField(line, "user_email"),
                            getField(line, "user_mobile"));
                }

                return null;
            }

        } catch (IOException e) {
            return null;
        }

        return null;
    }

    // Check ID uniqueness against the user file
    private boolean userIdExists(String id) {

        try (BufferedReader reader =
                new BufferedReader(new FileReader(USER_FILE))) {

            String line;

            while ((line = reader.readLine()) != null) {
                if (id.equals(getField(line, "user_id"))) {
                    return true;
                }
            }

        } catch (IOException e) {
            return false;
        }

        return false;
    }

    // Read a string value from one JSON-like record
    private String getField(String line, String key) {

        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(key) +
                "\"\\s*:\\s*\"([^\"]*)\"");

        Matcher matcher = pattern.matcher(line);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }
}
