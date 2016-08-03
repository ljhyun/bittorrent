package bencoding;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import main.RUBTClient;

import structures.Peer;

/**
 * A decoder to torrent response streams.
 * @author Ari Hayes
 */
public class ResponseDecoder {
	/**
	 * The response stream
	 */
	InputStream stream;
	
	/**
	 * Whether or not the file/stream was correctly parsed.
	 */
	boolean valid = false;
	
	/**
	 * Constant used during parsing.
	 */
	final int id_root = 0;
	
	/**
	 * Constant used during parsing.
	 */
	final int id_info = 1;
	
	/**
	 * Constant used during parsing.
	 */
	final int id_ignore = 2;
	
	/**
	 * Constant used during parsing.
	 */
	final int id_peer = 3;
	
	/**
	 * Failure message read from response, or null
	 */
	private String failure = null;
	
	/**
	 * Warning message read from response or null
	 */
	private String warning = null;
	
	/**
	 * Interval value read from response, or null
	 */
	private Integer interval = null;
	
	/**
	 * Minimum Interval value read from response, or 0
	 */
	private Integer min_interval = 0;
	
	/**
	 * trackerid read from response or null
	 */
	private String tracker_id = null;
	
	/**
	 * Value of complete read from stream or null
	 */
	private Integer complete = null;
	
	/**
	 * Value of incomplete read from stream or null
	 */
	private Integer incomplete = null;
	
	/**
	 * Value of download read from stream, or null
	 */
	private Integer downloaded = null;
	
	/**
	 * List of peer IPs and IDs read from response, or null
	 */
	private ArrayList<Peer> peers = null;
	
	/**
	 * Creates a ResponseDecoder object, and parses torrent response.
	 * @param is The input stream for the response
	 * @throws IOException on failure reading response stream
	 */
	public ResponseDecoder(InputStream is) throws IOException {
		this.stream = is;
		
		//Parse file:
		int b = readByte();
		if(b == 'd') {
			if(!parseDictionary(id_root)) {
				return;
			}
			
			//Error checking:
			b = readByte();
			if(b != -1) {
				RUBTClient.logError("Found " + (char)b + ", expected end of response.");
			}
			else if(failure != null) {
				RUBTClient.logError("Failure: " + failure);
			}
			else if(interval == null) {
				RUBTClient.logError("Interval not found; response may be corrupted.");
			}
			/*else if(tracker_id == null) {
				RUBTClient.logError("Tracker id not found; response may be corrupted.");
			}
			else if(complete == null) {
				RUBTClient.logError("Complete value not found; response may be corrupted.");
			}
			else if(incomplete == null) {
				RUBTClient.logError("Incomplete value not found; response may be corrupted.");
			}
			else if(downloaded == null) {
				RUBTClient.logError("Downloaded value not found; response may be corrupted.");
			}*/
			else if(peers == null) {
				RUBTClient.logError("Peers not found; response may be corrupted.");
			}
			else {
				valid = true;
			}
			
			if(warning != null) {
				RUBTClient.logError("Warning: " + warning);
			}
		}
		else {
			RUBTClient.logError("Expected dictionary; response is corrupt.");
		}
	}

