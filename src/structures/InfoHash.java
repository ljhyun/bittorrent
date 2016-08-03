package structures;

/**
 * Store an InfoHash as both a byte array and a URL-encoded String
 * Conversion is automatic on construction to save time later.
 * @author Ari Hayes
 */
public class InfoHash {
	/**
	 * byte[] form of hash
	 */
	private byte[] bytes;
	
	/**
	 * String form of hash
	 */
	private String string;
	
	/**
	 * Creates an InfoHash from the 20-byte hash value.
	 * @param hash byte[]
	 * @throws Exception given invaid-length array
	 */
	public InfoHash(byte[] hash) throws Exception {
		if(hash.length != 20)
			throw new Exception("Invalid hash length.");
		
		bytes = hash;
		
		//Create ASCII String equivalent of hash:
		string = "";
		for(int x = 0; x < 20; x++) {
			if(bytes[x] >= 'A' && bytes[x]<'Z' || bytes[x] >= 'a' && bytes[x]<'z' || bytes[x] >= '0' && bytes[x]<'9' || bytes[x] == '.' || bytes[x] == '-' || bytes[x] == '_' || bytes[x] == '~') {
				string = string + ((char)bytes[x]);
			}
			else {
				string = string + "%" + String.format("%02x", bytes[x]).toUpperCase();
			}
		}
	}
	
	/**
	 * Creates an InfoHash from the encoded ASCII String.
	 * @param hash String
	 * @throws Exception if invalid hash is the result
	 */
	public InfoHash(String hash) throws Exception {
		string = hash;
		
		//Create byte[20] equivalent of hash:
		bytes = new byte[20];
		int string_index = 0;
		int byte_index = 0;
		while(string_index < string.length()) {
			char next_char = string.charAt(string_index++);
			if(next_char == '%') {
				String encoded_byte = string.charAt(string_index++) + "" + string.charAt(string_index++);
				bytes[byte_index++] = (byte) Integer.parseInt(encoded_byte.toUpperCase(), 16);
			}
			else {
				bytes[byte_index++] = (byte) string.charAt(string_index++);
			}
		}
		
		if(byte_index != 20 || string_index != string.length())
			throw new Exception("Invalid hash length.");
	}
	
	/**
	 * Checks if this hash and another are equivalent by byte values.
	 * @param hash to compare against
	 * @return true if equivalent, false otherwise.
	 */
	public boolean equals(InfoHash hash) {
		for(int x = 0; x < 20; x++) {
			if(bytes[x] != hash.bytes[x]) {
				return false;
			}
		}
		return true;
	}
	
	/**
	 * Public getter
	 * @return byte[]
	 */
	public byte[] toBytes() {
		return bytes;
	}
	
	/**
	 * Public getter
	 * @return String
	 */
	public String toString() {
		return string;
	}
}
