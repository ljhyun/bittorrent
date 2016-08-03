/**
 * 
 */
package connection;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Vector;

import main.RUBTClient;

import structures.FileInfo;
import bencoding.TorrentDecoder;

/**
 * A listener for incoming connections.
 * @author Ari & Jay
 */
public class ListenerThread implements Runnable {
	/**
	 * The actual listener
	 */
	ServerSocket listen;
	
	/**
	 * The list of connected peers:
	 */
	Vector<PeerSocket> peerList;
	
	/**
	 * The meta-data for the torrent.
	 */
	private TorrentDecoder torrent;
	
	/**
	 * Data for the file we want
	 */
	private FileInfo file;
	
	/**
	 * Our peer ID
	 */
	private String my_id;
	
	/**
	 * Boolean to indicate if we should continue listening
	 */
	boolean done;
	
	/**
	 * The port to listen on.
	 */
	int port;
	
	/**
	 * Constructor
	 * @param port The port to listen on
	 * @param peerList The list to which we add connected peers
	 * @param tor The decoded torrent metadata
	 * @param fi The info for the file being downloaded
	 * @param id Our peer ID
	 * @throws IOException If the listener fails to bind to the port.
	 */
	public ListenerThread(int port, Vector<PeerSocket> peerList, TorrentDecoder tor, FileInfo fi, String id) throws IOException {
		this.port = port;
		listen = new ServerSocket(port);
		this.peerList = peerList;
		torrent = tor;
		file = fi;
		my_id = id;
		done = false;
	}

	/**
	 * Starts listening, continues until done = true after calling stop()
	 */
	@Override
	public void run() {
		while(!done) {
			Socket socket = null;
			try {
				socket = listen.accept();
			} catch (IOException e) {}
			
			if(socket != null) {
				try {
					PeerSocket p = new PeerSocket(socket,torrent,file, my_id);
					peerList.add(p);
					new Thread(p).start();
					RUBTClient.log("Incoming connection on listener.");
				} catch (Exception e) {
					RUBTClient.logError("Warning: A peer made a failed attempt to connect to us.");
				}
			}
		}
	}
	
	/**
	 * Start a thread for this listener.
	 */
	public void start() {
		try {
			listen.close();
		} catch (IOException e) {}
		
		try {
			listen = new ServerSocket(port);
		} catch (IOException e) {
			RUBTClient.logError("Listener broke.");
		}
		new Thread(this).start();
	}
	
	/**
	 * Stop listening
	 */
	public void stop() {
		done = true;
		try {
			listen.close();
		} catch (IOException e) {}
	}
}
