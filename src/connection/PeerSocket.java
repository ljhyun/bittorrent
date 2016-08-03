package connection;

import java.net.Socket;
import java.net.SocketException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import main.RUBTClient;

import bencoding.TorrentDecoder;

import structures.Bitfield;
import structures.FileInfo;
import structures.InfoHash;
import structures.Peer;

/**
 * A class which communicates with a given peer through a new socket.
 * @author Ari & Jay
 */
public class PeerSocket implements Runnable {
	/**
	 * The socket through which we connect to the peer.
	 */
	private TCPSocket socket;
	
	/**
	 * Mutex for messaging, so other threads can send choke, unchoke.
	 */
	private Object socket_lock = new Object();
	
	/**
	 * The peer's data (ip, port, id)
	 */
	private Peer peer;
	
	/**
	 * The meta-data for the torrent.
	 */
	private TorrentDecoder torrent;
	
	/**
	 * Data for the file we want
	 */
	private FileInfo file;
	
	/**
	 * Our peer_id
	 */
	private String my_id;
	
	/**
	 * The piece we have requested, or -1
	 */
	private int my_requested_piece = -1;
	
	/**
	 * The bytes of the current piece.
	 */
	private byte[] piece_bytes;
	
	/**
	 * How many bytes of the current piece we have.
	 */
	private int piece_bytes_done;
	
	/**
	 * Whether or not we are waiting for a requested piece.
	 */
	private boolean waiting_for_piece = false;
	
	/**
	 * Whether the peer has pieces we need or not.
	 */
	private boolean useful_pieces = false;
	
	/**
	 * The bits which the peer knows we have:
	 */
	private Bitfield bitfield;
	
	/**
	 * Whether or not the peer has sent us messages
	 * Used to determine if bitfield makes sense for them to send
	 */
	private boolean first_message = true;
	
	/**
	 * We are choking
	 */
	private boolean am_choking = true;
	
	/**
	 * We are interested
	 */
	private boolean am_interested = false;
	
	/**
	 * Peer is choking
	 */
	private boolean peer_choking = true;
	
	/**
	 * Peer is interested
	 */
	private boolean peer_interested = false;
	
	/**
	 * Mutex for choking/interested states for peer
	 */
	private Object peer_state_lock = new Object();
	
	/**
	 * Constant for messages
	 */
	private final byte message_choke = 0;
	
	/**
	 * Constant for messages
	 */
	private final byte message_unchoke = 1;
	
	/**
	 * Constant for messages
	 */
	private final byte message_interested = 2;
	
	/**
	 * Constant for messages
	 */
	private final byte message_uninterested = 3;
	
	/**
	 * Constant for messages
	 */
	private final byte message_have = 4;
	
	/**
	 * Constant for messages
	 */
	private final byte message_bitfield = 5;
	
	/**
	 * Constant for messages
	 */
	private final byte message_request = 6;
	
	/**
	 * Constant for messages
	 */
	private final byte message_piece = 7;
	
	/**
	 * Bytes downloaded by peer
	 */
	private long bytes_uploaded = 0;
	
	/**
	 * Bytes downloaded from peer
	 */
	private long bytes_downloaded = 0;
	
	/**
	 * Indicates that bytes downloaded and uploaded should be set to zero.
	 */
	private boolean reset_bytes = false;
	
	/**
	 * Static counter of unchoked peers
	 */
	private static int unchoked_count = 0;
	
	/**
	 * Maximum number of unchoked peers:
	 */
	private static final int unchoked_max = 3;
	
	/**
	 * Mutex for unchoked counter:
	 */
	private static Object unchoked_lock = new Object();
	
	/**
	 * Constructor for PeerSocket.
	 * @param p The Peer object.
	 * @param tf The TorrentDecoder object
	 * @param fi The FileInfo object
	 * @param my_peer_id The user's peer id
	 * @throws Exception on failed attempt to create a socket.
	 */
	public PeerSocket(Peer p, TorrentDecoder tf, FileInfo fi, String my_peer_id) throws Exception {
		peer = p;
		torrent = tf;
		file = fi;
		bitfield = new Bitfield(file.getBitfieldBitSize());
		my_id = my_peer_id;
		socket = null;
	}
	
