package utils;

import java.io.Serializable;
import java.util.Set;

public class Message implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 4079482067780341396L;
	private int nodeID;
	private Set<String> groups;
	private String message;
	
	public Message(int nodeID, Set<String> groups, String message) {
		super();
		this.nodeID = nodeID;
		this.groups = groups;
		this.message = message;
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
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
}
