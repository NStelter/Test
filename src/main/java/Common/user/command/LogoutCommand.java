package Common.user.command;

import Common.message.AbstractMessage;
import Common.user.Session;

/**
 * A command send from client to server to log out
 * 
 * @author Marco Grawunder
 *
 */

public class LogoutCommand extends AbstractMessage {
	
	private static final long serialVersionUID = -5912075449879112061L;

	public LogoutCommand(Session session) {
		super(session);
	}

}
