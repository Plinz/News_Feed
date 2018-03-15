package client;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Scanner;

public class ClientMain {
	public static void main(String [] args) {
		try {
			if (args.length < 1) {
				System.out.println("Usage: java HelloClient <rmiregistry host>");
				return;
			}
		
			String host = args[0];
		
			// Get remote object reference
			Registry registry = LocateRegistry.getRegistry(host); 
			NodeInterface serverInterface = (NodeInterface) registry.lookup("ServerInterface");
			
			Client client = new Client("Unnamed");
			ClientInterface c_stub = (ClientInterface) UnicastRemoteObject.exportObject(client, 0);
	
			Scanner scanner = new Scanner(System.in);
			while(true){
				String text = scanner.nextLine().trim();
		        if (text.equalsIgnoreCase("join")) {
		            serverInterface.join(c_stub);
		        } else if (text.equalsIgnoreCase("leave")){
		        	serverInterface.leave(c_stub);
		        } else if (text.equalsIgnoreCase("name")){
		        	System.out.println("Entrez votre pseudo :");
		        	text = scanner.nextLine();
		        	client.setName(text.trim());
		        } else if (text.equalsIgnoreCase("history")){
		        	serverInterface.getHistory(c_stub);
		        } else if (text.equalsIgnoreCase("quit")){
		        	break;
		        } else {
		        	serverInterface.sendMessage(c_stub, text);
		        }
			}
			scanner.close();
		} catch (Exception e)  {
			System.err.println("Error on client: " + e);
		}
	}
}
