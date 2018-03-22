package ring;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import utils.Message;

public class Token implements Serializable{

	private static final long serialVersionUID = 8276914654842212952L;
	private List<Message> messages;
	private boolean	isElection;

	public Token(boolean isElection) {
		super();
		this.messages = new ArrayList<Message>();
		this.isElection = isElection;
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
}