	/**
	 * Called by constructor to help parse response.
	 * @param dictionary_id Indicates whether in root dictionary, etc.
	 * @return true if syntactically correct dictionary is parsed successfully, false on failure.
	 * @throws IOException on failure to read a byte
	 */
	boolean parseDictionary(int dictionary_id) throws IOException {
		/**
		 * The last key parsed; used to make sure lexicographical order is enforced.
		 */
		String last_key = null;
		
		/**
		 * Peer data
		 */
		String id = null;
		String ip = null;
		Integer port = null;
		
		//Parse:
		int b = readByte();
		while(b != -1) {
			if(b == 'i') {//integer prefix
				RUBTClient.logError("Found integer; expected ascii dictionary key.");
				return false;
			}
			else if(b == 'l') {//list prefix
				RUBTClient.logError("Found list; expected ascii dictionary key.");
				return false;
			}
			else if(b == 'd') {//dictionary prefix
				RUBTClient.logError("Found dictionary; expected ascii dictionary key.");
				return false;
			}
			else if(b == 'e') {//end of dictionary
				if(dictionary_id == id_peer) {
					if(id == null || ip == null || port == null) {
						RUBTClient.logError("Missing peer data; response may be corrupt.");
						return false;
					}
					peers.add(new Peer(id,ip,port));
				}
				return true;
			}
			else if(b >= '0' && b <= '9') {//string length
				String key = parseString(b-'0');
				
				//Check if lexicographical order is correct:
				if(last_key != null) {
					int comp = last_key.compareTo(key);
					if(comp > 0) {
						RUBTClient.logError("Dictionary keys not in lexicographical order; response is corrupt.");
						return false;
					}
					else if(comp == 0) {
						RUBTClient.logError("Duplicate dictionary key found; response is corrupt.");
						return false;
					}
				}
				
				//Set last_key to current key:
				last_key = key;
				
				boolean value_parsed = false;
				
				//Read through key's corresponding value:
				if(dictionary_id == id_root) {
					if(key.equals("peers")) {
						value_parsed = true;
						peers = new ArrayList<Peer>();
						b = readByte();
						if(b == 'l') {//list
							if(!parsePeerList()) {
								return false;
							}
						}
						else {//not a list
							RUBTClient.logError("Unable to parse peer list; response may be corrupt.");
							return false;
						}
					}
					else if(key.equals("failure reason")) {
						value_parsed = true;
						failure = parseString(0);
						if(failure == null) {
							return false;
						}
						else {//failure message parsed
							int b2 = readByte();
							int b3 = readByte();
							if(b2 == 'e' && b3 == -1) {
								return true;
							}
							else {
								RUBTClient.logError("Expected end of response after failure message; respomse may be corrupt.");
								return false;
							}
						}
					}
					else if(key.equals("warning message")) {
						value_parsed = true;
						warning = parseString(0);
						if(warning == null) {
							return false;
						}			
					}
					else if(key.equals("interval")) {
						value_parsed = true;
						interval = parseInteger();
						if(interval == null) {
							return false;
						}
					}
					else if(key.equals("min interval")) {
						value_parsed = true;
						min_interval = parseInteger();
						if(min_interval == null) {
							return false;
						}
					}
					else if(key.equals("tracker id")) {
						value_parsed = true;
						tracker_id = parseString(0);
						if(tracker_id == null) {
							return false;
						}
					}
					else if(key.equals("complete")) {
						value_parsed = true;
						complete = parseInteger();
						if(complete == null) {
							return false;
						}					
					}
					else if(key.equals("incomplete")) {
						value_parsed = true;
						incomplete = parseInteger();
						if(incomplete == null) {
							return false;
						}
					}
					else if(key.equals("downloaded")) {
						value_parsed = true;
						downloaded = parseInteger();
						if(downloaded == null) {
							return false;
						}					
					}
				}
				else if(dictionary_id == id_peer) {
					if(key.equals("peer id")) {
						value_parsed = true;
						id = parseString(0);
						if(id == null) {
							RUBTClient.logError("Unable to parse peer id; response may be corrupt.");
							return false;
						}
					}
					else if(key.equals("ip")) {
						value_parsed = true;
						ip = parseString(0);
						if(ip == null) {
							RUBTClient.logError("Unable to parse ip; response may be corrupt.");
							return false;
						}
					}
					else if(key.equals("port")) {
						value_parsed = true;
						port = parseInteger();
						if(port == null) {
							RUBTClient.logError("Unable to parse port; response may be corrupt.");
							return false;
						}
					}
				}
				
				if(!value_parsed) {//unexpected dictionary key
					RUBTClient.logError("Warning: unexpected dictionary key '" + key + "' in tracker response.");
					parseValue();
				}
				
				//Read next byte:
				b = readByte();
			}
			else {//unexpected
				RUBTClient.logError("Unexpected byte '" + (char)b +  "' in dictionary; response may be corrupt.");
				return false;
			}
		}
		
		//End of file:
		RUBTClient.logError("End of response reached; expected end of dictionary.");
		return false;
	}
	
	/**
	 * Parses a bencoded list of peers.
	 * @return true if syntactically correct list is parsed successfully, false on failure.
	 * @throws IOException 
	 */
	private boolean parsePeerList() throws IOException {
		int b = readByte();
		while(b != -1) {
			if(b == 'e') {//end of list
				return true;
			}
			else {
				if(b == 'd') {
					if(!parseDictionary(id_peer)) {
						return false;
					}
				}
				else {
					RUBTClient.logError("Peer list is in wrong format.");
					return false;
				}
				
				//Read next byte:
				b = readByte();
			}
		}
		
		//End of file:
		RUBTClient.logError("End of file reached; expected end of list.");
		return false;
	}

	/**
	 * Parse a value corresponding to some dictionary key.
	 * @return null on failure, an object otherwise.
	 * @throws IOException on failure reading stream
	 */
	Object parseValue() throws IOException {
		int b = readByte();
		return parseValue(b);
	}
	
