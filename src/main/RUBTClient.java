package main;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Random;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;

import structures.FileInfo;
import bencoding.ResponseDecoder;
import bencoding.TorrentDecoder;
import connection.ListenerThread;
import connection.PeerSocket;
import connection.Tracker;

/**
 * The GUI for the client.
 * Allows user to view torrent progress, connected peers, etcetera.
 * @author arihayes
 */
public class RUBTClient extends JFrame implements Runnable, ActionListener {
	/**
	 * Automatically generated for god-knows-what-reason
	 */
	private static final long serialVersionUID = -693036000167528691L;
	
	/**
	 * JLabel displaying total bytes downloaded
	 */
	private static JLabel bytes_downloaded;
	
	/**
	 * JLabel displaying total bytes uploaded
	 */
	private static JLabel bytes_uploaded;
	
	/**
	 * JLabel displaying the percentage of the file we have verified
	 */
	private static JLabel percent_done;
	
	/**
	 * JLabel displaying how many pieces we have out of the total
	 */
	private static JLabel pieces_done;
	
	/**
	 * JLabel displaying how many peers are connected
	 */
	private static JLabel peers_connected;
	
	/**
	 * JLabel displaying how many peers & seeds are online
	 */
	private static JLabel peers_online;
	
	/**
	 * JLabel displaying our peer_id
	 */
	private static JLabel peer_id;
	
	/**
	 * JLabel displaying time until tracker update
	 */
	private static JLabel tracker_time;
	
	/**
	 * JButton to stop download
	 */
	private static JButton btnStop;
	
	/**
	 * JButton to resume download
	 */
	private static JButton btnResume;
	
	/**
	 * A table containing info on the connected peers
	 */
	private static JTable peer_table;
	
	/**
	 * The model used for the peer table
	 */
	private static DefaultTableModel peer_table_model;
	
	
	private static JTextArea log = new JTextArea();
	
	/**
	 * Structure to deal with state of downloaded file
	 */
	static FileInfo file_info;
	
	/**
	 * Structure to decode tracker response
	 */
	static ResponseDecoder decoder = null;
	
	/**
	 * Structure to listen for incoming connections
	 */
	static ListenerThread listener = null;
	
	/**
	 * Structure to deal with sending tracker requests
	 */
	static Tracker tracker;
	
	/**
	 * Target for saving downloaded file
	 */
	static String target;
	
	/**
	 * Structure containing all the peers
	 */
	static Vector<PeerSocket> peerList = new Vector<PeerSocket>();
	
	/**
	 * Boolean indicating completion of the downloaded file
	 */
	static boolean file_saved = false;
	
	/**
	 * Boolean indicating whether to stop the torrent.
	 */
	static boolean done = true;
	
	/**
	 * An instance of  this class, which comprises the gui.
	 */
	private static RUBTClient gui;
	
