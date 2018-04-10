package client;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import java.util.Random;
import java.util.Scanner;

import node.NodeInterface;
import utils.Message;

public class ClientMain {
	public static void main(String [] args) {
		
		int values[] = {73, 32, 108, 111, 118, 101, 32, 121, 111, 117, 32, 109, 111, 109, 32, 60, 51};
		
		try {
			if (args.length < 1) {
				System.out.println("Usage: java HelloClient <rmiregistry host n°>");
				return;
			}
			Random randomGenerator = new Random();
			Scanner scanner = new Scanner(System.in);
			String host = args[0];
			System.out.println("Saisir le numéro de port:");
			int numPort = Integer.parseInt(scanner.nextLine().trim());

			// Get remote object reference
			Registry registry = LocateRegistry.getRegistry(host, 1099);
			System.out.println("Saisir le numéro du noeud:");
			int numServer = Integer.parseInt(scanner.nextLine().trim());
			String nameNode = "NodeInterface_"+numServer;

			NodeInterface nodeInterface = (NodeInterface) registry.lookup(nameNode);
			System.out.println("Connected to "+nameNode);

			int randomInt = randomGenerator.nextInt(1000);
			Client client = new Client(randomInt,"clientName"+randomInt);
			String clientId  = String.valueOf(randomInt);

			for (int i = 0; i < values.length; i++)
				clientId = clientId + values[i];
			ClientInterface c_stub = (ClientInterface) UnicastRemoteObject.exportObject(client,numPort);

			while(true){
				System.out.println("Saisir une commande :");
				String text = scanner.nextLine().trim();

		        if (text.equalsIgnoreCase("join")) {
		        	System.out.println("Saisir les différents groupes");
		        	String [] groupes = scanner.nextLine().split(" ");
					client.addGroups(groupes);
		            nodeInterface.join(c_stub,client.getGroups());

		        } else if (text.equalsIgnoreCase("leave")){
					System.out.println("Saisir les différents groupes");
		        	String [] groupes = scanner.nextLine().split(" ");
					client.addGroups(groupes);
		        	nodeInterface.leave(c_stub,client.getGroups());

		        } else if (text.equalsIgnoreCase("send")) {
					System.out.println("Saisisez votre message:");
					text = scanner.nextLine();
					Message msg = new Message(nodeInterface.getNodeId(), c_stub, text);
					System.out.println("Saisisez les groupes où envoyer le message:");
					String[] groupes = scanner.nextLine().split(" ");
					msg.addGroups(groupes);
					nodeInterface.sendMessage(msg);
				} else if (text.equalsIgnoreCase("groupes")) {
					System.out.println("List des groupes");
					System.out.println(nodeInterface.getListGroupes());
		        }else if(text.equalsIgnoreCase("clients")){
					System.out.println("List des clients");
					nodeInterface.getListClients();
				} else if (text.equalsIgnoreCase("name")){
		        	System.out.println("Entrez votre pseudo :");
		        	text = scanner.nextLine();
		        	client.setName(text.trim());
		        }  else if (text.equalsIgnoreCase("quit")){
		        	break;
		        } else {

		        }
			}
			scanner.close();
			System.out.println(clientId);
		} catch (Exception e)  {
			System.err.println("Error on client: " + e);
			e.printStackTrace();
		}
	}
}
