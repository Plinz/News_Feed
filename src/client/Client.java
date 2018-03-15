package client;

import java.rmi.RemoteException;
import java.util.HashSet;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class Client implements ClientInterface{
	
	public String name;
	private ObservableList<String> usersList;
	private transient Observable observable = new Observable();
	private transient Set<Observer> observers = new HashSet<>();
	
	public Client(String n){
		name = n;
		usersList = FXCollections.observableArrayList();
	}

	public String getName() throws RemoteException {
		return name;
	}
	
	public void setName(String name) throws RemoteException {
		this.name = name;
	}

	public synchronized void addObserverPostMessage(Observer observer){
		observers.add(observer);
	}
	
	public synchronized void removeObserverpostMessage(Observer observer){
		observers.remove(observer);
	}
	
	public ObservableList<String> getUsersList(){
		return this.usersList;
	}
	@Override
	public void postMessage(String name, String msg) throws RemoteException {
		observers.stream().forEach(o -> o.update(observable, new Tuple<String, String>(name, msg)));
	}

	@Override
	public void userJoin(String name) throws RemoteException {
		usersList.add(name);
	}
	
	@Override
	public void userLeave(String name) throws RemoteException {
		usersList.remove(name);
	}
	
}
