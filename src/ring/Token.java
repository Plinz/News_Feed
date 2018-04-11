package ring;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import client.ClientInterface;
import utils.Message;
import utils.Triple;
import utils.Tuple;

public class Token implements Serializable {

	private static final long serialVersionUID = 8276914654842212952L;
	private List<Message> messages;
	private boolean isElection;
	private int senderNode;
	private HashSet<Tuple<Integer, ClientInterface>> listRemovedClients;
	private HashSet<Tuple<Integer, ClientInterface>> listAddedClients;
	private HashSet<Tuple<Integer, String>> listRemovedGroups;
	private HashSet<Tuple<Integer, String>> listAddedGroups;

	private HashSet<Triple<Integer, String,Integer>> listRemovedGroupsVoted;

	public Token(boolean isElection, int senderNode) {
		super();
		this.messages = new ArrayList<Message>();
		this.isElection = isElection;
		this.senderNode = senderNode;
		this.listRemovedClients = new HashSet<>();
		this.listRemovedGroups = new HashSet<>();

		this.listAddedClients = new HashSet<>();
		this.listAddedGroups = new HashSet<>();
		this.listRemovedGroupsVoted = new HashSet<>();
	}

	public List<Message> getMessages() {
		return messages;
	}

	public void setMessages(List<Message> messages) {
		this.messages = messages;
	}

	public boolean isElection() {
		return isElection;
	}

	public void setElection(boolean isElection) {
		this.isElection = isElection;
	}

	public HashSet<Tuple<Integer, ClientInterface>> getListRemovedClients() {
		return listRemovedClients;
	}

	public HashSet<Tuple<Integer, String>> getListRemovedGroups() {
		return listRemovedGroups;
	}

	public HashSet<Triple<Integer, String, Integer>> getListRemovedGroupsVoted() {
		return listRemovedGroupsVoted;
	}

	public HashSet<Tuple<Integer, ClientInterface>> getListAddedClients() {
		return listAddedClients;
	}

	public HashSet<Tuple<Integer, String>> getListAddedGroups() {
		return listAddedGroups;
	}

	public void printContenuToken() throws RemoteException {

		System.out.println("Liste des messages :");
		for(Message msg :this.getMessages())
			System.out.println("\t"+msg.getMessage());

		System.out.println("Liste des nouveaux groupes :");
		for(Tuple<Integer,String> tuple : getListAddedGroups())
			System.out.println("\t"+tuple.y);

		System.out.println("Liste des groupes enlevés :");
		for(Tuple<Integer,String> tuple : getListRemovedGroups())
			System.out.println("\t"+tuple.y);

		System.out.println("Liste des nouveaux groupes :");
		for(Tuple<Integer,ClientInterface> tuple : getListAddedClients())
			System.out.println("\t"+tuple.y.getName());

		System.out.println("Liste des clients enlevés :");
		for(Tuple<Integer,ClientInterface> tuple : getListRemovedClients())
			System.out.println("\t"+tuple.y.getName());
	}
}
