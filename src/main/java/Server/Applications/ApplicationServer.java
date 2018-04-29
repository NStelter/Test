package Server.Applications;

import Server.communication.Server;

public class ApplicationServer {

	public static void main(String[] args) throws Exception {
		int port = -1;
		if (args.length == 1){
			try{
				port = Integer.parseInt(args[0]);
			}catch(Exception e){
				port = -1;
			}
		}
		if (port < 0){
			port = 8889;
		}
		System.out.println("Starting Server on port "+port);
		// Reduce getInstance() methods as far as possible!
		new Server(port, UserService.getInstance()).start();
	}
	
}
