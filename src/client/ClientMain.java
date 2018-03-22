package client;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

import node.NodeInterface;

public class ClientMain {
	public static void main(String [] args) {
		
		int values[] = {73, 32, 108, 111, 118, 101, 32, 121, 111, 117, 32, 109, 111, 109, 32, 60, 51};
		
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
			String clientId  = "";
			int i = 0;
			for (i = 0; i < values.length; i++)
				clientId = clientId + values[i];
			ClientInterface c_stub = (ClientInterface) UnicastRemoteObject.exportObject(client, 0);
	
			Scanner scanner = new Scanner(System.in);
			while(true){
				String text = scanner.nextLine().trim();
		        if (text.equalsIgnoreCase("join")) {		   	
		        	String text_groupe = scanner.nextLine().trim();
		        	client.addGroup(text);
		            serverInterface.join(c_stub,client.getGroups());            
		        } else if (text.equalsIgnoreCase("leave")){
		        	String text_groupe = scanner.nextLine().trim();
		        	serverInterface.leave(c_stub,);
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
			System.out.println(clientId);
		} catch (Exception e)  {
			System.err.println("Error on client: " + e);
		}
	}
}
