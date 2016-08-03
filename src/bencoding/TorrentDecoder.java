package bencoding;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

import main.RUBTClient;

import structures.InfoHash;

/**
 * TorrentDecoder class
 * Parses and extracts data from a torrent file with a single tracker and single file.
 * @author Ari Hayes
 */
public class TorrentDecoder {
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
	 * A string holding the announce url.
	 */
	private String announce_url = null;
	
	/**
	 * Length of the file.
	 */
	private int total_length = -1;
	
	/**
	 * The name the file will be saved as.
	 */
	private String file_name = null;
	
	/**
	 * The info_hash for the torrent file.
	 */
	private InfoHash info_hash = null;
	
	/**
	 * The length of each piece.
	 */
	private int piece_length = -1;
	
	/**
	 * Array of 20-byte hashes for the pieces.
	 * Has dimensions number_of_hashes x 20
	 */
	private byte[][] piece_hashes = null;
	
	/**
	 * Used to store info_bytes in order to calculate info_hash
	 */
	private String info_bytes = "";
	
	/**
	 * Boolean for whether or not the info_bytes should be written to yet.
	 */
	private boolean info_found = false;
	
	/**
	 * Creates a TorrentDecoder object, and parses the specified file to get metadata.
	 * @param file A String identifying the torrent file on disc.
	 * @throws IOException on unexpected IO error.
	 */
	public TorrentDecoder(String file) throws IOException {
		try {
			stream = new FileInputStream(file);
		} catch (FileNotFoundException e) {
			RUBTClient.logError("File " + file  + " not found.");
			return;
		}
		
		//Parse file:
		int b = readByte();
		if(b == 'd') {
			if(!parseDictionary(id_root)) {
				return;
			}
			
			//Error checking:
			b = readByte();
			if(b != -1) {
				RUBTClient.logError("Found " + (char)b + ", expected end of file.");
			}
			else if(announce_url == null) {
				RUBTClient.logError("Announce url not found; torrent may be corrupted.");
			}
			else if(total_length < 1) {
				RUBTClient.logError("Valid file length not found; torrent may be corrupted.");
			}
			else if(file_name == null || file_name.length() < 1) {
				RUBTClient.logError("Valid file name not found; torrent may be corrupted.");
			}
			else if(piece_length < 1) {
				RUBTClient.logError("Valid piece length not found; torrent may be corrupted.");
			}
			else if(piece_hashes == null) {
				RUBTClient.logError("Valid hashes not found; torrent may be corrupted.");
			}
			else {
				valid = true;
			}
			
			//Calculate info_hash:
			try {
				MessageDigest md = MessageDigest.getInstance("SHA-1");
				for(int x = 0; x < info_bytes.length(); x++) {
					md.update((byte)info_bytes.charAt(x));
				}
				info_hash = new InfoHash(md.digest());
			} catch (Exception e) {
				RUBTClient.logError("Error computing info hash.");
				valid = false;
			}
		}
		else {
			RUBTClient.logError("Expected top-level dictionary; torrent is corrupt.");
		}
	}

