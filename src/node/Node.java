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
	private int nbNode;
	private Random randomGenerator;
	
	public Node(String queueNameRecv, String queueNameSend, int id,int nbNode) throws Exception{

		/*** Création des channels et queue pour utliser RabbitMQ***/
		this.factory = new ConnectionFactory();
		this.factory.setHost("localhost");
		this.recv = this.factory.newConnection().createChannel();
		this.send = this.factory.newConnection().createChannel();
		this.queueNameRecv = queueNameRecv;
		this.queueNameSend = queueNameSend;
		this.recv.queueDeclare(this.queueNameRecv, false, false, false, null);
		this.send.queueDeclare(this.queueNameSend, false, false, false, null);

		/*** Listes pour la gestion des clients ***/
		this.listClientsRing = new HashSet<>();
		this.listRemovedClients = new ConcurrentLinkedQueue<>();
		this.listNewClients = new ConcurrentLinkedQueue<>();

		/*** Listes pour la gestion des groupes ***/
		this.listGroupsRing = new HashSet<>();
		this.listRemovedGroups = new ConcurrentLinkedQueue<>();
		this.listNewGroups = new ConcurrentLinkedQueue<>();

		/*** Autres variables ***/
		hasToken = false;
		processToken = new ReentrantLock();
		this.nbNode = nbNode;
		randomGenerator = new Random();
		this.id = id;
		this.electionDone = false;

		/*** Liste de clients par groupes ***/
		this.clientsByGroup = new ConcurrentHashMap<String, ConcurrentLinkedQueue<ClientInterface>>();

		/*** Liste pour les messages reçu par les clients***/
		this.msgQueue = new ConcurrentLinkedQueue<Message>();
	}

	/**
	 * Lance le thread
	 */
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

	/**
	 * Gére la reception du ou des tokens en circulation sur l'anneau. Et appelle
	 * les différentes méthodes selon que le token soit de type election ou message.
	 * @throws Exception
	 */
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

	/**
	 * Gère la gestion des messages à la reception du token. Lit les
	 * nouveaux messages dans le token pour les publier aux clients.
	 * Enlève tout les message publié par le noeud. Puis ajoute les nouveaux
	 * messages que le noeud à reçu pendant le tour d'anneau
	 * @param tok
	 * @throws Exception
	 */
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

	/**
	 * Renvoi le token si l'id inscrit dans celui-ci est plus petit
	 * que l'id du noeud. Dans le cas contraire le token n'est pas renvoyé.
	 * A la fin il ne restera plus qu'un token correspondant à l'ID le plus petit.
	 * Lorsque le token arrivera sur le noeud avec l'id le plus petit alors le token
	 * avec l'écriture des messages peut commencer.
	 * @param tok
	 * @throws Exception
	 */
	private void election(Token tok) throws Exception {
		Message m = tok.getMessages().get(0);
		int precID = Integer.parseInt(m.getMessage());
		if (precID != id){
			if (precID < id) {
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

	/**
	 * Publie le token sur la queue d'envoi
	 * @param token
	 * @throws Exception
	 */
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

	/**
	 * Enleve le client de la liste de groupes dans laquelle il était présent. Puis met
	 * à jour les différentes listes pour l'anneau puisse être informé que ce client n'est
	 * plus présent ou qu'un groupe a été supprimé car plus aucun client n'était présent à l'intérieur
	 * @param client le client souhaitant partir
	 * @param groups liste de groupes quittés par le client
	 * @throws RemoteException
	 */
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

	/**
	 * Donne un identifiant unique sur l'anneau au nouveau client créer
	 * @return
	 * @throws RemoteException
	 */
	public int askIdClient() throws RemoteException{
		while(!hasToken);

		int randomInt = randomGenerator.nextInt(1000);
		HashSet<Integer> listIdClient = new HashSet<>();
		for(ClientInterface client : this.listClientsRing){
			listIdClient.add(client.getId());
		}
		while(listIdClient.contains(randomInt))
			randomInt = randomGenerator.nextInt(1000);

		return randomInt;
	}

	/**
	 * Vérifie que le nom définit par le nouveau client n'est pas déjà utilisé
	 * par un autre client sur l'anneau
	 * @param client
	 * @param name
	 * @return
	 * @throws RemoteException
	 */
	public boolean checkAvailableClient(ClientInterface client, String name) throws RemoteException{
		while(!hasToken);
		boolean isPresent = false;
		HashSet<String> listNameClient = new HashSet<>();
		for(ClientInterface cl : this.listClientsRing){
			if(cl.getName().equals(name))
				isPresent = true;
		}
		return isPresent;
	}

	/**
	 * Getter de la liste de tout les groupes présent sur l'anneau
	 * @return la liste des groupes sur l'anneau
	 * @throws RemoteException
	 */
	public Set<String> getListGroupes() throws RemoteException {
		return this.listGroupsRing;
	}

	/**
	 * Getter de la liste de tout les clients présent sur l'anneau
	 * @return la liste des clients sur l'anneau
	 */
	public HashSet<ClientInterface> getListClients(){
		return this.listClientsRing;
	}

	/**
	 * Vide les listes de clients et de groupes que le noeud a ajouté.
	 * Pour la liste de vote des groupes à enlever, on vérifié aussi que le nombre
	 * de vote est égal au nombre de noeud présent sur l'anneau, si c'est égal
	 * alors on ajoute le groupe à la liste des groupes à enlever.
	 *
	 * @param token Le token reçu par le noeud
	 */
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
			if(triple.x == this.id && triple.z == nbNode) {
				System.out.println("Le vote"+triple.z);
				token.getListRemovedGroups().add(new Tuple<>(triple.x, triple.y));
				tempListGroupVoted.add(triple);
			}
		}
		token.getListRemovedGroupsVoted().removeAll(tempListGroupVoted);

	}

	/**
	 * Lit les informations des listes de clients et groupes. Puis met à jour
	 * la liste local listClientsRing et la liste local listGroupsRing
	 * @param token Le token reçu par le noeud
	 */
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

	/**
	 * Met à jour sur le token, les nouvelles informations sur les nouveaux clients et groupes
	 * ajouté sur le noeud, afin de les transmettre à ses voisins.
	 * @param token Le token reçu par le noeud
	 */
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
			token.getListRemovedGroupsVoted().add(new Triple<>(this.id,group,0));

		listRemovedGroups.clear();

	}


}
