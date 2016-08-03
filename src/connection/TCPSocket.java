package connection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;

/**
 * A wrapper for Socket with some extra send and get methods.
 * @author Ari Hayes
 */
class TCPSocket {
	/**
	 * The connected socket.
	 */
	private Socket socket;

	/**
	 * The socket's output stream for sending data.
	 */
	private OutputStream ostream;

	/**
	 * The socket's input stream for receiving data.
	 */
	private InputStream istream;

	/**
	 * Whether or not the socket is connected to a server.
	 */
	private boolean connected;

	/**
	 * Constructor for TCPSocket
	 * @param mySocket The socket around which the TCPSocket is wrapped.
	 * @throws SocketException On socket or stream failure
	 */
	public TCPSocket(Socket mySocket) throws SocketException {
		socket = mySocket;
		socket.setSoTimeout(2*60*1000);//timeout 2 minutes for reads
		try {
			ostream = socket.getOutputStream();
			istream = socket.getInputStream();
			connected = true;
		} catch (IOException e) {
			tryClose();
		}
	}

	/**
	 * Constructor for TCPSocket
	 * @param ip The ip address to connect to.
	 * @param port The port to connect to.
	 * @throws Exception on stream or socket failure.
	 */
	public TCPSocket(String ip, int port) throws Exception {
		socket = new Socket(ip,port);
		socket.setSoTimeout(2*60*1000);//timeout 2 minutes for reads
		ostream = socket.getOutputStream();
		istream = socket.getInputStream();
		connected = true;
	}

	/**
	 * Sends a byte through the socket.
	 * @param b The byte value to send
	 */
	public void sendByte(int b) {
		if (!connected()) {
			return;
		}

		try {
			ostream.write(b);
		} catch (IOException ex) {
			tryClose();
		}
	}
	
	/**
	 * Sends a byte[] through the socket.
	 * @param bytes The byte array to send
	 */
	public void sendByteArray(byte[] bytes) {
		if (!connected()) {
			return;
		}

		try {
			ostream.write(bytes);
		} catch (IOException ex) {
			tryClose();
		}
	}
	
	/**
	 * Sends a byte[] through the socket.
	 * @param bytes The byte array to send some of
	 * @param offset The index of the first byte to send
	 * @param length The number of bytes to send
	 */
	public void sendByteArray(byte[] bytes, int offset, int length) {
		if (!connected()) {
			return;
		}

		try {
			ostream.write(bytes,offset,length);
		} catch (IOException ex) {
			tryClose();
		}
	}

	/**
	 * Sends an integer through the socket in big-endian.
	 * @param i The integer value to send
	 */
	public void sendInteger(int i) {
		if (!connected()) {
			return;
		}

		try {
			ostream.write(i >> 24 & 0xff);
			ostream.write(i >> 16 & 0xff);
			ostream.write(i >> 8 & 0xff);
			ostream.write(i & 0xff);
		} catch (IOException ex) {
			tryClose();
		}
	}

	/**
	 * Sends a String through the socket in ASCII bytes.
	 * @param str The String to send
	 */
	public void sendString(String str) {
		if (!connected()) {
			return;
		}

		try {
			for(int x = 0; x < str.length(); x++) {
				ostream.write((byte)str.charAt(x));
			}
		} catch (IOException ex) {
			tryClose();
		}
	}

	/**
	 * Gets a byte from the socket.
	 * @return -1 on failure.
	 */
	public int getByte() {
		if (!connected()) {
			return - 1;
		}

		try {
			return istream.read();
		} catch (IOException ex) {
			tryClose();
			return -1;
		}
	}
	
	/**
	 * Gets a byte[] from the socket.
	 * @param length The number of bytes to read.
	 * @return null on failure.
	 */
	public byte[] getByteArray(int length) {
		if (!connected()) {
			return null;
		}
		
		try {
			byte[] bytes = new byte[length];
			for(int x = 0; x < length; x++) {
				bytes[x] = (byte) istream.read();
			}
			return bytes;
		} catch (IOException ex) {
			tryClose();
			return null;
		}
	}
	
	/**
	 * Gets bytes from the socket and writes them to an array.
	 * @param array The array to write to
	 * @param offset The index of the array to start at
	 * @param length The number of bytes to write.
	 */
	public void getByteArray(byte[] array, int offset,int length) {
		try {
			for(int x = offset; x < length + offset; x++) {
				array[x] = (byte)istream.read();
			}
		} catch (IOException e) {
			tryClose();
		}
	}
	
	/**
	 * Gets a bug-endian integer from the socket.
	 * TODO: Make ABSOLUTELY sure this doesn't screw up because of java forcing bytes signed.
	 * @return -1 on failure.
	 */
	public int getInteger() {
		if (!connected()) {
			return -1;
		}

		try {
			int i = 0;
			for(int x = 0; x < 4; x++) {
				int next_byte = istream.read();
				if(next_byte == -1) {
					return -1;
				}
				else {
					i = i << 8;
					i = i | next_byte;
				}
			}
			return i;
		} catch (IOException ex) {
			tryClose();
			return -1;
		}
	}

	/**
	 * Gets an ASCII String from the socket.
	 * @param length The length of the String.
	 * @return null on failure.
	 */
	public String getString(int length) {
		if (!connected()) {
			return null;
		}

		try {
			String str = "";
			for(int x = 0; x < length; x++) {
				int next_byte = istream.read();
				if(next_byte == -1) {
					return null;
				}
				else {
					str = str + (char)next_byte;
				}
			}
			return str;
		} catch (IOException ex) {
			tryClose();
			return null;
		}
	}

	/**
	 * Gets the connection state.
	 * @return true iff the socket is connected
	 */
	public boolean connected() {
		return connected;
	}
	
	/**
	 * Tries to close the socket.
	 */
	public void tryClose() {
		connected = false;
		try {
			socket.close();
		}
		catch(IOException e){}
	}
}