	/**
	 * Constructor
	 * @param sock The connected socket
	 * @param tf the decoded metadata for the torrent
	 * @param fi the info for the downloading file
	 * @param my_peer_id the peer ID of us, the user
	 * @throws SocketException on failed creation of TCPSocket
	 */
	public PeerSocket(Socket sock, TorrentDecoder tf, FileInfo fi, String my_peer_id) throws SocketException {
		peer = new Peer("", sock.getRemoteSocketAddress().toString(), sock.getPort());
		torrent = tf;
		file = fi;
		bitfield = new Bitfield(file.getBitfieldBitSize());
		my_id = my_peer_id;
		socket = new TCPSocket(sock);
	}
	
	/**
	 * Start communicating with the peer.
	 */
	public void download() {
		//Create TCPSocket for outgoing connection:
		if(socket == null) {
			try {
				socket = new TCPSocket(peer.getIP(),peer.getPort());
			} catch (Exception e) {
				RUBTClient.logError("Failed to connect socket for peer " + peer.getID() + ".");
				return;
			}
		}
		
		//Perform handshake:
		if(!handshake()) {
			RUBTClient.logError("Handshake failed with " + peer.getID() + ".");
			socket.tryClose();
			socket = null;
			return;
		}
		else {
			RUBTClient.log("Successful handshake with " + peer.getID() + ".");
		}
		
		//Start post-handshake communication:
		while(socket.connected()) {
			//Reset byte counts if appropriate:
			if(reset_bytes) {
				reset_bytes = false;
				bytes_uploaded = 0;
				bytes_downloaded = 0;
			}
			
			//Unchoke peer if appropriate:
			synchronized(peer_state_lock) {
				if(peer_interested && peer_choking && incrementUnchoked()) {
					RUBTClient.log("Sending unchoke to peer " + peer.getID());
					
					//Send unchoke:
					socket.sendInteger(1);
					socket.sendByte(message_unchoke);
					peer_choking = false;
				}
			}
			
			//Tell the peer about any new pieces we have:
			for(int x = 0; x < bitfield.getBitSize(); x++) {
				if(bitfield.getBit(x) == 0 && file.getPieceState(x) == FileInfo.state_complete) {
					bitfield.setBit(x, true);
					socket.sendInteger(5);
					socket.sendByte(message_have);
					socket.sendInteger(x);
					RUBTClient.log("Telling peer " + peer.getID() + " we have piece " + x);
				}
			}
			
			if(am_choking && !am_interested) {//need to express interest
				if(!useful_pieces) {
					parseMessage();
				}
				else {
					am_interested = true;
					socket.sendInteger(1);
					socket.sendByte(message_interested);
				}
			}
			else if(!am_choking && am_interested) {//need to request piece
				if(!useful_pieces) {
					am_interested = false;
					socket.sendInteger(1);
					socket.sendByte(message_uninterested);
				}
				else if(my_requested_piece < 0) {//no piece requested
					//Determine which piece to request:
					my_requested_piece = file.getMissingPiece(peer.getBitfield());
					if(my_requested_piece == -1) {//no needed piece
						useful_pieces = false;
						am_interested = false;
						socket.sendInteger(1);
						socket.sendByte(message_uninterested);
						continue;
					}
					else if(!file.downloadingPiece(my_requested_piece)) {//another peer got there first!
						my_requested_piece = -1;
						continue;
					}
					
					//Prepare to request piece:
					if(my_requested_piece == torrent.getHashCount() - 1) {//last piece
						int last_size = torrent.getFileLength() % torrent.getPieceLength();
						if(last_size == 0)
							last_size = torrent.getPieceLength();
						piece_bytes = new byte[last_size];
					}
					else {//normal piece
						piece_bytes = new byte[torrent.getPieceLength()];
					}
					piece_bytes_done = 0;
					int bytes_to_request = piece_bytes.length - piece_bytes_done;
					if(bytes_to_request > 16384)
						bytes_to_request = 16384;
					waiting_for_piece = true;
					
					//Start requesting piece:
					socket.sendInteger(13);
					socket.sendByte(message_request);
					socket.sendInteger(my_requested_piece);
					socket.sendInteger(piece_bytes_done);
					socket.sendInteger(bytes_to_request);
				}
				else if(waiting_for_piece) {//need to get data from stream
					parseMessage();
				}
				
				else if(piece_bytes_done == piece_bytes.length) {//piece is done
					//Calculate piece's hash value
					MessageDigest md = null;
					try {
						md = MessageDigest.getInstance("SHA-1");
					} catch (NoSuchAlgorithmException e) {
						RUBTClient.logError("Impossible Error: SHA-1 hashes don't exist.");
						break;
					}
					md.update(piece_bytes);
					byte[] hash = md.digest();
					
					//Verify hash:
					boolean valid_hash = true;
					for(int x = 0; x < 20; x++) {
						if(hash[x] != torrent.getHashByte(my_requested_piece,x)) {
							valid_hash = false;
						}
					}
					
					if(valid_hash) {//Store piece
						try {
							file.completePiece(my_requested_piece, piece_bytes);
							RUBTClient.log("Downloaded & verified piece " + my_requested_piece);
							RUBTClient.log("We have " + file.getCompleteCount() + "/" + file.getBitfieldBitSize() + " pieces.");
						} catch (Exception e) {
							RUBTClient.logError("Impossible Error: This verified piece makes no sense.");
							file.cancelPiece(my_requested_piece);
							break;
						}
						
						//Prepare for next piece:
						my_requested_piece = -1;
					}
					else {//Delete piece's data
						RUBTClient.logError("Warning: Piece has wrong hash. Downloading again.");
						piece_bytes_done = 0;
					}
				}
				else {//request more of current piece
					//Prepare to request piece:
					int bytes_to_request = piece_bytes.length - piece_bytes_done;
					if(bytes_to_request > 16384)
						bytes_to_request = 16384;
					waiting_for_piece = true;
					
					//Continue requesting piece:
					socket.sendInteger(13);
					socket.sendByte(message_request);
					socket.sendInteger(my_requested_piece);
					socket.sendInteger(piece_bytes_done);
					socket.sendInteger(bytes_to_request);
				}
			}
			else {
				parseMessage();
			}
		}
		
		//Disconnected after requesting piece:
		if(my_requested_piece >= 0) {
			file.cancelPiece(my_requested_piece);
			my_requested_piece = -1;
		}
		
		if(socket.connected()) {
			socket.tryClose();
			socket = null;
		}
		
		//Disconnected after unchoking peer:
		synchronized(peer_state_lock) {
			synchronized(unchoked_lock) {
				if(!peer_choking) {
					peer_choking = true;
					decrementUnchoked();
				}
			}
		}
		
		//Decrement common-ness/antirarity/whatever of this peer's pieces
		if(peer.getBitfield() != null) {
			for(int x = 0; x < peer.getBitfield().getBitSize(); x++) {
				if(peer.getBitfield().getBit(x) == 1) {
					file.decrementPieceRarity(x);
				}
			}
		}
	}
	
