package utils;

import client.ClientInterface;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

public class Message implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 4079482067780341396L;
	private int nodeID;
	private Set<String> groups;
	private String message;
	private ClientInterface client;
	
	public Message(int nodeID, ClientInterface client, Set<String> groups, String message) {
		super();
		this.nodeID = nodeID;
		this.groups = groups;
		this.message = message;
	}

	public Message(int nodeID, ClientInterface client,String message) {
		super();
		this.nodeID = nodeID;
		this.groups = new HashSet<>();
		this.message = message;
		this.setClient(client);
	}
	public int getNodeID() {
		return nodeID;
	}
	public void setNodeID(int nodeID) {
		this.nodeID = nodeID;
	}
	public Set<String> getGroups() {
		return groups;
	}
	public void setGroups(Set<String> groups) {
		this.groups = groups;
	}

	public void addGroup(String group) { this.groups.add(group); }

	public void addGroups(String [] groups) {
		for(String group : groups){
			this.addGroup(group);
		}
	}
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}

	public ClientInterface getClient() {
		return client;
	}

	private void setClient(ClientInterface client) {
		this.client = client;
	}
}