	/**
	 * Constructor for GUI.
	 * 
	 */
	public RUBTClient() {
		//TODO: close operation?
		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		
		//Default fonts:
		UIManager.put("Label.font",UIManager.getFont("Label.font").deriveFont(20f));
		UIManager.put("Button.font",UIManager.getFont("Button.font").deriveFont(20f));
		
		//Widgets & layout:
		JTabbedPane pane = new JTabbedPane(JTabbedPane.TOP,JTabbedPane.SCROLL_TAB_LAYOUT);
		GridBagConstraints c = new GridBagConstraints();
		
		//General tab
        JPanel panel = new JPanel(new GridBagLayout());
        pane.addTab("General", panel);
        
        //General tab labels:
        bytes_downloaded = new JLabel("0 B");
    	bytes_uploaded = new JLabel("0 B");
    	percent_done = new JLabel("0%");
    	pieces_done = new JLabel("0 pieces (Have 0)");
    	peers_connected = new JLabel("0 connected");
    	peers_online = new JLabel("0 leeches 0 seeds)");
    	peer_id = new JLabel("Grp3-");
    	tracker_time = new JLabel("Update 0 sec");
        //c.weightx = 0.5;
        //c.weighty = 0.5;
        c.gridx = 0;
		c.gridy = 0;
        panel.add(new JLabel("% Done:"),c);
        c.gridx = 1;
        panel.add(percent_done,c);
        c.gridx = 0;
		c.gridy = 1;
        panel.add(new JLabel("Downloaded:"),c);
        c.gridx = 1;
        panel.add(bytes_downloaded,c);
        c.gridx = 0;
		c.gridy = 2;
        panel.add(new JLabel("Uploaded:"),c);
        c.gridx = 1;
        panel.add(bytes_uploaded,c);
        c.gridx = 0;
		c.gridy = 3;
        panel.add(new JLabel("Pieces:"),c);
        c.gridx = 1;
        panel.add(pieces_done,c);
        c.gridx = 0;
		c.gridy = 4;
        panel.add(new JLabel("Peers:"),c);
        c.gridx = 1;
        panel.add(peers_connected,c);
        c.gridx = 0;
		c.gridy = 5;
        panel.add(new JLabel("Online:"),c);
        c.gridx = 1;
        panel.add(peers_online,c);
        c.gridx = 0;
		c.gridy = 6;
        panel.add(new JLabel("Your Peer ID:"),c);
        c.gridx = 1;
        panel.add(peer_id,c);
        c.gridx = 0;
		c.gridy = 7;
        panel.add(new JLabel("Tracker update:"),c);
        c.gridx = 1;
        panel.add(tracker_time,c);
        
        //For empty space before buttons:
        c.gridy = 8;
        c.gridx = 0;
        panel.add(new JLabel(" "),c);
        
        //General tab buttons:
        btnStop = new JButton("Stop");
        btnResume = new JButton("Resume");
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridwidth = 2;
        c.gridy = 9;
        panel.add(btnStop,c);
        c.gridy = 10;
        panel.add(btnResume,c);
        btnResume.setEnabled(false);
        
        //Button listeners:
        btnStop.addActionListener(this);
        btnResume.addActionListener(this);
        
		//Peers tab table
		peer_table_model = new DefaultTableModel();
		peer_table_model.addColumn("Peer ID");
		peer_table_model.addColumn("IP Address");
		peer_table_model.addColumn("Piece Downloading");
		peer_table_model.addColumn("Unchoked?");
		peer_table_model.addColumn("Interested?");
		peer_table_model.addColumn("Recent Downloaded");
		peer_table_model.addColumn("Recent Uploaded");
		peer_table = new JTable(peer_table_model);
		peer_table.setColumnSelectionAllowed(false);
		peer_table.setRowSelectionAllowed(true);
		peer_table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		peer_table.setEnabled(false);
		
		//Peers tab
        JScrollPane scroll = new JScrollPane(peer_table);
        pane.addTab("Peers",scroll);
		
		//Add Logger tab:
		pane.addTab("Logger",new JScrollPane(log));
		
		//Add tabs to window:
		add(pane);
		
		//resize:
        pack();
        this.setSize(this.getWidth() + 80, this.getHeight() - 100);
	}
	
