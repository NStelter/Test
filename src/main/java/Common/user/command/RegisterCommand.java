package Common.user.command;

import Common.user.Session;
import Common.message.AbstractMessage;



public class RegisterCommand extends AbstractMessage {

    private static final long serialVersionUID = 793454958390539421L;
    String username;
    String password;

    public RegisterCommand(String username, String password) {
        super(Session.invalid);
        this.username = username;
        this.password = password;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPassword() {
        return password;
    }

}
