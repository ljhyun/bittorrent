package structures;

/**
 * A simple structure to store a peer's ID, ip address, port, and bitfield
 * @author Ari Hayes
 */
public class Peer {
	/**
	 * Peer data:
	 */
	private String id;
	private String ip;
	private int port;
	private Bitfield bitfield = null;

	/**
	 * Constructor
	 * @param id The peer's id
	 * @param ip The peer's ip
	 * @param port The peer's port
	 */
	public Peer(String id, String ip, int port) {
		this.id = id;
		this.ip = ip;
		this.port = port;
	}

	/**
	 * @return the id
	 */
	public String getID() {
		return id;
	}

	/**
	 * @return the ip
	 */
	public String getIP() {
		return ip;
	}

	/**
	 * @return the port
	 */
	public int getPort() {
		return port;
	}

	/**
	 * @return the bitfield
	 */
	public Bitfield getBitfield() {
		return bitfield;
	}
	
	/**
	 * @param peer_id the id
	 */
	public void setID(String peer_id) {
		id = peer_id;
	}
	
	/**
	 * @param bitfield the bitfield to set
	 */
	public void setBitfield(Bitfield bitfield) {
		this.bitfield = bitfield;
	}
}