	/**
	 * Called by constructor, usually indirectly, to help parse torrent file.
	 * @param dictionary_id Indicates whether in root dictionary, or info dictionary, etc.
	 * @return true if syntactically correct dictionary is parsed successfully, false on failure.
	 * @throws IOException on failure reading stream
	 */
	boolean parseDictionary(int dictionary_id) throws IOException {
		/**
		 * The last key parsed; used to make sure lexicographical order is enforced.
		 */
		String last_key = null;
		
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
				if(dictionary_id == id_info) {
					this.info_found = false;
					//this.info_bytes = this.info_bytes.substring(0, this.info_bytes.length() - 1);
				}
				return true;
			}
			else if(b >= '0' && b <= '9') {//string length
				String key = parseString(b-'0');
				
				//Check if lexicographical order is correct:
				if(last_key != null) {
					int comp = last_key.compareTo(key);
					if(comp > 0) {
						RUBTClient.logError("Dictionary keys not in lexicographical order; torrent is corrupt.");
						return false;
					}
					else if(comp == 0) {
						RUBTClient.logError("Duplicate dictionary key found; torrent is corrupt.");
						return false;
					}
				}
				
				//Set last_key to current key:
				last_key = key;
				
				boolean value_parsed = false;
				
				//Read through key's corresponding value:
				if(dictionary_id == id_root) {
					if(key.equals("announce")) {
						value_parsed = true;
						this.announce_url = parseString(0);
						if(announce_url == null) {//not a String
							RUBTClient.logError("Unable to parse announce url; torrent may be corrupt.");
							return false;
						}
					}
					else if(key.equals("info")) {
						value_parsed = true;
						info_found = true;
						b = readByte();
						if(b == 'd') {//dictionary
							if(!parseDictionary(id_info)) {
								return false;
							}
						}
						else {//not a dictionary
							RUBTClient.logError("Unable to parse info dictionary; torrent may be corrupt.");
							return false;
						}
					}
				}
				else if(dictionary_id == id_info) {
					if(key.equals("length")) {
						value_parsed = true;
						Integer i = parseInteger();
						if(i == null) {
							RUBTClient.logError("Unable to parse length; torrent may be corrupt.");
							return false;
						}
						else {
							this.total_length = i;
						}
					}
					else if(key.equals("name")) {
						value_parsed = true;
						this.file_name = parseString(0);
						if(this.file_name == null) {
							RUBTClient.logError("Unable to parse name; torrent may be corrupt.");
							return false;
						}
					}
					else if(key.equals("piece length")) {
						value_parsed = true;
						Integer i = parseInteger();
						if(i == null) {
							RUBTClient.logError("Unable to parse piece_length; torrent may be corrupt.");
							return false;
						}
						else {
							this.piece_length = i;
						}
					}
					else if(key.equals("pieces")) {
						value_parsed = true;
						if(!parseHashes()){
							return false;
						}
					}
				}
				
				if(!value_parsed) {//If the key is not one we want, parse anyway:
					if(parseValue() == null) {//nonsensical value
						return false;
					}
				}
				
				//Read next byte:
				b = readByte();
			}
			else {//unexpected
				RUBTClient.logError("Unexpected byte '" + (char)b +  "' in dictionary; torrent may be corrupt.");
				return false;
			}
		}
		
		//End of file:
		RUBTClient.logError("End of file reached; expected end of dictionary.");
		return false;
	}
	
	/**
	 * Parses the SHA-1 hashes from the file stream.
	 * @return true on success, false on failure.
	 * @throws IOException 
	 */
	private boolean parseHashes() throws IOException {
		//Get total hash length:
		int len = parseInteger(':',0);
		
		if(len % 20 != 0) {
			RUBTClient.logError("Hash length not a multiple of 20; torrent may be corrupt.");
			return false;
		}
		
		//Read hashes:
		int b;
		int num_hashes = len / 20;
		this.piece_hashes = new byte[num_hashes][20];
		for(int x = 0; x < num_hashes; x++) {
			for(int y = 0; y < 20; y++) {
				b = readByte();
				if(b == -1) {//end of file
					this.piece_hashes = null;
					RUBTClient.logError("End of file reached; expected SHA-1 hash.");
					return false;
				}
				this.piece_hashes[x][y] = (byte)b;
			}
		}
		
		return true;
	}
	
	/**
	 * Reads a byte from the stream, adding it to info_bytes if necessary.
	 * @return The next byte as an int.
	 * @throws IOException on failure reading stream
	 */
	int readByte() throws IOException {
		int b = this.stream.read();
		if(info_found && b != -1) {
			//TODO: don't read into a String
			info_bytes += (char)b;
		}
		return b;
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
	 * @throws IOException on failure reading stream
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
	 * Whether or not the encoded stream was successfully parsed.
	 * @return valid
	 */
	public boolean valid() {
		return valid;
	}
	
	/**
	 * Gets the announce url
	 * @return String value or null
	 */
	public String getAnnounceURL() {
		return announce_url;
	}
	
	/**
	 * Gets the file length
	 * @return integer value or null
	 */
	public int getFileLength() {
		return total_length;
	}
	
	/**
	 * Gets the recommended file name
	 * @return String value or null
	 */
	public String getFileName() {
		return file_name;
	}
	
	/**
	 * Gets the info hash
	 * @return InfoHash object or null
	 */
	public InfoHash getInfoHash() {
		return info_hash;
	}
	
	/**
	 * Gets the piece length
	 * @return integer value or null
	 */
	public int getPieceLength() {
		return piece_length;
	}
	
	/**
	 * Gets a piece hash
	 * @param piece index of the piece whose hash we want
	 * @return byte array containing hash value
	 */
	public byte[] getHash(int piece) {
		if(piece >= 0 && piece < piece_hashes.length)
			return piece_hashes[piece];
		else
			return null;
	}
	
	/**
	 * Gets a byte from a piece hash
	 * @param piece index of the piece whose hash we want
	 * @param bite index of the byte we want within the piece
	 * @return a byte
	 */
	public byte getHashByte(int piece, int bite) {
		return piece_hashes[piece][bite];
	}
	
	/**
	 * Gets the number of pieces
	 * @return integer value
	 */
	public int getHashCount() {
		return piece_hashes.length;
	}
}