
package model;

/**
 * Represents the common information of system users.
 * This is the abstract base class for Customer and Admin.
 */
public abstract class User {

    private String userId;
    private String userName;
    private String userPassword;
    private String userRegisterTime;
    private String userRole;

    // Parameterized constructor
    public User(String userId, String userName,
                String userPassword,
                String userRegisterTime,
                String userRole) {

        this.userId = userId;
        this.userName = userName;
        this.userPassword = userPassword;
        this.userRegisterTime = userRegisterTime;
        this.userRole = userRole;
    }

    // Default constructor
    public User() {
        this("", "", "", "", "customer");
    }

    // Getters
    public String getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public String getUserPassword() {
        return userPassword;
    }

    public String getUserRegisterTime() {
        return userRegisterTime;
    }

    public String getUserRole() {
        return userRole;
    }

    // Setters
    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void setUserPassword(String userPassword) {
        this.userPassword = userPassword;
    }

    public void setUserRegisterTime(String userRegisterTime) {
        this.userRegisterTime = userRegisterTime;
    }

    @Override
    public String toString() {
        return "{\"user_id\":\"" + userId
                + "\",\"user_name\":\"" + userName
                + "\",\"user_password\":\"" + userPassword
                + "\",\"user_register_time\":\"" + userRegisterTime
                + "\",\"user_role\":\"" + userRole
                + "\"}";
    }
}
