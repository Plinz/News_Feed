package client;

import java.rmi.RemoteException;
import java.util.HashSet;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import utils.Message;

public class Client implements ClientInterface{
	
	public String name;
	private transient Set<String> groups = new HashSet<>();
	
	public Client(String n){
		name = n;
	}

	public String getName() throws RemoteException {
		return name;
	}
	
	public void setName(String name) throws RemoteException {
		this.name = name;
	}

	@Override
	public void publish(Message message) {
		// TODO Auto-generated method stu	
	}

	public Set<String> getGroups() {
		return groups;
	}

	public void addGroup(String group) {
		this.groups.add(group);
	}
}
