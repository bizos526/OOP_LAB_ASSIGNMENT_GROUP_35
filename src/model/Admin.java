
package model;

/**
 * Represents an administrator account.
 */
public class Admin extends User {

    public Admin(String userId, String userName,
                 String userPassword, String userRegisterTime,
                 String userRole) {

        super(userId, userName, userPassword,
              userRegisterTime, userRole);
    }

    public Admin() {
        super("", "", "", "", "admin");
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
