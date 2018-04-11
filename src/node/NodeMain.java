package node;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class NodeMain {
	public static void main(String[] args) {
		if (args.length == 1){
			try {
				Registry registry = LocateRegistry.createRegistry(1099);
				int nbNodes = Integer.parseInt(args[0]);
				Thread thread;
				for(int i =0 ; i < nbNodes;i++){
					NodeInterface nodeInterface = new Node(String.valueOf(i), String.valueOf((i+1)%nbNodes),i,nbNodes);
					NodeInterface s_stub = (NodeInterface) UnicastRemoteObject.exportObject(nodeInterface, 1099);
					registry.bind("NodeInterface_"+i, s_stub);

					System.out.println ("Node "+i+" ready");
					Node node = (Node) nodeInterface;
					thread = new Thread(node);
					thread.start();
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}else{
			System.out.println("nombre de noeuds");
		}

	}
}
