package node;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.HashSet;
import java.util.Set;

import client.ClientInterface;
import utils.Message;

public interface NodeInterface extends Remote {
	void join(ClientInterface client, Set<String> groups) throws RemoteException;
	void leave(ClientInterface client, Set<String> groups) throws RemoteException;
	void sendMessage(Message message) throws RemoteException;
	int getNodeId() throws RemoteException;
	HashSet<ClientInterface> getListClients() throws RemoteException;
	Set<String> getListGroupes() throws RemoteException;
	int askIdClient() throws RemoteException;
}
