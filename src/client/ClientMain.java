package client;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import java.util.*;

import node.NodeInterface;
import utils.Message;

public class ClientMain {
	public static void main(String [] args) {

		try {
			if (args.length < 1) {
				System.out.println("Usage: java HelloClient <rmiregistry host n°>");
				return;
			}

			Scanner scanner = new Scanner(System.in);
			String host = args[0];

			// Get remote object reference
			Registry registry = LocateRegistry.getRegistry(host, 1099);
			System.out.println("Saisir le numéro du noeud:");
			int numServer = Integer.parseInt(scanner.nextLine().trim());
			String nameNode = "NodeInterface_"+numServer;

			NodeInterface nodeInterface = (NodeInterface) registry.lookup(nameNode);
			System.out.println("Connected to "+nameNode);

			int idClient = nodeInterface.askIdClient();
			Client client = new Client(idClient,"clientName"+idClient);
			ClientInterface c_stub = (ClientInterface) UnicastRemoteObject.exportObject(client,0);
			setAndCheckNameClient(client,c_stub,nodeInterface,scanner);
			HashSet<String> groupes;

			while(true){
				System.out.println("Saisir une commande :");
				String [] cmd = scanner.nextLine().split(" ");
				groupes = tabToCollection(Arrays.copyOfRange(cmd, 1, cmd.length));

		        if (cmd[0].equalsIgnoreCase("join")) {
					client.getGroups().addAll(groupes);
		            nodeInterface.join(c_stub,groupes);

		        } else if (cmd[0].equalsIgnoreCase("leave")){
					client.getGroups().removeAll(groupes);
		        	nodeInterface.leave(c_stub,groupes);

		        } else if (cmd[0].equalsIgnoreCase("send")) {
					System.out.println("Saisisez votre message:");
					String text = scanner.nextLine();
					Message msg = new Message(nodeInterface.getNodeId(), c_stub, text);
					msg.getGroups().addAll(groupes);
					nodeInterface.sendMessage(msg);

				} else if (cmd[0].equalsIgnoreCase("groups")) {
					afficheListGroupes(nodeInterface);

		        }else if(cmd[0].equalsIgnoreCase("clients")) {
					afficheListClients(nodeInterface);

				}else if(cmd[0].equalsIgnoreCase("subgroupes")){
		        	System.out.println("Mes groupes : ");
					for(String group : client.getGroups())
						System.out.println("\t"+group);

				} else if (cmd[0].equalsIgnoreCase("name")){
		        	System.out.println("Entrez votre pseudo :");
		        	String text = scanner.nextLine();
		        	client.setName(text.trim());

		        }  else if (cmd[0].equalsIgnoreCase("quit")){
		        	break;
		        }else{
		        	System.out.println("Mauvaise commande ou inexistante");
				}
			}
			scanner.close();
		} catch (Exception e)  {
			System.err.println("Error on client: " + e);
			e.printStackTrace();
		}
	}

	static void afficheListClients(NodeInterface nodeInterface) throws RemoteException {
		System.out.println("List des clients");
		for(ClientInterface client : nodeInterface.getListClients())
			System.out.println("\t"+client.getName());
	}
	static void afficheListGroupes(NodeInterface nodeInterface) throws RemoteException{
		System.out.println("List des groupes");
		for(String group : nodeInterface.getListGroupes())
			System.out.println("\t"+group);
	}
	static HashSet<String> tabToCollection(String [] tab){
		HashSet<String> collection = new HashSet<>();
		for(String s : tab){
			collection.add(s);
		}
		return collection;
	}

	static void setAndCheckNameClient(Client client,ClientInterface c_stub,NodeInterface nodeInterface, Scanner scanner) throws RemoteException {
		System.out.println("Entrez votre pseudo :");
		String text = scanner.nextLine();
		if(!text.isEmpty()) {
			while(nodeInterface.checkAvailableClient(c_stub,text)!=false){
				System.out.println("Entrez votre pseudo :");
				text = scanner.nextLine();
			}
			client.setName(text);
		}
	}
}
