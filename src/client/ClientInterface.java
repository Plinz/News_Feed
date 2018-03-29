package client;

import java.rmi.Remote;
import java.rmi.RemoteException;

import utils.Message;

public interface ClientInterface extends Remote{
	void publish(Message message) throws RemoteException;
	String getName() throws RemoteException;
}