	/**
	 * Gets data from the socket and responds appropriately.
	 */
	public void parseMessage() {
		//Get message length
		//Outside lock so other threads can choke/unchoke us while waiting for messages
		int message_length = socket.getInteger();
		
		synchronized(socket_lock) {
			if(message_length == 0) {//keep-alive
				//Keep-alive is basically automatically implemented by the built-in timeout feature in Socket class, set to two minutes in TCPSocket.
				first_message = false;
				return;
			}
			else if(message_length == -1) {//failure to read from socket (end of bytestream)
				socket.tryClose();
				RUBTClient.log("Warning: Peer " + peer.getID() + " socket not reading; presumed disconnected.");
				return;
			}
			else if(message_length < 0) {//negative length?!
				RUBTClient.logError("Warning: Peer " + peer.getID() + " sent negative message-length " + message_length + "; disconnecting.");
				socket.tryClose();
				return;
			}
			
			//Get message id
			message_length--;
			int message = socket.getByte();
			if(message < 4) {
				if(message_length != 0) {
					RUBTClient.logError("Warning: Peer " + peer.getID() + " sent invalid message-length; disconnecting.");
					socket.tryClose();
					return;
				}
			}
			
			if(message == message_choke) {
				first_message = false;
				am_choking = true;
				waiting_for_piece = false;
				if(my_requested_piece >= 0) {
					file.cancelPiece(my_requested_piece);
					my_requested_piece = -1;
				}
			}
			else if(message == message_unchoke) {
				first_message = false;
				am_choking = false;
			}
			else if(message == message_interested) {
				first_message = false;
				synchronized(peer_state_lock) {
					peer_interested = true;
				}
				
				RUBTClient.log("Peer " + peer.getID() + " interested");
			}
			else if(message == message_uninterested) {
				first_message = false;
				
				synchronized(peer_state_lock) {
					peer_interested = false;
					if(!peer_choking) {
						RUBTClient.log("Sending choke to peer " + peer.getID());
						
						//Send choke:
						socket.sendInteger(1);
						socket.sendByte(message_choke);
						peer_choking = true;
						decrementUnchoked();
					}
				}
				
				RUBTClient.log("Peer " + peer.getID() + " uninterested");
			}
			else if(message == message_have) {
				first_message = false;
				if(message_length != 4) {
					RUBTClient.logError("Warning: Peer " + peer.getID() + " sent invalid message-length; disconnecting.");
					socket.tryClose();
					return;
				}
				
				//Get bitfield bit:
				int piece = socket.getInteger();
				if(piece < 0 || piece >= file.getBitfieldBitSize()) {
					RUBTClient.logError("Warning: Peer " + peer.getID() + " sent invalid bitfield-index; disconnecting.");
					socket.tryClose();
					return;
				}
				
				//Set bitfield appropriately
				if(peer.getBitfield() == null) {
					peer.setBitfield(new Bitfield(file.getBitfieldBitSize()));
				}
				peer.getBitfield().setBit(piece, true);
				
				//Increment common-ness/antirarity/whatever of this piece
				file.incrementPieceRarity(piece);
				
				//If appropriate, check if we need the peer's new piece
				if(useful_pieces == false && file.getPieceState(piece) == FileInfo.state_missing) {
					useful_pieces = true;
				}
				
				RUBTClient.log("Peer " + peer.getID() + " has piece " + piece);
			}
			else if(message == message_bitfield) {
				if(!first_message) {
					RUBTClient.logError("Warning: Peer " + peer.getID() + " sent bitfield at inappropriate time; disconnecting.");
					first_message = false;
					socket.tryClose();
					return;
				}
				
				if(message_length != file.getBitfieldByteSize()) {
					RUBTClient.logError("Warning: Peer " + peer.getID() + " sent invalid bitfield-length; disconnecting.");
					socket.tryClose();
					return;
				}
				
				//Store bitfield:
				peer.setBitfield(new Bitfield(file.getBitfieldBitSize()));
				try {
					peer.getBitfield().setBytes(socket.getByteArray(message_length));
				} catch (Exception e) {
					RUBTClient.logError("Warning: Peer " + peer.getID() + " sent invalid bitfield; disconnecting.");
					socket.tryClose();
					return;
				}
				
				//Increment common-ness/antirarity/whatever of this peer's pieces
				if(peer.getBitfield() != null) {
					for(int x = 0; x < peer.getBitfield().getBitSize(); x++) {
						if(peer.getBitfield().getBit(x) == 1) {
							file.incrementPieceRarity(x);
						}
					}
				}
				
				//Check if we need any of the peer's pieces.
				Bitfield useful_field = file.getUsefulBits(peer.getBitfield());
				if(useful_field == null) {
					RUBTClient.logError("Warning: Peer " + peer.getID() + " sent invalid bitfield; disconnecting.");
					socket.tryClose();
					return;
				}
				if(useful_field.nonZero()) {
					useful_pieces = true;
				}
				
				RUBTClient.log("Got bitfield from " + peer.getID());
			}
			else if(message == message_request) {
				first_message = false;
				if(message_length != 12) {
					RUBTClient.logError("Warning: Peer " + peer.getID() + " sent invalid message-length; disconnecting.");
					socket.tryClose();
					return;
				}
				
				int index = socket.getInteger();
				int begin = socket.getInteger();
				int length = socket.getInteger();
				
				//Error check:
				if(peer_choking) {
					RUBTClient.logError("Warning: Peer " + peer.getID() + " sent request while being choked; ignoring.");
					//socket.tryClose();
					return;
				}
				else if(!peer_interested) {
					RUBTClient.logError("Warning: Peer " + peer.getID() + " sent request while not interested; disconnecting.");
					socket.tryClose();
					return;
				}
				else if(index < 0 || index >= file.getBitfieldBitSize() || begin < 0 || length < 1 || length > 16384) {
					RUBTClient.logError("Warning: Peer " + peer.getID() + " sent invalid request; disconnecting.");
					socket.tryClose();
					return;
				}
				else if(file.getPieceState(index) != FileInfo.state_complete) {
					RUBTClient.logError("Warning: Peer " + peer.getID() + " sent request for incomplete piece; disconnecting.");
					socket.tryClose();
					return;
				}
				else if(begin + length > file.getPieceSize(index)) {
					RUBTClient.logError("Warning: Peer " + peer.getID() + " sent out-of-bounds request; disconnecting.");
					socket.tryClose();
					return;
				}
				else if(peer.getBitfield() != null && peer.getBitfield().getBit(index) == 1) {
					RUBTClient.logError("Warning: Peer " + peer.getID() + " requested a peice they already have; disconnecting.");
					socket.tryClose();
					return;
				}
				
				RUBTClient.log("Sending piece " + index + " (" + begin + "-" + (begin+length) +  ") to peer " + peer.getID());
				
				socket.sendInteger(length + 9);
				socket.sendByte(message_piece);
				socket.sendInteger(index);
				socket.sendInteger(begin);
				socket.sendByteArray(file.getPieceBytes(index), begin, length);
				bytes_uploaded += length;
				file.incrementUploaded(length);
			}
			else if(message == message_piece) {
				first_message = false;
				if(message_length < 8) {
					RUBTClient.logError("Warning: Peer " + peer.getID() + " sent invalid message-length; disconnecting.");
					socket.tryClose();
					return;
				}
				else if(!waiting_for_piece) {
					RUBTClient.logError("Warning: Peer " + peer.getID() + " sent unrequested piece; disconnecting.");
					socket.tryClose();
					return;
				}
				
				waiting_for_piece = false;
				
				//Get metadata:
				message_length -= 8;
				int index = socket.getInteger();
				int begin = socket.getInteger();
				
				//Error check:
				if(index != my_requested_piece) {
					RUBTClient.logError("Warning: Peer " + peer.getID() + " sent wrong piece; disconnecting.");
					socket.tryClose();
					return;
				}
				else if(begin != piece_bytes_done) {
					RUBTClient.logError("Warning: Peer " + peer.getID() + " sent wrong part of piece (" + begin + " instead of " + piece_bytes_done + "); disconnecting.");
					socket.tryClose();
					return;
				}
				else if(begin + message_length > piece_bytes.length) {
					RUBTClient.logError("Warning: Peer " + peer.getID() + " sent too much data for piece; disconnecting.");
					socket.tryClose();
					return;
				}
				
				//Get piece data:
				socket.getByteArray(piece_bytes,piece_bytes_done,message_length);
				piece_bytes_done += message_length;
				file.incrementDownloaded(message_length);
				bytes_downloaded += message_length;
				
				//Error check:
				if(!socket.connected()) {
					RUBTClient.logError("Warning: Peer " + peer.getID() + " disconnected.");
					return;
				}
			}
		}
	}
	
