package connection;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * A class for sending tracker requests and getting a response stream.
 * @author Ari & Jay
 */
public class Tracker {
	/**
	 * The url of the tracker
	 */
	private String url;
	
	/**
	 * The info hash, url encoded
	 */
	private String hash;
	
	/**
	 * The peer id which we are using
	 */
	private String id;
	
	/**
	 * The port on which we are listening for connections
	 */
	private int port;
	
	/**
	 * Constructor
	 * @param url The url of the tracker
	 * @param info_hash The info hash of the torrent
	 * @param peer_id The peer_id which our client will be using
	 * @param port The port on which we are listening for incoming connections
	 */
	public Tracker(String url, String info_hash, String peer_id, int port) {
		this.url = url;
		this.hash = info_hash;
		this.id = peer_id;
		this.port = port;
	}
	
	/**
	 * Sends a GET request to the tracker.
	 * @param uploaded The amount of bytes we have uploaded
	 * @param downloaded The amount of bytes we have downloaded
	 * @param left The amount of bytes we still need to finish the file
	 * @param event The value of the event String to send
	 * @return tracker response as an InputStream
	 * @throws IOException on failed URL get request
	 */
	public InputStream request(long uploaded, long downloaded, int left, String event) throws IOException {
		return new URL(url + "?info_hash=" + hash + "&peer_id=" + id + "&port=" + port + "&uploaded=" + uploaded + "&downloaded=" + downloaded + "&left=" + left + "&event=" + event).openStream();
	}
}
