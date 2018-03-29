package node;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import client.ClientInterface;
import ring.Token;
import utils.Message;

public class Node implements NodeInterface {
	private ConnectionFactory factory;
	private String queueNameRecv;
	private String queueNameSend;
	private Channel recv;
	private Channel send;
	private int id;
	private boolean electionDone;
	private Map<String, Set<ClientInterface>> clientsByGroup;
	private Queue<Message> msgQueue;
	
	public Node(String queueNameRecv, String queueNameSend, int id) throws Exception{
		this.factory = new ConnectionFactory();
		this.factory.setHost("localhost");
		this.recv = this.factory.newConnection().createChannel();
		this.send = this.factory.newConnection().createChannel();
		this.queueNameRecv = queueNameRecv;
		this.queueNameSend = queueNameSend;
		this.recv.queueDeclare(this.queueNameRecv, false, false, false, null);
		this.send.queueDeclare(this.queueNameSend, false, false, false, null);
		this.id = id;
		this.electionDone = false;
		this.clientsByGroup = new ConcurrentHashMap<String, Set<ClientInterface>>();
		this.msgQueue = new ConcurrentLinkedQueue<Message>();
	}
	
	public void run(){
		try {
			Token token = new Token(true);
			token.getMessages().add(new Message(id, null, ""+this.id));
			send(token);
			while (true){
				receive();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}	
	}
	
	private void receive() throws Exception{
		//System.out.println(" ["+this.id+"] En attente d'un message");
		Consumer consumer = new DefaultConsumer(recv) {
		  @Override
		  public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body)
		      throws IOException {
				try {
					ByteArrayInputStream bis = new ByteArrayInputStream(body);
					ObjectInput in = null;
					try {
						in = new ObjectInputStream(bis);
						Token tok = (Token) in.readObject();
						if(tok.isElection()){
							election(tok);
						} else {
							messagesGestion(tok);
						}
					} finally {
						try {
							if (in != null) {
								in.close();
							}
						} catch (IOException ex) {
							// ignore close exception
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
		  }
		};
		recv.basicConsume(this.queueNameRecv, true, consumer);
	}
	

	private void messagesGestion(Token tok) {
		List<Message> messages = tok.getMessages();
		Message tmp;
		while((tmp = messages.get(0)) != null && tmp.getNodeID() == this.id){
			messages.remove(tmp);
		}
		for(Message m : messages){
			for(String group : m.getGroups()){
				for(ClientInterface client : clientsByGroup.get(group)){
					try {
						client.publish(m);
					} catch (RemoteException e) {
						e.printStackTrace();
					}
				}
			}
		}
		while((tmp = msgQueue.poll()) != null){
			tok.getMessages().add(tmp);
		}
	}
	
	private void election(Token tok) throws Exception {
		Message m = tok.getMessages().get(0);
		int precID = Integer.parseInt(m.getMessage());
		if (precID != id){
			if (precID > id)
				precID = id;
			m.setMessage(""+precID);
			send(tok);
		} else if (!this.electionDone){
			this.electionDone = true;
			tok.setElection(false);
			tok.getMessages().clear();
			tok.getMessages().addAll(msgQueue);
			this.msgQueue.clear();
			send(tok);
		}
	}
	
	private void send(Token token) throws Exception{
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutput out = null;
		try {
			out = new ObjectOutputStream(bos);   
			out.writeObject(token);
			out.flush();
			send.basicPublish("", this.queueNameSend, null, bos.toByteArray());
		    System.out.println(" ["+this.id+"] Sent '" + token + "'");
		} finally {
			try {
				bos.close();
			} catch (IOException ex) {
				// ignore close exception
			}
		}
	}

	@Override
	public void join(ClientInterface client, Set<String> groups) throws RemoteException {
		for(String group : groups){
			Set<ClientInterface> clients = this.clientsByGroup.get(group);
			if (clients == null){
				clients = new ConcurrentSkipListSet<ClientInterface>();
			}
			clients.add(client);
			this.clientsByGroup.put(group, clients);
		}
	}

	@Override
	public void leave(ClientInterface client, Set<String> groups) throws RemoteException {
		for(String group : groups){
			Set<ClientInterface> clients = this.clientsByGroup.get(group);
			if (clients != null){
				clients.remove(client);
			}
		}
	}

	@Override
	public void sendMessage(ClientInterface client, Message message) throws RemoteException {
		for(String group : message.getGroups()){
			Set<ClientInterface> clients = this.clientsByGroup.get(group);
			if (!clients.contains(client)){
				message.getGroups().remove(group);
			}
		}
		if(!message.getGroups().isEmpty() && !message.getMessage().trim().isEmpty()){
			msgQueue.offer(message);
		}
	}


}
