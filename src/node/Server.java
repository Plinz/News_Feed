package node;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

public class Server implements NodeInterface {
	private List<ClientInterface> clientList;
	private List<Tuple<String, String>> history;
	private File historyFile;
	private FileOutputStream fo;
	private ObjectOutputStream oo;
	
	@SuppressWarnings("unchecked")
	public Server(){
		clientList = new ArrayList<ClientInterface>();
		history = new ArrayList<Tuple<String, String>>();

		FileInputStream fi;
		try {
			historyFile = new File("historyFile");
			fo = new FileOutputStream(historyFile, true);
			if (historyFile.length() != 0) {
				fi = new FileInputStream(historyFile);
				ObjectInputStream oi = new ObjectInputStream(fi);
				Tuple<String, String> hist;
				while(fi.available() > 0 && (hist = ((Tuple<String, String>)oi.readObject())) != null){
					history.add(hist);
				}
				oi.close();
				fi.close();
				oo = new ObjectOutputStream(fo) {
			        @Override
			        protected void writeStreamHeader() throws IOException {
			        }
				};
			} else {
				oo = new ObjectOutputStream(fo);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	void writeInFile(File file, Object obj){
		try {
			oo.writeObject(obj);
			oo.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
//		try {			
////			FileOutputStream f = new FileOutputStream(file, true);
////			ObjectOutputStream o = new ObjectOutputStream(f);
//			
////			o.close();
////			f.close();
//		} catch (FileNotFoundException e) {
//			System.out.println("File not found");
//		} catch (IOException e) {
//			System.out.println("Error initializing stream");
//		}
	}
	
	@Override
	public boolean join(ClientInterface client) throws RemoteException {
		boolean joined = true;
		for(ClientInterface c : clientList){
			if (c.getName().equals(client.getName())){
				joined = false;
			}
		}
		if(joined && !clientList.contains(client) && !client.getName().equals("SERVER") && !client.getName().equals("ERROR")){
			clientList.add(client);
			history.forEach(t -> {
				try {
					client.postMessage(t.x, t.y);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			});
			clientList.forEach(c -> {
				try {
					client.userJoin(c.getName());
					c.postMessage("SERVER", client.getName()+" rentre dans le chat");
					if (!client.getName().equals(c.getName()))
						c.userJoin(client.getName());
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			});
			System.out.println("Join Client : "+client.getName());
		} else {
			joined = false;
		}
		return joined;
	}

	@Override
	public void leave(ClientInterface client) throws RemoteException {
		if(clientList.contains(client)){
			String name = client.getName();
			clientList.remove(client);
			clientList.forEach(c -> {
				try {
					c.postMessage("SERVER", client.getName()+" quitte dans le chat");
					c.userLeave(name);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			});
			System.out.println("Leave Client : "+name);
		}
	}

	@Override
	public void sendMessage(ClientInterface client, String message) throws RemoteException {
		if (clientList.contains(client)){
			System.out.println("Nouveau Message de "+client.getName()+">"+message);
			String name = client.getName();
			Tuple<String, String> tuple = new Tuple<String, String>(name, message);
			writeInFile(historyFile, tuple);
			history.add(tuple);
			for(ClientInterface c : clientList){
				if(!c.equals(client))
					c.postMessage(name, message);
			}
		}
	}
	
	@Override
	public void getHistory(ClientInterface client) throws RemoteException {
		for(Tuple<String, String> t : history){
			client.postMessage(t.x, t.y);
		}	
	}

}