	/**
	 * Check if peer's socket is still connected.
	 * @return true or false
	 */
	public boolean connected() {
		if(socket == null) {
			return false;
		}
		else {
			return socket.connected();
		}
	}
	
	/**
	 * Performs handshake with peer.
	 * @return true on successful handshake, false otherwise.
	 */
	private boolean handshake() {
		socket.sendByte(19);
		socket.sendString("BitTorrent protocol");
		socket.sendInteger(0);
		socket.sendInteger(0);
		socket.sendByteArray(torrent.getInfoHash().toBytes());
		socket.sendString(this.my_id);
		
		if(socket.getByte() != 19) {
			RUBTClient.logError(peer.getID() + " sent incorrent string instead of \"BitTorrent protocol\"");
			return false;
		}
		String protocol = socket.getString(19);
		if(protocol == null || !protocol.equals("BitTorrent protocol")) {
			RUBTClient.logError(peer.getID() + " sent incorrent string instead of \"BitTorrent protocol\"");
			return false;
		}
		
		if(socket.getInteger() != 0) {
			RUBTClient.logError("Warning: reserved bytes nonzero in " + peer.getID() + "'s handshake.");
		}
		if(socket.getInteger() != 0) {
			RUBTClient.logError("Warning: reserved bytes nonzero in " + peer.getID() + "'s handshake.");
		}
		
		InfoHash peer_hash;
		try {
			peer_hash = new InfoHash(socket.getByteArray(20));
		} catch (Exception e) {
			RUBTClient.logError(peer.getID() + " sent invalid info hash");
			return false;
		}
		
		if(!torrent.getInfoHash().equals(peer_hash)) {
			RUBTClient.logError(peer.getID() + " sent wrong info hash");
			return false;
		}
		
		String peer_id = socket.getString(20);
		if(!peer.getID().equals(peer_id)) {
			if(peer.getID().length() > 0)
				RUBTClient.logError("Warning: peer's id, " + peer_id + ", is different than expected");
			peer.setID(peer_id);
		}
		
		return true;
	}

