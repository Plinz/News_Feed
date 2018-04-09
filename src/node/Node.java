package node;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.rmi.RemoteException;
import java.util.*;
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

public class Node implements NodeInterface, Runnable{
	private ConnectionFactory factory;
	private String queueNameRecv;
	private String queueNameSend;
	private Channel recv;
	private Channel send;
	private int id;
	private boolean electionDone;
	private ConcurrentHashMap<String, ConcurrentLinkedQueue<ClientInterface>> clientsByGroup;
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
		this.clientsByGroup = new ConcurrentHashMap<String, ConcurrentLinkedQueue<ClientInterface>>();
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
	

	private void messagesGestion(Token tok) throws Exception {

		List<Message> messages = tok.getMessages();
		List<Message> messagesToRemove = new ArrayList<>();
		Message tmp;
		if(messages.size()>0) {

			for (Message m : messages) {
				System.out.println("Node "+this.id+" "+m.getMessage());
				for (String group : m.getGroups()) {
					if (clientsByGroup.get(group) != null) {
						for (ClientInterface client : clientsByGroup.get(group)) {
							try {
								if (client != m.getClient())
									client.publish(m);
							} catch (RemoteException e) {
								e.printStackTrace();
							}
						}
					}
				}
			}

			for (Message m : messages) {
				if (m.getNodeID() == this.id) {
					messagesToRemove.add(m);
				}
			}
			messages.removeAll(messagesToRemove);
		}
		while((tmp = msgQueue.poll()) != null){
			tok.getMessages().add(tmp);
		}
		send(tok);
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
			if(token.getMessages().size()>0)
				System.out.println(" ["+this.id+"] Sent '" + token.getMessages().get(0).getNodeID()+ "'");
		} finally {
			try {
				bos.close();
			} catch (IOException ex) {
				// ignore close exception
			}
		}
	}

	/**
	 * Ajoute un client à un ou plusieurs groupes. Si ce groupe n'est pas encore existant alors
	 * une nouvelle liste de client est créer associé à ce nouveau groupe
	 *
	 * @param client Le client a ajouter dans un groupe
	 * @param groups La liste de groupes dans lesquels ajouter le client
	 * @throws RemoteException
	 */
	@Override
	public void join(ClientInterface client, Set<String> groups) throws RemoteException {
		for(String group : groups){
			ConcurrentLinkedQueue<ClientInterface> clients = this.clientsByGroup.get(group);
			if (clients == null){
				clients = new ConcurrentLinkedQueue<ClientInterface>();
			}
			clients.add(client);
			this.clientsByGroup.put(group, clients);

		}
		System.out.println("Ajout du client "+client.getName()+" au groupe "+groups);
	}

	@Override
	public void leave(ClientInterface client, Set<String> groups) throws RemoteException {
		for(String group : groups){
			ConcurrentLinkedQueue<ClientInterface> clients = this.clientsByGroup.get(group);
			if (clients != null){
				clients.remove(client);
			}
		}
		System.out.println("Le client "+client.getName()+" quitte le "+groups);
	}

	/**
	 * Envoi un message à une liste de groupe contenu dans le message.
	 * Avant d'envoyer le message, on vérifie que le client envoi des messages uniquement au groupe auquels il appartient,
	 * sinon on retire ce groupe du message. Le message est envoyé uniquement si la liste des groupes et le message ne sont pas vides.
	 *
	 * @param message Le message émis contenant, l'éméetteur du message, la liste des groupes
	 * @throws RemoteException
	 */
	@Override
	public void sendMessage(Message message) throws RemoteException {
		ArrayList<String> groupsToRemoveFromMsg =  new ArrayList<>();
		for(String group : message.getGroups()) {
			ConcurrentLinkedQueue<ClientInterface> clients = this.clientsByGroup.get(group);

			if (clients == null || !clients.contains(message.getClient())) {
				groupsToRemoveFromMsg.add(group);
			}
		}
		if(groupsToRemoveFromMsg.size()>0)
			message.getGroups().removeAll(groupsToRemoveFromMsg);

		System.out.println("Client : "+ message.getClient().getName()+" Message : "+message.getMessage()+"Groupes :"+message.getGroups());
		if(!message.getGroups().isEmpty() && !message.getMessage().trim().isEmpty()){
			msgQueue.offer(message);
		}
	}

	/**
	 * @return L'identifiant du noeud
	 * @throws RemoteException
	 */
	public int getNodeId() throws RemoteException{
		return this.id;
	}


}
