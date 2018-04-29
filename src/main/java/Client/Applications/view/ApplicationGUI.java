package Client.Applications.view;

import java.util.List;

import javax.security.auth.login.LoginException;

import Common.user.message.*;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import Client.communication.Client;
import Client.Applications.IConnectionListener;
import io.netty.channel.Channel;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import Common.user.IUserService;
import Common.user.Session;
import Client.user.UserServiceFactory;

public class ApplicationGUI extends Application implements IConnectionListener {

	private String host;
	private int port;

	IUserService userService;
	Session userSession = Session.invalid;

	private Stage primaryStage;
	private Scene loginScene;
	private Scene lobbyScene;
	private Scene registerScene;
	private ObservableList<String> users;
	private String username;
	private boolean checker = false;
	boolean registerFailBoolean = false;

	Client clientConnection;

	final EventBus eventBus = new EventBus();

	// -----------------------------------------------------
	// Java FX Methods
	// ----------------------------------------------------

	@Override
	public void init() throws Exception {
		Parameters p = getParameters();
		List<String> args = p.getRaw();

		if (args.size() != 2) {
			host = "localhost";
			port = 8889;
			System.err.println("Usage: " + Client.class.getSimpleName() + " host port");
			System.err.println("Using default port " + port + " on " + host);
		} else {
			host = args.get(0);
			port = Integer.parseInt(args.get(1));
		}

		// do not establish connection here
		// if connection is established in this stage, no GUI is shown and
		// exceptions are only visible in console!
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		this.primaryStage = primaryStage;
		primaryStage.setTitle("SWP Demo Application");
		clientConnection = new Client(host, port, eventBus);
		clientConnection.addConnectionListener(this);
		// Register this class for events (e.g. for exceptions)
		eventBus.register(this);
		// JavaFX Thread should not be blocked to long!
		Thread t = new Thread() {
			public void run() {
				try {
					clientConnection.start();
				} catch (Exception e) {
					exceptionOccured(e);
				}
			};
		};
		t.setDaemon(true);
		t.start();
	}

	@Override
	public void stop() throws Exception {
		if (userService != null && userSession != null && userSession.isValid()) {
			Session session = userSession;
			userSession = Session.invalid;
			userService.logout(session);
		}
		// Important: Close connection so connection thread can terminate
		// else client application will not stop
		System.err.println("Shutting down client ...");
		if (clientConnection != null) {
			clientConnection.close();
		}
		System.err.println("Shutting down client done");
	}

	// -----------------------------------------------------
	// User Management Events
	// -----------------------------------------------------

	@Subscribe
	public void userLoggedIn(LoginSuccessfullMessage message) {
		if (message.getSession().isValid()) {
			this.username = message.getUsername();
			this.userSession = message.getSession();
			showLobbyScreen();
		} else {
			showLoginErrorScreen();
		}
	}


	@Subscribe
	public void userRegistered(RegisterSuccessfullMessage message){
		loginScene = null;
		checker = true;
		showLoginScreen();
	}

	@Subscribe
	public void newUser(UserLoggedInMessage userName) {
		if (userSession.isValid()) {
			userService.retrieveAllUsers(userSession);
		}
	}

	@Subscribe
	public void userLeft(UserLoggedOutMessage username) {
		if (userSession.isValid()) {
			userService.retrieveAllUsers(userSession);
		}
	}

	@Subscribe
	public void userList(UsersListMessage userList) {
		updateUsersList(userList.getUsers());
	}

	@Override
	public void exceptionOccured(Throwable e) {
		if (e instanceof LoginException) {
			showLoginErrorScreen();
		} else {
			showServerError(e);
		}
	}

	@Override
	public void connectionEstablished(Channel ch) {
		UserServiceFactory.init(ch);
		// When connection is established, the user service is available
		this.userService = UserServiceFactory.getUserService();
		// register user service as listener to eventbus
		eventBus.register(userService);
		showLoginScreen();
	}

	// -----------------------------------------------------
	// JavFX Help methods
	// -----------------------------------------------------

	public void showServerError(Throwable e) {
		Platform.runLater(new Runnable() {

			@Override
			public void run() {
				Alert a = new Alert(AlertType.ERROR, "Server returned an error:\n" + e.getMessage());
				a.showAndWait();
			}
		});
	}

	private void showLoginErrorScreen() {
		Platform.runLater(new Runnable() {

			@Override
			public void run() {
				Alert alert = new Alert(AlertType.ERROR, "Error logging in to server");
				alert.showAndWait();
				showLoginScreen();
			}
		});
	}