	/**
	 * Getter for the Peer object
	 * @return a Peer object
	 */
	public Peer getPeer() {
		return peer;
	}
	
	/**
	 * Force a disconnection
	 */
	public void disconnect() {
		if(socket != null) {
			socket.tryClose();
		}
	}
	
	/**
	 * Called by thread
	 * Handshakes and then downloads/uploads
	 */
	@Override
	public void run() {
		download();
	}
	
	/**
	 * Gets the number of bytes downloaded from peer
	 * @return long
	 */
	public long getDownloaded() {
		return bytes_downloaded;
	}
	
	/**
	 * Gets the number of bytes downloaded BY peer
	 * @return long
	 */
	public long getUploaded() {
		return bytes_uploaded;
	}
	
	/**
	 * Resets bytes downloaded/uploaded
	 * There is a functional delay to avoid threading issues
	 */
	public void resetByteCounts() {
		reset_bytes = true;
	}

	/**
	 * Checks whether the peer is choking
	 * @return boolean
	 */
	public boolean peerChoking() {
		return peer_choking;
	}

	/**
	 * Chokes the peer without affecting the choking counter.
	 * Used by main threads to choke worst peer
	 * Threadsafe
	 * @return true on success, or false on failure
	 */
	public boolean choke() {
		synchronized(socket_lock) {
			synchronized(peer_state_lock) {
				if(socket == null || peer_choking) {
					return false;
				}
				peer_choking = true;
			}
			
			socket.sendInteger(1);
			socket.sendByte(message_choke);
		}
		
		return true;
	}
	
