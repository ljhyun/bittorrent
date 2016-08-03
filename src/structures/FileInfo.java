package structures;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Random;

import main.RUBTClient;
/**
 * Used to store data of downloading file.
 * Thread-safe; not all getter data is guaranteed to be up-to-date, but setters are safe.
 * @author Ari & Jay
 */
public class FileInfo {
	/**
	 * An array of pieces where each piece is an array of bytes:
	 */
	private byte[][] file_bytes;
	
	/**
	 * An array for piece rarity
	 */
	private int[] piece_rarity;
	
	/**
	 * A random number generator
	 */
	Random random = new Random();
	
	/**
	 * An array indicating whether pieces are missing, downloading, or complete:
	 */
	private byte[] piece_state;
	
	/**
	 * Size of last piece
	 */
	private int last_piece_size;
	
	/**
	 * Number of complete pieces, to determine if file is complete.
	 */
	private int complete_pieces = 0;
	
	/**
	 * bitfield corresponding to pieces (1's for state_complete)
	 * Only belongs here because we're doing single-file torrents.
	 */
	private Bitfield bitfield;
	
	/**
	 * Total bytes downloaded:
	 */
	private long bytes_downloaded = 0;
	
	/**
	 * Total bytes uploaded	
	 */
	private long bytes_uploaded = 0;
	
	/**
	 * RandomAccessFile to save to disk:
	 */
	RandomAccessFile disk_file;
	
	/**
	 * RandomAccessFile for metadata:
	 */
	RandomAccessFile metadata;
	
	/**
	 * Mutex for piece states
	 */
	private Object piece_state_lock = new Object();
	
	/**
	 * Mutex for uploaded/downloaded byte counts
	 */
	private Object byte_count_lock = new Object();
	
	/**
	 * Mutex for piece rarity
	 */
	private Object rarity_lock = new Object();
	
	/**
	 * Constant for piece state:
	 */
	public static final byte state_missing = 0;
	
	/**
	 * Constant for piece state:
	 */
	public static final byte state_downloading = 1;
	
	/**
	 * Constant for piece state:
	 */
	public static final byte state_complete = 2;
	
	/**
	 * Only constructor for FileInfo
	 * Sets up the object, loading data and metadata from disk if possible
	 * @param file_size The size of the file we are downloading/uploading (in bytes)
	 * @param piece_size The size of each piece of the file (in bytes)
	 * @param path The location to save/load the file.
	 * @throws Exception on failure to read/write to disk
	 */
	public FileInfo(int file_size, int piece_size, String path) throws Exception {
		this.file_bytes = new byte[1 + (file_size-1)/piece_size][piece_size];
		this.piece_state = new byte[file_bytes.length];
		this.last_piece_size = file_size % piece_size;
		this.piece_rarity = new int[file_bytes.length];
		if(last_piece_size == 0)
			last_piece_size = piece_size;
		bitfield = new Bitfield(this.file_bytes.length);
		
		for(int x = 0; x < file_bytes.length; x++) {
			piece_state[x] = state_missing;
		}
		
		//Disk file & metadata file:
		disk_file = new RandomAccessFile(path,"rw");
		metadata = new RandomAccessFile(path + ".meta","rw");
		if(disk_file.length() == 0) {//new file
			disk_file.setLength(last_piece_size + file_bytes[0].length * (file_bytes.length-1));
			metadata.writeInt(0);
			metadata.writeInt(0);
			metadata.write(bitfield.getBytes());
		}
		else {//already existed
			byte[] temp = new byte[bitfield.getByteSize()];
			bytes_downloaded = metadata.readLong();
			bytes_uploaded = metadata.readLong();
			metadata.read(temp, 0, temp.length);
			bitfield.setBytes(temp);
			
			for(int x = 0; x < bitfield.getBitSize(); x++) {
				if(bitfield.getBit(x) == 1) {//remember complete pieces
					piece_state[x] = state_complete;
					disk_file.seek(x * file_bytes[0].length);
					disk_file.read(file_bytes[x], 0, getPieceSize(x));
					complete_pieces++;
				}
			}
		}
	}
	
