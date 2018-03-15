package client;

import java.rmi.Remote;

import utils.Message;

public interface ClientInterface extends Remote{
	void publish(Message message);
}