	/**
	 * Main method for GUI
	 * Creates a JFrame, makes it visible
	 * Connects to tracker & peers
	 * @param args The run arguments
	 */
	public static void main(String[] args) {
		gui = new RUBTClient();
		gui.setVisible(true);
		
		//Error check:
		if(args.length != 2) {
			logError("Wrong number of arguments.");
			logError("Expected two arguments are <torrent file> <download target>");
			return;
		}
		
		//Read args:
		String torrent = args[0];
		target = args[1];
		
		//Error checking:
		File f = new File(torrent);
		if(f.isDirectory() || !f.exists()) {
			logError("Torrent file not found.");
			return;
		}
		
		//Declare variables for later:
		TorrentDecoder torrent_data;
		String peer_id;
		DecimalFormat decimal_formatter = new DecimalFormat("#.00");
		
		//Parse torrent file
		try {
			torrent_data = new TorrentDecoder(torrent);
		} catch (IOException e) {
			logError("Unable to read torrent file.");
			return;
		}
		if(!torrent_data.valid()) {
			logError("Invalid torrent file.");
			return;
		}
		
		//Prepare the file_info object:
		try {
			file_info = new FileInfo(torrent_data.getFileLength(), torrent_data.getPieceLength(), target);
		} catch (Exception e2) {
			logError("Cannot read/create file.");
		}
		
		//Set gui labels based on file_info:
		bytes_uploaded.setText(file_info.getBytesUploaded()/1024 + " KB");
		bytes_downloaded.setText(file_info.getBytesDownloaded()/1024 + " KB");
		pieces_done.setText(file_info.getBitfieldBitSize() + " pieces (Have " + file_info.getPiecesCompleted() + ")");
		percent_done.setText(decimal_formatter.format(file_info.getPiecesCompleted() * 100.0 / file_info.getBitfieldBitSize()));
		
		//Generate 20char peer_id with group# and datetime-stamp
		Calendar cal = Calendar.getInstance();
		peer_id = "Grp03.";
		if(cal.get(Calendar.MONTH) <= 10)
			peer_id += "0";
		peer_id += (cal.get(Calendar.MONTH) + 1) + "-";
		if(cal.get(Calendar.DATE) < 10)
			peer_id += "0";
		peer_id += cal.get(Calendar.DATE) + ".";
		if(cal.get(Calendar.HOUR_OF_DAY) < 10)
			peer_id += "0";
		peer_id += cal.get(Calendar.HOUR_OF_DAY) + "-";
		if(cal.get(Calendar.MINUTE) < 10)
			peer_id += "0";
		peer_id += cal.get(Calendar.MINUTE) + "-";
		if(cal.get(Calendar.SECOND) < 10)
			peer_id += "0";
		peer_id += cal.get(Calendar.SECOND);
		RUBTClient.peer_id.setText(peer_id);
		
		//Start listening for connections from peers:
		int port;
		for(port = 6881; port < 6890; port++) {
			try {
				listener = new ListenerThread(port,peerList,torrent_data,file_info,peer_id);
				new Thread(listener).start();
				log("Listening on port " + port);
			} catch (IOException e) {
				continue;
			}
			break;
		}
		if(port == 6890) {
			logError("Unable to create listener on any port.");
			return;
		}
		
		//Prepare to connect to tracker:
		tracker = new Tracker(torrent_data.getAnnounceURL(), torrent_data.getInfoHash().toString(), peer_id, port);
		
		//Alert tracker to download about to start:
		try {
			decoder = new ResponseDecoder(tracker.request(file_info.getBytesUploaded(), file_info.getBytesDownloaded(), file_info.getPieceSize(0) * (file_info.getBitfieldBitSize() - file_info.getPiecesCompleted()), "started"));
			log("Sent intial tracker request.");
		} catch (IOException e) {
			logError("Warning: Unable to communicate with tracker.");
		}
		
		//Note number of leeches and seeds
		if(decoder.getComplete() != null && decoder.getDownloaded() != null) {
			peers_online.setText(decoder.getIncomplete() + " leeches " + decoder.getComplete() + " seeds");
		}
		else {
			peers_online.setText("? online");
		}
		
		//Check if file is already done:
		if(file_info.complete()) {
			file_saved = true;
		}
		
		//Set interval for tracker communication:
		Integer interval = decoder.getInterval();
		Integer min_interval = decoder.getMinInterval();
		if(min_interval == null) {
			if(interval == null) {
				min_interval = 0;
				interval = 60;
			}
			else {
				min_interval = interval/2;
			}
		}
		else if(interval == null) {
			interval = min_interval * 2;
		}
		
		//Add shutdown hook:
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				shutdown();
			}
		});
		
		//Start peer-choking thread:
		done = false;
		new Thread(gui).start();
		
		//Loop for announces and peer connections:
		while(true) {
			//Find correct peers and connect (handshake):
			for(int x = 0; x < decoder.getPeers().size(); x++) {
				//if(decoder.getPeers().get(x).getIP().equals("128.6.5.130") || decoder.getPeers().get(x).getIP().equals("128.6.5.131")) {
					PeerSocket peer = null;
					Thread peerThread = null;
					
					//Check if we already are connected to this peer:
					boolean peerExists = false;
					for(int y = 0; y < peerList.size(); y++) {
						if(peerList.get(y).getPeer().getID().equals(decoder.getPeers().get(x).getID())) {
							peerExists = true;
							break;
						}
					}
					if(peerExists) {
						continue;
					}
					
					//Construct peer object
					try {
						peer = new PeerSocket(decoder.getPeers().get(x), torrent_data, file_info, peer_id);
					} catch (Exception e) {
						logError("Unable to connect peer " + decoder.getPeers().get(x).getID());
						continue;
					}
					peerThread = new Thread(peer);
					
					//Error check:
					if(peer == null) {
						logError("Unable to connect peer " + decoder.getPeers().get(x).getID());
						continue;
					}
					else if(peerThread == null) {
						logError("Unable to connect peer " + decoder.getPeers().get(x).getID());
						continue;
					}
					
					//Connect to peer:
					peerList.add(peer);
					peerThread.start();
				//}
			}//peer connection loop
			
			peers_connected.setText(peerList.size() + " connected");
			
			//Sleep for random seconds between min-interval and 2*interval:
			int sleep = (int)(Math.random()*(interval*2 - min_interval) + min_interval);
			for(int x = 0; x < sleep || done; x++) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e1) {}
				
				if(done) {
					if(x < sleep) {
						x = sleep;
						try {
							decoder = new ResponseDecoder(tracker.request(file_info.getBytesUploaded(), file_info.getBytesDownloaded(), file_info.getPieceSize(0) * (file_info.getBitfieldBitSize() - file_info.getPiecesCompleted()), "stopped"));
							
							//Note number of leeches and seeds
							if(decoder.getComplete() != null && decoder.getDownloaded() != null) {
								peers_online.setText(decoder.getIncomplete() + " leeches " + decoder.getComplete() + " seeds");
							}
							else {
								peers_online.setText("? online");
							}
							
						} catch (IOException e) {
							x = 0;
						}
					}
					else {
						x = sleep;
					}
				}
				
				//Set gui labels:
				tracker_time.setText((sleep-x) + " seconds");
				bytes_uploaded.setText(file_info.getBytesUploaded()/1024 + " KB");
				
				if(!file_saved) {
					bytes_downloaded.setText(file_info.getBytesDownloaded()/1024 + " KB");
					pieces_done.setText(file_info.getBitfieldBitSize() + " pieces (Have " + file_info.getPiecesCompleted() + ")");
					percent_done.setText(decimal_formatter.format(file_info.getPiecesCompleted() * 100.0 / file_info.getBitfieldBitSize()));
					
					//Check if we need to alert tracker to download complete:
					if(file_info.complete()) {
						break;
					}
				}
			}
			
			//Alert tracker to download completion:
			if(file_info.complete() && !file_saved) {
				try {
					file_info.saveFile(target);
					file_saved = true;
					log("DOWNLOAD COMPLETE!");
					decoder = new ResponseDecoder(tracker.request(file_info.getBytesUploaded(), file_info.getBytesDownloaded(), file_info.getPieceSize(0) * (file_info.getBitfieldBitSize() - file_info.getPiecesCompleted()), "completed"));
				} catch (IOException e) {
					logError("Unable to save file to disk.");
				}
			}
			else {//Tracker announcement:
				try {
					log("Sending announce to tracker.");
					decoder = new ResponseDecoder(tracker.request(file_info.getBytesUploaded(), file_info.getBytesDownloaded(), file_info.getPieceSize(0) * (file_info.getBitfieldBitSize() - file_info.getPiecesCompleted()), "started"));
				} catch (IOException e) {
					logError("Unable to connect to the tracker.");
					return;
				}
			}
			
			//Note number of leeches and seeds
			if(decoder.getComplete() != null && decoder.getDownloaded() != null) {
				peers_online.setText(decoder.getIncomplete() + " leeches " + decoder.getComplete() + " seeds");
			}
			else {
				peers_online.setText("? online");
			}
		}//tracker communication loop
		
		//shutdown();
	}
	
	/**
	 * Code for a thread which, every 30 seconds, chokes the worse peer and unchokes a random peer.
	 * Also removes disconnected peers from the list, and updates the GUI's peer table
	 */
	@Override
	public void run() {
		int worstPeer = -1;
		int unchoked = 0;
		ArrayList<PeerSocket> choked_peers = new ArrayList<PeerSocket>();
		Random random = new Random();
		while(!done) {
			//Sleep for 30 seconds, updating peer statuses inbetween
			for(int x = 0; x < 20 && !done; x++) {
				try {
					Thread.sleep(1500);
				} catch (InterruptedException e1) {}
				
				//Remove disconnected peers
				for(int y = 0; y < peerList.size(); y++) {
					if(!peerList.get(y).connected()) {
						peerList.remove(y);
						y--;
						peers_connected.setText(peerList.size() + " connected");
					}
				}
				
				writePeerTable();
			}
			
			//Finish thread if done
			if(done) {
				break;
			}			
			
			//Determine worst unchoked peer & remove disconnected peers
			worstPeer = -1;
			unchoked = 0;
			for(int y = 0; y < peerList.size(); y++) {
				if(peerList.get(y).connected()) {
					if(!peerList.get(y).peerChoking()) {
						unchoked++;
						if(worstPeer == -1 || peerList.get(y).getDownloaded() + peerList.get(y).getUploaded() < 
								peerList.get(worstPeer).getDownloaded() + peerList.get(worstPeer).getUploaded()) {
							worstPeer = y;
						}
					}
				}
				else {//remove disconnected peer and continue
					peerList.remove(y);
					y--;
				}
			}
			peers_connected.setText(peerList.size() + " connected");
			
			//If appropriate, choke worse peer & unchoke random
			if(worstPeer >= 0 && unchoked >= PeerSocket.getMaxUnchoked()) {
				//Choke peer or else go back to the while:
				if(!peerList.get(worstPeer).choke()) {
					continue;
				}
				
				//Determine peer to unchoke & reset byte counts:
				choked_peers.clear();
				for(int y = 0; y < peerList.size(); y++) {
					peerList.get(y).resetByteCounts();
					if(peerList.get(y).peerInterested() && peerList.get(y).peerChoking()) {
						choked_peers.add(peerList.get(y));
					}
				}
				
				//Unchoke random peer:
				if(choked_peers.isEmpty() || !choked_peers.get(random.nextInt(choked_peers.size())).unchoke()) {
					//Choked someone, but failed to unchoke someone;
					//we'd better fix the counter:
					PeerSocket.decrementUnchoked();
				}
			}
		}//main loop of this thread
		
		//Disconnect all the peers:
		for(int x = 0; x < peerList.size(); x++) {
			peerList.get(x).disconnect();
			peerList.remove(x);
			x--;
		}
		
		writePeerTable();
	}
	
	private void writePeerTable() {
		//Rewrite data for peer table
		PeerSocket p;
		peer_table_model.setRowCount(peerList.size());
		for(int y = 0; y < peerList.size(); y++) {
			p = peerList.get(y);
			if(peer_table_model.getValueAt(y, 0) != p.getPeer().getID()) {
				peer_table_model.setValueAt(p.getPeer().getID(), y, 0);
				peer_table_model.setValueAt(p.getPeer().getIP(), y, 1);
			}
			
			if(p.getCurrentPiece() >= 0) {
				peer_table_model.setValueAt(p.getCurrentPiece(), y, 2);
			}
			else {
				peer_table_model.setValueAt("N/A", y, 2);
			}
			
			if(p.peerChoking()) {
				peer_table_model.setValueAt("NOPE", y, 3);
			}
			else {
				peer_table_model.setValueAt("YES", y, 3);
			}
			
			if(p.peerInterested()) {
				peer_table_model.setValueAt("YES", y, 4);
			}
			else {
				peer_table_model.setValueAt("NOPE", y, 4);
			}
			
			peer_table_model.setValueAt(p.getDownloaded()/1024 + " KB", y, 5);
			peer_table_model.setValueAt(p.getUploaded()/1024 + " KB", y, 6);
		}
		peers_connected.setText(peerList.size() + " connected");
	}

	/**
	 * Called during shutdown.
	 * Stops the listener and waits for all the peers to disconnect.
	 * Informs tracker of download stopping.
	 */
	public static void shutdown() {
		//Stop listener:
		listener.stop();
		
		if(!done) {
			
			//Gracefully disconnect, if not already stopped
			try {
				new ResponseDecoder(tracker.request(file_info.getBytesUploaded(), file_info.getBytesDownloaded(), file_info.getPieceSize(0) * (file_info.getBitfieldBitSize() - file_info.getPiecesCompleted()), "stopped"));
				log("Disconnected from tracker.");
			} catch (IOException e) {
				logError("Warning: Unable to communicate with tracker.");
			}
			
			//Stop peer-choking thread:
			done = true;
			
			//Wait for threads to stop:
			while(peerList.size() > 0) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
				}
			}
		}
	}
	
	/**
	 * Called when a button is clicked.
	 * Stops/Resumes uploading and downloading.
	 * @param evt The click event
	 */
	@Override
	public void actionPerformed(ActionEvent evt) {
		Object src = evt.getSource();
		if(src == btnResume) {
			btnResume.setEnabled(false);
			listener.start();
			
			done = false;
			new Thread(gui).start();
			
			btnStop.setEnabled(true);
		}
		else if(src == btnStop) {
			btnStop.setEnabled(false);
			listener.stop();
			
			done = true;
			while(!peerList.isEmpty()) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {}
			}
			
			btnResume.setEnabled(true);
		}
	}
	
	/**
	 * Writes a line to the log.
	 * Also writes it to System.out
	 * @param line The line String to write
	 */
	public synchronized static void log(String line) {
		log.append(line + "\n");
		System.out.println(line);
	}
	
	/**
	 * Writes "ERROR:" followed by a line to the log.
	 * Also writes it normally to System.err
	 * @param line The line String to write
	 */
	public synchronized static void logError(String line) {
		log.append("ERROR: " + line + "\n");
		System.err.println(line);
	}
}
