package io;

import java.util.List;
import java.util.Scanner;

/** Centralizes all console input and output for the E-Commerce application. */
public final class IOInterface {
    private static final IOInterface INSTANCE = new IOInterface();
    private final Scanner scanner = new Scanner(System.in);

    private IOInterface() { }

    /** Returns the application's single console interface instance. */
    public static IOInterface getInstance() { return INSTANCE; }

    /** Reads one prompt response as a trimmed string. */
    public String getUserInput(String message) {
        System.out.print(message);
        return scanner.nextLine().trim();
    }

    /**
     * Reads whitespace-separated arguments, truncating extras and padding
     * missing values with empty strings as required by the assignment.
     */
    public String[] getUserInput(String message, int numOfArgs) {
        if (numOfArgs < 0) throw new IllegalArgumentException("numOfArgs must not be negative");
        String[] result = new String[numOfArgs];
        String input = getUserInput(message);
        String[] parts = input.isEmpty() ? new String[0] : input.split("\\s+");
        for (int i = 0; i < numOfArgs; i++) result[i] = i < parts.length ? parts[i] : "";
        return result;
    }

    /** Displays the application entry menu. */
    public void mainMenu() {
        System.out.println("\n===== E-COMMERCE SYSTEM =====");
        System.out.println("1. Login");
        System.out.println("2. Register Customer");
        System.out.println("3. Browse Products");
        System.out.println("0. Exit");
    }

    /** Displays every required administrator task plus existing conveniences. */
    public void adminMenu() {
        System.out.println("\n===== ADMIN MENU =====");
        System.out.println("1. View Customers");
        System.out.println("2. View Products");
        System.out.println("3. Search Products");
        System.out.println("4. Delete Product");
        System.out.println("5. Generate Product Charts");
        System.out.println("6. Generate Order Charts");
        System.out.println("7. Add Customer");
        System.out.println("8. Show Orders");
        System.out.println("9. Generate Test Order Data");
        System.out.println("10. Delete All Data");
        System.out.println("0. Logout");
    }

    /** Displays customer tasks, retaining previously tested option numbers. */
    public void customerMenu() {
        System.out.println("\n===== CUSTOMER MENU =====");
        System.out.println("1. Browse Products");
        System.out.println("2. Search Products");
        System.out.println("3. Create Order");
        System.out.println("4. View My Orders");
        System.out.println("5. Delete My Order");
        System.out.println("6. Update My Profile");
        System.out.println("7. Show My Profile");
        System.out.println("8. Generate My Consumption Figure");
        System.out.println("0. Logout");
    }

    /** Prints a titled collection, omitting pagination details. */
    public void showList(String title, Iterable<?> items) {
        System.out.println("\n===== " + title + " =====");
        boolean empty = true;
        for (Object item : items) {
            System.out.println(item);
            empty = false;
        }
        if (empty) System.out.println("No records found.");
    }

    /** Prints a numbered, paginated list as requested by the original PDF. */
    public void showList(String userRole, String listType, List<?> objectList,
                         int pageNumber, int totalPages) {
        System.out.println("\n===== " + listType + " (" + userRole + ") =====");
        if (objectList == null || objectList.isEmpty()) System.out.println("No records found.");
        else {
            int pageSize = 10;
            int start = Math.max(0, (pageNumber - 1) * pageSize);
            for (int i = 0; i < objectList.size(); i++) {
                System.out.println((start + i + 1) + ". " + objectList.get(i));
            }
        }
        System.out.println("Page " + pageNumber + " / " + totalPages);
    }

    /** Prints an error with source and message. */
    public void printErrorMessage(String errorSource, String errorMessage) {
        System.out.println("[ERROR] " + errorSource + ": " + errorMessage);
    }

    /** Backward-compatible single-message error display. */
    public void printErrorMessage(String message) { System.out.println("[ERROR] " + message); }

    /** Prints an information message. */
    public void printMessage(String message) { System.out.println("[INFO] " + message); }

    /** Prints the supplied object's string representation. */
    public void printObject(Object object) { System.out.println(object); }
}