	/**
	 * Unchokes the peer without affecting the choking counter.
	 * Used by main threads to unchoke random peer
	 * Threadsafe
	 * @return true on success, or false on failure
	 */
	public boolean unchoke() {
		synchronized(socket_lock) {
			synchronized(peer_state_lock) {
				if(socket == null || !peer_choking || !peer_interested) {
					return false;
				}
				peer_choking = false;
			}
			
			socket.sendInteger(1);
			socket.sendByte(message_unchoke);
		}
		
		return true;
	}
	
	/**
	 * Decrements the counter of unchoked peers
	 * Used by main threads if forcing a peer to unchoke fails.
	 * Threadsafe
	 */
	public static void decrementUnchoked() {
		synchronized(unchoked_lock) {
			unchoked_count--;
		}
	}
	
	/**
	 * Tries to decrement the counter of unchoked peers
	 * Threadsafe
	 * @return true on success, false on failure
	 */
	private static boolean incrementUnchoked() {
		synchronized(unchoked_lock) {
			if(unchoked_count < unchoked_max) {
				unchoked_count++;
				return true;
			}
		}
		return false;
	}

	/**
	 * Gets the index of the piece we are currently getting from this peer.
	 * @return -1 if there is no such piece
	 */
	public int getCurrentPiece() {
		return my_requested_piece;
	}

	/**
	 * Gets the peer's interest state
	 * @return boolean
	 */
	public boolean peerInterested() {
		return peer_interested;
	}

	/**
	 * Gets the max number of unchoked peers
	 * @return integer amount
	 */
	public static int getMaxUnchoked() {
		return unchoked_max;
	}
}
