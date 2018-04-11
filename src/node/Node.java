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
import java.util.concurrent.locks.ReentrantLock;

import client.Client;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import client.ClientInterface;
import ring.Token;
import utils.Message;
import utils.Triple;
import utils.Tuple;

public class Node implements NodeInterface, Runnable{
	private ConnectionFactory factory;
	private String queueNameRecv;
	private String queueNameSend;
	private Channel recv;
	private Channel send;
	private int id;
	private boolean electionDone;
	private ConcurrentHashMap<String, ConcurrentLinkedQueue<ClientInterface>> clientsByGroup;

	private HashSet<ClientInterface> listClientsRing;
	private ConcurrentLinkedQueue<ClientInterface> listRemovedClients,listNewClients;
	private HashSet<String> listGroupsRing;
	private ConcurrentLinkedQueue<String> listRemovedGroups,listNewGroups;

	private Queue<Message> msgQueue;
	private boolean hasToken;
	private ReentrantLock processToken;
	
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

		this.listClientsRing = new HashSet<>();
		this.listRemovedClients = new ConcurrentLinkedQueue<>();
		this.listNewClients = new ConcurrentLinkedQueue<>();

		this.listGroupsRing = new HashSet<>();
		this.listRemovedGroups = new ConcurrentLinkedQueue<>();
		this.listNewGroups = new ConcurrentLinkedQueue<>();
		hasToken = false;
		processToken = new ReentrantLock();

	}
	
	public void run(){
		try {
			Token token = new Token(true,this.id);
			token.getMessages().add(new Message(id, null, ""+this.id));
			send(token);
			receive();

		} catch (Exception e) {
			e.printStackTrace();
		}	
	}
	
	private void receive() throws Exception{

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
						processToken.lock();
						hasToken = true;
						processToken.unlock();
						if(tok.isElection()){
							election(tok);
						} else {
							messagesGestion(tok);
							updateAndCleanListRing(tok);
							clearListsToken(tok);
							updateListToken(tok);
							send(tok);
						}
						processToken.lock();
						hasToken = false;
						processToken.unlock();
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
				for (String group : m.getGroups()) {
					if (clientsByGroup.get(group) != null) {
						for (ClientInterface client : clientsByGroup.get(group)) {
							try {
								if (client.getId() != m.getClient().getId())
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
	}

	private void election(Token tok) throws Exception {
		Message m = tok.getMessages().get(0);
		int precID = Integer.parseInt(m.getMessage());
		if (precID != id){
			if (precID < id) {
				//precID = id;
				//m.setMessage("" + precID);
				send(tok);
			}
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
				this.listNewGroups.add(group);
				this.listGroupsRing.add(group);
			}
			if(!clients.contains(client)){
				clients.add(client);
			    this.clientsByGroup.put(group, clients);
			    listNewClients.add(client);
			}

		}
		System.out.println(listNewClients);
		System.out.println("Ajout du client "+client.getName()+" au groupe "+groups);
	}

	@Override
	public void leave(ClientInterface client, Set<String> groups) throws RemoteException {
		for(String group : groups){
			ConcurrentLinkedQueue<ClientInterface> clients = this.clientsByGroup.get(group);
			if (clients != null && clients.contains(client)){
				clients.remove(client);
				if (clients.size() == 0){
					this.listRemovedGroups.add(group);
					this.clientsByGroup.remove(clients);
				}
			}
		}
		Boolean isPresent = false;
		Iterator iterator = clientsByGroup.entrySet().iterator();
		while(iterator.hasNext() && isPresent == false){
			Map.Entry<String,ClientInterface> mapEntry = (Map.Entry<String,ClientInterface>) iterator.next();
			String group = mapEntry.getKey();
			ConcurrentLinkedQueue<ClientInterface> clients = this.clientsByGroup.get(group);
			if(clients.contains(client)){
				isPresent = true;
				System.out.println("LOL");
			}
		}
		if(isPresent == false)
			this.listRemovedClients.add(client);

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

	public int askIdClient() throws RemoteException{
		while(!hasToken);
		Random randomGenerator = new Random();
		int randomInt = randomGenerator.nextInt(1000);
		HashSet<Integer> listIdClient = new HashSet<>();
		for(ClientInterface client : this.listClientsRing){
			listIdClient.add(client.getId());
		}
		while(listIdClient.contains(randomInt))
			randomInt = randomGenerator.nextInt(1000);

		return randomInt;
	}

	public boolean checkAvailableClient(ClientInterface client, String name) throws RemoteException{
		while(!hasToken);
		boolean isPresent = false;
		HashSet<String> listNameClient = new HashSet<>();
		for(ClientInterface cl : this.listClientsRing){
			if(cl.equals(name))
				isPresent = true;
		}
		return isPresent;
	}


	public Set<String> getListGroupes() throws RemoteException {
		return this.listGroupsRing;
	}

	public HashSet<ClientInterface> getListClients(){
		return this.listClientsRing;
	}

	private void clearListsToken(Token token){
		// Vide la liste listAddedClients du token ajouté par le noeud
		ArrayList<Tuple<Integer,ClientInterface>> tempListClient = new ArrayList<>();
		for(Tuple<Integer,ClientInterface> tuple : token.getListAddedClients()){
			if(tuple.x == this.id)
				tempListClient.add(tuple);
		}
		token.getListAddedClients().removeAll(tempListClient);

		// Vide la liste listRemovedClients du token ajouté par le noeud
		tempListClient.clear();
		for(Tuple<Integer,ClientInterface> tuple : token.getListRemovedClients()){
			if(tuple.x == this.id)
				tempListClient.add(tuple);
		}
		token.getListRemovedClients().removeAll(tempListClient);

		// Vide la liste listAddedGroups du token ajouté par le noeud
		ArrayList<Tuple<Integer,String>> tempListGroup = new ArrayList<>();
		for(Tuple<Integer,String> tuple : token.getListAddedGroups()){
			if(tuple.x == this.id)
				tempListGroup.add(tuple);
		}
		token.getListAddedGroups().removeAll(tempListGroup);

		// Vide la liste listRemovedGroups du token ajouté par le noeud
		tempListGroup.clear();
		for(Tuple<Integer,String> tuple : token.getListRemovedGroups()){
			if(tuple.x == this.id)
				tempListGroup.add(tuple);
		}
		token.getListRemovedGroups().removeAll(tempListGroup);

		// Vide la liste listRemovedGroups du token ajouté par le noeud
		ArrayList<Triple<Integer,String,Integer>> tempListGroupVoted = new ArrayList<>();
		for(Triple<Integer,String,Integer> triple : token.getListRemovedGroupsVoted()){
			if(triple.x == this.id && triple.z >= 3) {
				token.getListRemovedGroups().add(new Tuple<>(triple.x, triple.y));
				tempListGroupVoted.add(triple);

			}
		}
		token.getListRemovedGroupsVoted().removeAll(tempListGroupVoted);

	}

	private void updateAndCleanListRing(Token token){

		for(Tuple<Integer,ClientInterface> tuple : token.getListAddedClients())
			this.listClientsRing.add(tuple.y);

		for(Tuple<Integer,ClientInterface> tuple : token.getListRemovedClients())
			this.listClientsRing.remove(tuple.y);

		for(Tuple<Integer,String> tuple : token.getListAddedGroups())
			this.listGroupsRing.add(tuple.y);

		for(Tuple<Integer,String> tuple : token.getListRemovedGroups())
			this.listGroupsRing.remove(tuple.y);

		ArrayList<Tuple<Integer,String>> tempList = new ArrayList<>();
		for(Triple<Integer,String,Integer> triple : token.getListRemovedGroupsVoted()){
			if(clientsByGroup.get(triple.y) == null || clientsByGroup.get(triple.y).size() == 0)
				triple.z++;
		}
	}

	private void updateListToken(Token token){

		for(ClientInterface client : this.listNewClients)
			token.getListAddedClients().add(new Tuple<>(this.id,client));

		this.listNewClients.clear();

		for(ClientInterface client : this.listRemovedClients)
			token.getListRemovedClients().add(new Tuple<>(this.id,client));

		listRemovedClients.clear();

		for(String group : this.listNewGroups)
			token.getListAddedGroups().add(new Tuple<>(this.id,group));

		listNewGroups.clear();

		for(String group : this.listRemovedGroups)
			token.getListRemovedGroupsVoted().add(new Triple<>(this.id,group,1));

		listRemovedGroups.clear();

	}


}
