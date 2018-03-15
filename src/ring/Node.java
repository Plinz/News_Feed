package ring;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import client.ClientInterface;
import node.NodeInterface;
import utils.Message;

public class Node implements NodeInterface{
	private ConnectionFactory factory;
	private String queueNameRecv;
	private String queueNameSend;
	private Channel recv;
	private Channel send;
	private int id;
	private Map<String, Set<ClientInterface>> clientsByGroup;
	
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
		this.clientsByGroup = new ConcurrentHashMap<String, Set<ClientInterface>>();
	}
	
	public void run(){
		try {
			send("ELECTION;"+id);
			receive();
		} catch (Exception e) {
			e.printStackTrace();
		}	
	}
	
	private void receive() throws Exception{
		System.out.println(" ["+this.id+"] En attente d'un message");
		Consumer consumer = new DefaultConsumer(recv) {
		  @Override
		  public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body)
		      throws IOException {
				try {
					String msg = new String(body, "UTF-8");
					System.out.println(" ["+id+"] Receive message "+msg);
					if(msg.startsWith("ELECTION;")){
						int precID = Integer.parseInt(msg.split(";")[1]);
						if (precID != id){
							if (precID > id)
								precID = id;
							send("ELECTION;"+precID);
						} else {
							send("HELLO le gollum!");
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
		  }
		};
		recv.basicConsume(this.queueNameRecv, true, consumer);
	}
	
	private void send(String message) throws Exception{
	    send.basicPublish("", this.queueNameSend, null, message.getBytes("UTF-8"));
	    System.out.println(" ["+this.id+"] Sent '" + message + "'");
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
	public void sendMessage(ClientInterface client, Set<String> groups, Message message) throws RemoteException {
		for(String group : groups){
			Set<ClientInterface> clients = this.clientsByGroup.get(group);
			if (clients.contains(clients)){
				for(ClientInterface c : clients){
					if (c != client){
						c.publish(message);
					}
				}
			}
		}
	}

	public static void main(String[] args) {
		if (args.length == 3){
			Node n;
			try {
				n = new Node(args[0], args[1], Integer.parseInt(args[2]));
				n.run();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
}