	/**
	 * Set incomplete piece's state to downloading
	 * @param piece The number identifying the piece
	 * @return false if the piece was not in the missing state.
	 */
	public boolean downloadingPiece(int piece) {
		synchronized(piece_state_lock) {
			if(piece >= 0 && piece_state[piece] == state_missing) {
				piece_state[piece] = state_downloading;
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Set downloading piece's state to missing
	 * Assumes that the piece's state = state_downloading
	 * @param piece The number identifying the piece
	 */
	public void cancelPiece(int piece) {
		if(piece_state[piece] != state_complete) {
			synchronized(piece_state_lock) {
				piece_state[piece] = state_missing;
			}
		}
	}
	
	/**
	 * Store completely downloaded piece
	 * Assumes that the piece's state = state_downloading
	 * @param piece The number identifying the piece
	 * @param data The piece's bytes
	 * @throws Exception for invalid byte[]
	 */
	public void completePiece(int piece, byte[] data) throws Exception {
		if(data.length != getPieceSize(piece)) {
			RUBTClient.logError("Invalid byte array for piece " + piece + ".");
			throw new Exception("Invalid piece.");
		}
		
		//Write to memory
		for(int x = 0; x < data.length; x++) {
			file_bytes[piece][x] = data[x];
		}
		
		//Set piece state
		synchronized(piece_state_lock) {
			piece_state[piece] = state_complete;
			bitfield.setBit(piece,true);
			complete_pieces++;
		}
		
		//Write to disk
		disk_file.seek(piece*getPieceSize(0));
		disk_file.write(file_bytes[piece],0,getPieceSize(piece));
		metadata.seek(16 + piece/8);
		metadata.write(bitfield.getBytes()[piece/8]);
		metadata.seek(0);
		metadata.writeLong(bytes_downloaded);
		metadata.writeLong(bytes_uploaded);
	}
	
	/**
	 * Find a missing piece we can download
	 * @param peer_bitfield The peer's bitfield
	 * @return The missing piece's id, or -1.
	 */
	public int getMissingPiece(Bitfield peer_bitfield) {
		int min = Integer.MAX_VALUE;
		ArrayList<Integer> duplicates = new ArrayList<Integer>();
		
		for(int i=0;i<piece_rarity.length;i++){
			if(peer_bitfield.getBit(i) == 1 && piece_state[i] == state_missing) {
				if(piece_rarity[i] < min){
					duplicates.clear();
					duplicates.add(i);
				}
				else if(piece_rarity[i] == min)
				{
					duplicates.add(i);
				}
			}
		}
		
		if(duplicates.isEmpty()) {
			return -1;
		}
		
		return duplicates.get(random.nextInt(duplicates.size()));
	}
	
	/**
	 * Finds useful bits in peer's bitfield.
	 * Equivalent to peer's bitfield AND the COMPLEMENT of our bitfield.
	 * @param peer_bitfield The peer's bitfield.
	 * @return a new bitfield, or null for invalid input.
	 */
	public Bitfield getUsefulBits(Bitfield peer_bitfield) {
		return bitfield.getUsefulBits(peer_bitfield);
	}
	
	/**
	 * Getter for our bitfield's bit-size
	 * @return integer number of bytes
	 */
	public int getBitfieldBitSize() {
		return bitfield.getBitSize();
	}
	
	/**
	 * Getter for our bitfield's byte-size
	 * @return integer number of bytes
	 */
	public int getBitfieldByteSize() {
		return bitfield.getByteSize();
	}
	
	/**
	 * Getter for number of complete pieces.
	 * @return integer
	 */
	public int getCompleteCount() {
		return complete_pieces;
	}
	
	/**
	 * Whether or not file is complete
	 * @return true or false
	 */
	public boolean complete() {
		return (complete_pieces == file_bytes.length);
	}
	
	/**
	 * Saves file to disk.
	 * @param path Location to save file.
	 * @throws IOException on failure to close streams
	 */
	public void saveFile(String path) throws IOException {
		disk_file.close();
		metadata.close();
	}
	
	/**
	 * Returns the state of a piece
	 * @param index The index of the piece whose state we want
	 * @return a byte
	 */
	public byte getPieceState(int index) {
		return piece_state[index];
	}

	/**
	 * Gets the byte-size of a piece.
	 * @param index The index of the piece whose size we want
	 * @return an integer value
	 */
	public int getPieceSize(int index) {
		if(index == file_bytes.length - 1) {
			return last_piece_size;
		}
		else {
			return file_bytes[0].length;
		}
	}

	/**
	 * Gets the byte array for a piece
	 * @param index The index of the piece whose bytes we want
	 * @return a byte array
	 */
	public byte[] getPieceBytes(int index) {
		return file_bytes[index];
	}
	
	/**
	 * Increment Piece rarity
	 * @param index The index of the piece whose rarity we want to increment.
	 */
	public void incrementPieceRarity(int index){
		synchronized(rarity_lock ){
			piece_rarity[index]++;
		}
	}
	
	/**
	 * Decrement Piece Rarity
	 * @param index The piece whose rarity we want to decrement
	 */
	public void decrementPieceRarity(int index){
		synchronized(rarity_lock){
			piece_rarity[index]--;
		}
	}
	
	/**
	 * Adds to the count for downloaded bytes:
	 * @param amount The amount by which to increment downloaded bytes
	 */
	public void incrementDownloaded(int amount) {
		synchronized(byte_count_lock) {
			bytes_downloaded += amount;
		}
	}
	
	/**
	 * Adds to the count for uploaded bytes:
	 * @param amount The amount by which to decrement downloaded bytes
	 */
	public void incrementUploaded(int amount) {
		synchronized(byte_count_lock) {
			bytes_uploaded += amount;
		}
	}
	
	/**
	 * Gets the bytes downloaded.
	 * @return an integer
	 */
	public long getBytesDownloaded() {
		return bytes_downloaded;
	}
	
	/**
	 * Gets the bytes uploaded.
	 * @return an int
	 */
	public long getBytesUploaded() {
		return bytes_uploaded;
	}

	/**
	 * Getter for complete_pieces
	 * @return an int
	 */
	public int getPiecesCompleted() {
		return complete_pieces;
	}
}