	/**
	 * Parse a value.
	 * @param b The last byte read
	 * @return null on failure, an object otherwise.
	 * @throws IOException on failure reading stream
	 */
	Object parseValue(int b) throws IOException {
		if(b == 'i') {//integer prefix
			return parseInteger('e',0);
		}
		else if(b == 'l') {//list prefix
			if(parseList()) {
				return 1;
			}
			else {
				return null;
			}
		}
		else if(b == 'd') {//dictionary prefix
			if(parseDictionary(id_ignore)) {
				return 1;
			}
			else {
				return null;
			}
		}
		else if(b >= '0' && b <= '9') {
			return parseString(b-'0');
		}
		else {//unexpected
			RUBTClient.logError("Unexpected byte '" + (char)b +  "' in dictionary value; torrent may be corrupt.");
			return null;
		}
	}
	
	/**
	 * Parses a bencoded list.
	 * @return true if syntactically correct list is parsed successfully, false on failure.
	 * @throws IOException 
	 */
	private boolean parseList() throws IOException {
		int b = readByte();
		while(b != -1) {
			if(b == 'e') {//end of list
				return true;
			}
			else {
				return (parseValue(b) != null);
			}
		}
		
		//End of file:
		RUBTClient.logError("End of file reached; expected end of list.");
		return false;
	}
	
	/**
	 * Parses an ASCII string at current offset of the stream.
	 * @param first_digit The first digit of the string-length if it was already read from stream.
	 * @return The String on success, null on failure.
	 * @throws IOException on failure reading stream
	 */
	String parseString(int first_digit) throws IOException {
		int b;
		Integer len = parseInteger(':',first_digit);
		if(len == null) {
			return null;
		}
		
		//Read string of length len:
		String str = "";
		for(int x = 0; x < len; x++) {
			b = readByte();
			if(b == -1) {//end of file
				RUBTClient.logError("End of file reached; expected end of string-length.");
				return null;
			}
			else {//next character
				str = str + (char)b;
			}
		}
		
		return str;
	}
	
	/**
	 * Parses an integer at current offset of the stream.
	 * @param end_byte The byte signaling the end of the integer, either ':' or 'e'
	 * @param first_digit The first digit of the integer if it was already read from stream.
	 * @return The Integer object on success, null on failure.
	 * @throws IOException on failure reading stream
	 */
	Integer parseInteger(char end_byte, int first_digit) throws IOException {
		Integer i = first_digit;
		int b = readByte();
		while(b != end_byte) {
			if(b == -1) {//end of file
				RUBTClient.logError("End of file reached; expected end of string-length.");
				return null;
			}
			else if(b >= '0' && b <= '9') {//digit
				i = 10*i + (b-'0');
				b = readByte();
			}
			else {//unexpected
				RUBTClient.logError("Non-digit " + (char)b + " in integer; torrent may be corrupt.");
				return null;
			}
		}
		return i;
	}
	
	/**
	 * Parses an integer at current offset of the stream.
	 * @return The Integer object on success, null on failure.
	 * @throws IOException on failure reading stream
	 */
	Integer parseInteger() throws IOException {
		if(readByte() == 'i') {
			return parseInteger('e',0);
		}
		else {
			return null;
		}
	}
	
	/**
	 * Reads a byte from a stream
	 * @return The next byte as an int.
	 * @throws IOException on failure reading stream
	 */
	int readByte() throws IOException {
		return stream.read();
	}
	
	/**
	 * Whether or not the encoded stream was successfully parsed.
	 * @return valid
	 */
	public boolean valid() {
		return valid;
	}
	
	/**
	 * Gets the interval value
	 * @return integer value or null
	 */
	public Integer getInterval() {
		return interval;
	}
	
	/**
	 * Get the min interval value
	 * @return integer value or null
	 */
	public Integer getMinInterval() {
		return min_interval;
	}
	
	/**
	 * Gets the tracker-id
	 * @return String value or null
	 */
	public String getTrackerID() {
		return tracker_id;
	}
	
	/**
	 * Gets the complete value
	 * @return integer value or null
	 */
	public Integer getComplete() {
		return complete;
	}
	
	/**
	 * Gets the incomplete value
	 * @return integer value or null
	 */
	public Integer getIncomplete() {
		return incomplete;
	}
	
	/**
	 * Gets the downloaded value
	 * @return integer value or null
	 */
	public Integer getDownloaded() {
		return downloaded;
	}
	
	/**
	 * Gets the peer list
	 * @return ArrayList of Peer objects, or null if decoding failed somehow
	 */
	public ArrayList<Peer> getPeers() {
		return peers;
	}
}
