package node;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class ServerMain {
	  public static void  main(String [] args) {
		  try {
		    NodeInterface serverInterface = new Server();
		    NodeInterface s_stub = (NodeInterface) UnicastRemoteObject.exportObject(serverInterface, 0);

		    Registry registry= LocateRegistry.getRegistry(); 
		    registry.bind("ServerInterface", s_stub);
		    System.out.println ("Server ready");

		  } catch (Exception e) {
			  System.err.println("Error on server :" + e) ;
			  e.printStackTrace();
		  }
	  }
}
