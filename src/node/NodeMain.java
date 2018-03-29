package node;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class NodeMain {
	public static void main(String[] args) {
		if (args.length == 3){
			try {
				NodeInterface nodeInterface = new Node(args[0], args[1], Integer.parseInt(args[2]));

				NodeInterface s_stub = (NodeInterface) UnicastRemoteObject.exportObject(nodeInterface, 1099);
				Registry registry = LocateRegistry.createRegistry(1099);
				registry.bind("NodeInterface", s_stub);

				System.out.println ("Server ready");
				Node node = (Node) nodeInterface;
				node.run();

			} catch (Exception e) {
				e.printStackTrace();
			}
		}else{
			System.out.println("NomQueueReception NomQueueEnvoi Id");
		}
	}
}
