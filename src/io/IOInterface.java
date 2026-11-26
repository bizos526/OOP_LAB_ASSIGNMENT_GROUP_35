
package io;

import java.util.Scanner;

public class IOInterface {
    private static final IOInterface INSTANCE = new IOInterface();
    private final Scanner scanner;

    private IOInterface() {
        scanner = new Scanner(System.in);
    }

    public static IOInterface getInstance() {
        return INSTANCE;
    }

    public String getUserInput(String message) {
        System.out.print(message);
        return scanner.nextLine().trim();
    }

    public void mainMenu() {
        System.out.println("\n===== E-COMMERCE SYSTEM =====");
        System.out.println("1. Login");
        System.out.println("2. Register Customer");
        System.out.println("3. Browse Products");
        System.out.println("0. Exit");
    }

    public void adminMenu() {
        System.out.println("\n===== ADMIN MENU =====");
        System.out.println("1. View Customers");
        System.out.println("2. View Products");
        System.out.println("3. Search Products");
        System.out.println("4. Delete Product");
        System.out.println("5. Generate Product Charts");
        System.out.println("6. Generate Order Charts");
        System.out.println("0. Logout");
    }

    public void customerMenu() {
        System.out.println("\n===== CUSTOMER MENU =====");
        System.out.println("1. Browse Products");
        System.out.println("2. Search Products");
        System.out.println("3. Create Order");
        System.out.println("4. View My Orders");
        System.out.println("5. Delete My Order");
        System.out.println("6. Update My Profile");
        System.out.println("0. Logout");
    }

    public void showList(String title, Iterable<?> items) {
        System.out.println("\n===== " + title + " =====");

        boolean empty = true;
        for (Object item : items) {
            System.out.println(item);
            empty = false;
        }

        if (empty) {
            System.out.println("No records found.");
        }
    }

    public void printErrorMessage(String message) {
        System.out.println("[ERROR] " + message);
    }

    public void printMessage(String message) {
        System.out.println("[INFO] " + message);
    }

    public void printObject(Object object) {
        System.out.println(object);
    }
}
