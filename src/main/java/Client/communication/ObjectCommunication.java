package Client.communication;

import java.io.Serializable;
import java.util.List;

import Common.command.GenericCommand;
import Common.command.GenericCommands;
import io.netty.channel.Channel;
import Common.user.IUserService;
import Common.user.Session;
import Common.user.command.LoginCommand;
import Common.user.command.LogoutCommand;
import Common.user.command.RegisterCommand;

/**
 * This class is used to hide the communication details
 * implements IClientUserService
 *
 * @author Marco Grawunder
 *
 */

public class ObjectCommunication implements IUserService {


	/**
	 * The physical connection to the client
	 */
	Channel client;

	/**
	 * Creates a new Communication object with the connection information
	 * @param client
	 */
	public ObjectCommunication(Channel client) {
		this.client = client;
	}

	@Override
	public Session login(String username, String password){
		LoginCommand msg = new LoginCommand(username, password);
		sendMessage(msg);
		return null; // asynch call
	}

	@Override
	public void register(String username, String password){
		RegisterCommand msg = new RegisterCommand(username, password);
		sendMessage(msg);
	}

	@Override
	public String logout(Session session){
		LogoutCommand msg = new LogoutCommand(session);
		sendMessage(msg);
		return null;
	}


	@Override
	public List<String> retrieveAllUsers(Session session) {
		GenericCommand cmd = new GenericCommand(GenericCommands.RETRIEVE_USERS_LIST, session);
		sendMessage(cmd);
		return null; // asynch call
	}

	private void sendMessage(Serializable msg) {
		client.writeAndFlush(msg);
	}

}
