package structures;

/**
 * An array of bytes, used to keep track of which pieces a peer does or does not have.
 * @author Ari Hayes
 */
public class Bitfield {
	/**
	 * The actual bits
	 */
	private byte[] field;
	
	/**
	 * Number of bits
	 */
	private int bit_size;
	
	/**
	 * Constructor
	 * @param num_bits The number of bits in the field.
	 */
	public Bitfield(int num_bits) {
		field = new byte[1 + (num_bits-1)/8];
		bit_size = num_bits;
		for(int x = 0; x < field.length; x++) {
			field[x] = 0;
		}
	}
	
	/**
	 * Sets a bit to one or zero.
	 * @param bit the bit's index in the field.
	 * @param one True for setting to one, false for setting to zero.
	 */
	public void setBit(int bit, boolean one) {
		if(one) {
			field[bit/8] |= (byte)1 << (7 - (bit % 8));
		}
		else {
			field[bit/8] &= ~(byte)1 << (7 - (bit % 8));
		}
	}
	
	/**
	 * Gets a bit
	 * @param bit the bit's index in the field.
	 * @return a byte equal to either 1 or 0.
	 */
	public byte getBit(int bit) {
		if((field[bit/8] & 1 << (7 - (bit % 8))) == 0) {
			return 0;
		}
		else {
			return 1;
		}
	}
	
	/**
	 * Finds useful bits in an uploader's bitfield.
	 * Equivalent to uploader's bitfield AND the COMPLEMENT of this bitfield.
	 * @param uploader_bitfield The uploader's bitfield.
	 * @return a new bitfield, or null for invalid input.
	 */
	public Bitfield getUsefulBits(Bitfield uploader_bitfield) {
		if(field.length != uploader_bitfield.field.length) {
			return null;
		}
		
		//Make sure extra bits are 0:
		if(bit_size % 8 != 0) {
			for(int x = bit_size; x % 8 != 0; x++) {
				if(uploader_bitfield.getBit(x) != 0) {
					return null;
				}
			}
		}
		
		//Construct useful Bitfield
		Bitfield useful = new Bitfield(bit_size);
		for(int x = 0; x < useful.field.length; x++) {
			useful.field[x] = (byte) (uploader_bitfield.field[x] & ~(field[x]));
		}
		//Set extra bits to 0:
		if(bit_size % 8 != 0) {
			for(int x = bit_size; x % 8 != 0; x++) {
				useful.setBit(x,false);
			}
		}
		
		return useful;
	}
	
	/**
	 * Returns a String representation of the bitfield.
	 * Mostly for debugging purposes.
	 * @return a String of 1's and 0's
	 */
	public String toString() {
		String str = "";
		for(int x = 0; x < bit_size; x++) {
			str += getBit(x);
		}
		return str;
	}

	/**
	 * Sets the field to the given byte[]'s values.
	 * @param b The bytes
	 * @throws Exception on invalid-sized array
	 */
	public void setBytes(byte[] b) throws Exception {
		if(b.length > field.length) {
			throw new Exception();
		}
		for(int x = 0; x < b.length; x++) {
			field[x] = b[x];
		}
	}

	/**
	 * Getter for byte-size
	 * @return integer
	 */
	public int getByteSize() {
		return field.length;
	}
	
	/**
	 * Getter for bit-size
	 * @return integer
	 */
	public int getBitSize() {
		return bit_size;
	}

	/**
	 * Checks if there are any nonzero bits in the field.
	 * @return a boolean
	 */
	public boolean nonZero() {
		for(int x = 0; x < field.length; x++) {
			if(field[x] != 0)
				return true;
		}
		return false;
	}

	/**
	 * Getter for the field's data
	 * @return byte[]
	 */
	public byte[] getBytes() {
		return field;
	}
}
