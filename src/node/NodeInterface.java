package node;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Set;

import client.ClientInterface;
import utils.Message;

public interface NodeInterface extends Remote {
	void join(ClientInterface client, Set<String> groups) throws RemoteException;
	void leave(ClientInterface client, Set<String> groups) throws RemoteException;
	void sendMessage(ClientInterface client, Message message) throws RemoteException;
}