	private void showLobbyScreen() {
		// Show lobby window
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				primaryStage.setTitle("SWP Demo Application for " + username);
				if (lobbyScene == null) {
					GridPane rootPane = new GridPane();
					lobbyScene = new Scene(rootPane, 800, 600);
					users = FXCollections.observableArrayList();
					ListView<String> usersView = new ListView<String>(users);
					rootPane.add(usersView, 1, 1);
				}
				primaryStage.setScene(lobbyScene);
			}
		});

	}

	private void showLoginScreen() {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {

				// first approach: Login Screen with Java Code
				if (loginScene == null) {
					GridPane rootPane = new GridPane();

					loginScene = new Scene(rootPane, 600, 200);
					Label name = new Label("Name:");
					TextField nameField = new TextField("Bitte Name eingeben");
					Label password = new Label("Passwort:");
					Label registerSuccess = new Label("Registrierung erfolgreich!");
					PasswordField passwordField = new PasswordField();
					rootPane.add(name, 1, 1);
					rootPane.add(nameField, 2, 1);
					rootPane.add(password, 1, 2);
					rootPane.add(passwordField, 2, 2);

					if(checker == true){
						rootPane.add(registerSuccess, 1, 6);
					}

					Button login = new Button("Login");
					login.setOnAction(new EventHandler<ActionEvent>() {

						@Override
						public void handle(ActionEvent event) {
							userService.login(nameField.getText(), passwordField.getText());
						}
					});

					rootPane.add(login, 1, 3);

					Label register = new Label("Noch nicht registriert?");
					Button registerBtn = new Button("Registrieren");
					registerBtn.setOnAction(new EventHandler<ActionEvent>() {
						@Override
						public void handle(ActionEvent event) {
							showRegisterScreen();
						}
					});
					rootPane.add(register, 1, 4);
					rootPane.add(registerBtn, 2, 4);



					loginScene.setOnKeyPressed(new EventHandler<KeyEvent>() {
						public void handle(KeyEvent event) {
							if (event.getCode() == KeyCode.ENTER) {
								userService.login(nameField.getText(), passwordField.getText());
							}
						};
					});

				}

				primaryStage.setScene(loginScene);
				primaryStage.show();
			}
		});

	}

	private void showRegisterScreen(){
		Platform.runLater(new Runnable() {

			@Override
			public void run() {

				if(registerScene == null){

					GridPane rootPane = new GridPane();

					registerScene = new Scene(rootPane, 600, 300);
					Label name = new Label("Name:");
					Label password = new Label("Passwort:");
					Label passwordCheck = new Label("Passwort wiederholen:");
					Label registerFail = new Label("Passwörter stimmen nicht überein!");
					TextField nameField = new TextField();
					PasswordField passwordField = new PasswordField();
					PasswordField passwordFieldCheck = new PasswordField();
					rootPane.add(name, 1, 1);
					rootPane.add(nameField, 2,1);
					rootPane.add(password, 1, 2);
					rootPane.add(passwordField, 2, 2);
					rootPane.add(passwordCheck, 1, 3);
					rootPane.add(passwordFieldCheck, 2, 3);
					if(registerFailBoolean == true){
						rootPane.add(registerFail, 3,3);
					}
					Button register = new Button("Registrieren");
					register.setOnAction(new EventHandler<ActionEvent>() {
						@Override
						public void handle(ActionEvent event) {
							if(passwordField.getText().equals(passwordFieldCheck.getText()) && (passwordField.getText().isEmpty() == false || passwordFieldCheck.getText().isEmpty() == false)){
								userService.register(nameField.getText(), passwordField.getText());

							}
							else{
								registerScene = null;
								registerFailBoolean = true;
								showRegisterScreen();
							}
						}
					});
					Button back = new Button("Zurück");
					back.setOnAction(new EventHandler<ActionEvent>() {
						@Override
						public void handle(ActionEvent event) {
							showLoginScreen();
						}
						});
					rootPane.add(register, 2,4);
					rootPane.add(back, 1, 4);

				}
				primaryStage.setScene(registerScene);
			}
		});
	}


	private void updateUsersList(List<String> userList) {
		// Attention: This must be done on the FX Thread!
		Platform.runLater(new Runnable() {

			@Override
			public void run() {
				users.clear();
				users.addAll(userList);
			}
		});
	}

	public static void main(String[] args) {
		launch(args);
	}

}
