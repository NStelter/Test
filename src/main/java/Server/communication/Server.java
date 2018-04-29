package Server.communication;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.security.auth.login.LoginException;

import com.google.common.eventbus.DeadEvent;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import Common.command.GenericCommand;
import Common.exception.ExceptionMessage;
import Common.exception.SecurityException;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import Common.message.AbstractMessage;
import Common.message.IMessage;
import Common.user.IUserService;
import Common.user.Session;
import Common.user.command.LoginCommand;
import Common.user.command.LogoutCommand;
import Common.user.command.RegisterCommand;
import Common.user.message.*;

public class Server implements ServerHandlerDelegate {
	/**
	 * Server port
	 */
	final private int port;

	/**
	 * User Service
	 */
	final private IUserService userService;

	/**
	 * Clients that are connected
	 */
	final private List<ChannelHandlerContext> connectedClients = new CopyOnWriteArrayList<>();

	/**
	 * Client with logged in sessions
	 */
	final private Map<ChannelHandlerContext, Session> activeSessions = new HashMap<>();

	/**
	 * For Applications reasons the eventBus as part of this class
	 */
	final private EventBus eventBus = new EventBus();

	/**
	 * Creates a new Server Object and start listening on given port
	 *
	 * @param port
	 *            The port the server should listen for new connection
	 * @param userService
	 *            The userService that should be used for the server
	 */
	public Server(int port, IUserService userService) {
		this.port = port;
		this.userService = userService;
		// TODO: Ping clients
		eventBus.register(this);
	}

	/**
	 * Initialize netty
	 *
	 * @throws Exception
	 */
	public void start() throws Exception {
		final ServerHandler serverHandler = new ServerHandler(this);
		EventLoopGroup bossGroup = new NioEventLoopGroup(1);
		EventLoopGroup workerGroup = new NioEventLoopGroup();
		try {
			ServerBootstrap b = new ServerBootstrap();
			b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
					.localAddress(new InetSocketAddress(port)).childHandler(new ChannelInitializer<SocketChannel>() {

						@Override
						protected void initChannel(SocketChannel ch) throws Exception {
							// Encoder and decoder are both needed! Send and
							// receive serializable objects
							ch.pipeline().addLast(new ObjectEncoder());
							ch.pipeline().addLast(new ObjectDecoder(ClassResolvers.cacheDisabled(null)));
							// must be last in the pipeline else they will not
							// get encoded/decoded objects but ByteBuf
							ch.pipeline().addLast(serverHandler);
						}

					});
			// Just wait for server shutdown
			ChannelFuture f = b.bind().sync();
			f.channel().closeFuture().sync();
		} finally {
			bossGroup.shutdownGracefully().sync();
			workerGroup.shutdownGracefully().sync();
		}
	}

	// Called from ServerHandler
	@Override
	public void process(ChannelHandlerContext ctx, IMessage msg) {

		try {
			// Bind msg to ctx
			msg.setInfo(ctx);
			eventBus.post(msg);

		} catch (Exception e) {
			System.err.println("ServerException " + e.getClass().getName() + " " + e.getMessage());
			sendToClient(ctx, new ExceptionMessage(e));
		}
	}

	@Subscribe
	private void handleEventBusError(DeadEvent deadEvent){
		System.err.println("DeadEvent detected "+deadEvent);
	}

	@Subscribe
	private void processLoginCommand(LoginCommand msg) {
		if (msg.getInfo() instanceof ChannelHandlerContext) {
			ChannelHandlerContext ctx = (ChannelHandlerContext) msg.getInfo();

			System.out.println("Got new login message with " + msg.getUsername() + " " + msg.getPassword());

			Session newSession = userService.login(msg.getUsername(), msg.getPassword());

			if (newSession.isValid()) {
				sendToClient(ctx, new LoginSuccessfullMessage(newSession, msg.getUsername()));
				putSession(ctx, newSession);

				// Send all clients information, that a new user is logged in
				sendToAll(new UserLoggedInMessage(msg.getUsername()));
			} else {
				sendToClient(ctx, new ExceptionMessage(new LoginException()));
			}
		}
	}

	@Subscribe
	private void processRegisterCommand(RegisterCommand msg){
		if (msg.getInfo() instanceof ChannelHandlerContext) {
			ChannelHandlerContext ctx = (ChannelHandlerContext) msg.getInfo();
			userService.register(msg.getUsername(), msg.getPassword());
			sendToClient(ctx, new RegisterSuccessfullMessage());
		}
	}

	@Subscribe
	private void processLogoutCommand(LogoutCommand msg) {
		if (msg.getInfo() instanceof ChannelHandlerContext) {
			ChannelHandlerContext ctx = (ChannelHandlerContext) msg.getInfo();
			System.out.println("Got new logout " + msg.getSession());
			checkLogin(ctx, msg);
			removeUser(ctx, msg.getSession());
		}
	}


	private void removeUser(ChannelHandlerContext ctx, Session session) {
		String user = userService.logout(session);
		if (user != null) {
			UserLoggedOutMessage loggedOutMessage = new UserLoggedOutMessage(user);
			sendToAll(loggedOutMessage);
		}
	}

	@Subscribe
	private void processGenericCommand(GenericCommand msg) {
		if (msg.getInfo() instanceof ChannelHandlerContext) {
			ChannelHandlerContext ctx = (ChannelHandlerContext) msg.getInfo();
			System.out.println("Got a command " + msg.getCommand() + " for session " + msg.getSession());
			checkLogin(ctx, msg);
			switch (msg.getCommand()) {
			case RETRIEVE_USERS_LIST:
				UsersListMessage user = new UsersListMessage(userService.retrieveAllUsers(msg.getSession()));
				sendToClient(ctx, user);
				break;
			default:
				System.err.println("Unknown command!");
			}
		}
	}

	private void checkLogin(ChannelHandlerContext ctx, AbstractMessage msg) {
		msg.forceSession();
		if (!msg.getSession().equals(getSession(ctx))) {
			throw new SecurityException("Login required for " + msg);
		}
	}

	// -------------------------------------------------------------------------------
	// Handling of connected clients
	// -------------------------------------------------------------------------------
	@Override
	public void newClientConnected(ChannelHandlerContext ctx) {
		System.err.println("New client " + ctx + " connected");
		connectedClients.add(ctx);
	}

	@Override
	public void clientDisconnected(ChannelHandlerContext ctx) {
		System.err.println("Client disconnected");
		removeUser(ctx, getSession(ctx));
		connectedClients.remove(ctx);
	}

	// -------------------------------------------------------------------------------
	// Session Management
	// -------------------------------------------------------------------------------
	private void putSession(ChannelHandlerContext ctx, Session newSession) {
		// TODO: check if session is already bound to connection
		activeSessions.put(ctx, newSession);
	}

	private Session getSession(ChannelHandlerContext ctx) {
		return activeSessions.get(ctx);
	}

	// -------------------------------------------------------------------------------
	// Help methods: Send only objects of type IMessage
	// -------------------------------------------------------------------------------

	private void sendToClient(ChannelHandlerContext ctx, IMessage message) {
		ctx.writeAndFlush(message);
	}

	private void sendToAll(IMessage msg) {
		for (ChannelHandlerContext client : connectedClients) {
			try {
				client.writeAndFlush(msg);
			} catch (Exception e) {
				// TODO: Handle exception for unreachable clients
				e.printStackTrace();
			}
		}
	}

}
