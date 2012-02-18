package es.jinki.BanList;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import lib.PatPeter.SQLibrary.DatabaseHandler;
import lib.PatPeter.SQLibrary.MySQL;
import lib.PatPeter.SQLibrary.SQLite;

/**
 * 
 * @author cerevisiae
 * @version 1.0
 */
public class SQLConnect {
	
	private static Logger log = Logger.getLogger("Minecraft");
	
	// Settings Variables
	private boolean useMySQL = false; //If we should use mySQL or SQLite
	private String dbHost = "localhost";
	private String dbUser = null;
	private String dbPass = null;
	private String dbName = null;
	private String dbPort = "3306";
	private String logPrefix = "[BanList]";
	private File dataFolder;
	private String dbPrefix = "bl_";

	/**
	 * MySQL Constructor
	 * @param aLog Logger to use.
	 * @param lprefix Prefix to use for logging
	 * @param host The host address for the server
	 * @param port The port for the server
	 * @param name The name of the database
	 * @param user The user to log in with
	 * @param pass The password to use
	 */
	public SQLConnect(Logger aLog, String lprefix,
			String host,
			String port,
			String name,
			String user,
			String pass){
		useMySQL = true;//This is the mySQL constructor
		log = aLog;
		logPrefix = lprefix;
		dbHost = host;
		dbUser = user;
		dbName = name;
		dbPort = port;
		dbPass = pass;
	}
	
	/**
	 * SQLite Constructor
	 * @param aLog Logger to use.
	 * @param lprefix Prefix to use for logging
	 * @param name The name to use for the database
	 * @param location The location of the database
	 */
	public SQLConnect(Logger aLog, String lprefix, String name, String location){
		useMySQL = false;//This is the mySQL constructor
		log = aLog;
		logPrefix = lprefix;
		dbName = name;
		dataFolder = new File(location);
	}
	
	/**
	 * Sets up the BanList tables if needed
	 */
	public void setupTables(){
		//Table Query
		String ActionLog = "CREATE TABLE IF NOT EXISTS " + dbPrefix + "ActionLog (" +
				"actionTime timestamp DEFAULT CURRENT_TIMESTAMP," +
				"playerIP text NOT NULL," +
				"playerName text NOT NULL," +
				"modName text NOT NULL," +
				"actionType int(1) NOT NULL," +
				"actionReason text," +
				"PRIMARY KEY (actionTime)," +
				"FOREIGN KEY (playerName) REFERENCES " + dbPrefix + "PlayerInfo(playerName)" +
				");";
		
		String ConnectionLog = "CREATE TABLE IF NOT EXISTS " + dbPrefix + "ConnectionLog (" +
				"joinTime timestamp DEFAULT CURRENT_TIMESTAMP," +
				"quitTime timestamp," +
				"playerIP text NOT NULL," +
				"playerName text NOT NULL," +
				"PRIMARY KEY (joinTime)," +
				"FOREIGN KEY (playerName) REFERENCES " + dbPrefix + "PlayerInfo(playerName)" +
				");";
		
		String playerInfo = "CREATE TABLE IF NOT EXISTS " + dbPrefix + "PlayerInfo (" +
				"playerName text NOT NULL," +
				"isBanned boolean DEFAULT FALSE," +
				"endProbation timestamp," +
				"PRIMARY KEY (playerName)" +
				");";
		
		String ipBans = "CREATE TABLE IF NOT EXISTS " + dbPrefix + "IPBans (" +
				"ipAddress text NOT NULL," +
				"PRIMARY KEY (ipAddress)" +
				");";
		
		sendUpdate(ActionLog);
		sendUpdate(ConnectionLog);
		sendUpdate(playerInfo);
		sendUpdate(ipBans);
	}
	
	/**
	 * Runs a sql statement that doesn't return anything.
	 */
	public void sendUpdate(String sql){
		PreparedStatement st = null;
		DatabaseHandler db = null;
		
		//Get the preparestatement
		if(useMySQL){
			db = new MySQL(log, logPrefix, dbHost, dbPort, dbName, dbUser, dbPass);
			st = ((MySQL)db).prepare(sql);
		}else{
			db = new SQLite(log, logPrefix, "banlist", dataFolder.getPath());
			st = ((SQLite)db).prepare(sql);
		}
		try {
			st.executeUpdate();
			st.close();
			if(db instanceof MySQL)
				((MySQL)db).close();
			else
				((SQLite)db).close();
		} catch (SQLException e) {
			log.warning(logPrefix + e.getMessage());
		}
	}
	
	/**
	 * Handler for if no type needs to be specified
	 * @return the total number
	 */
	public int getCountActions(){
		return getCountActions(null, BanList.NONE);
	}
	
	/**
	 * Handler for if no type needs to be specified
	 * @param type Based on the banlist items
	 * @return the total number
	 */
	public int getCountActions(int type){
		return getCountActions(null, type);
	}
	
	/**
	 * Finds out how many rapsheet pages there are in total
	 * @param playerName The name of the player to look up
	 * @return The number of pages
	 */
	public int getCountActions(String playerName){
		return getCountActions(playerName, BanList.NONE);
	}
	
	/**
	 * Get how many of a certain type of actions there are
	 * @param playerName The name of the player to look up
	 * @param type Based on the banlist items
	 * @return The count
	 */
	public int getCountActions(String playerName, int type){
		int count = 0;
		
		//Format the query
		String query = "SELECT COUNT(*) FROM " + dbPrefix + "ActionLog";
		
		if(type > BanList.NONE)
			query =  "SELECT COUNT(*) FROM " + dbPrefix + "ActionLog WHERE actionType=?";
		
		if(playerName != null)
			query =  "SELECT COUNT(*) FROM " + dbPrefix + "ActionLog WHERE playerName=?";
		
		if(playerName != null && type > BanList.NONE)
			query =  "SELECT COUNT(*) FROM " + dbPrefix + "ActionLog WHERE actionType=? AND playerName=?";

		PreparedStatement st = null;
		DatabaseHandler db = null;
		
		//Get the preparestatement
		if(useMySQL){
			db = new MySQL(log, logPrefix, dbHost, dbPort, dbName, dbUser, dbPass);
			st = ((MySQL)db).prepare(query);
		}else{
			db = new SQLite(log, logPrefix, "banlist", dataFolder.getPath());
			st = ((SQLite)db).prepare(query);
		}
		try {
			
			int i = 1;//For accurately entering based on amount of info
			
			//If a type was specified
			if(type > BanList.NONE){
				st.setInt(1, type);
				i++;
			}
			
			//If a player was specified
			if(playerName != null)
				st.setString(i, playerName);
			
			ResultSet rs = st.executeQuery();
			if(rs.next()){
				count = rs.getInt(1);
			}
			rs.close();
			st.close();
			if(db instanceof MySQL)
				((MySQL)db).close();
			else
				((SQLite)db).close();
		} catch (SQLException e) {
			log.warning(logPrefix + e.getMessage());
		}
			
		return count;
	}

	/**
	 * Get the rapsheet pages based page number
	 * @param pageNum Get the page number specified
	 * @param numResultsPerPage
	 * @return
	 */
	public AdminAction[] getActionPage(int pageNum, int pageLength) {
		return getActionPage(null, pageNum, pageLength);
	}

	/**
	 * Get the most recent entry for a specified player and action type
	 * @param playerName The name of the player to look up.
	 * @param pageNum The type of actions to be examined
	 * @return A rapsheet containing all the info about the most recent entry.
	 */
	public AdminAction getMostRecent(String playerName, int type){
		
		AdminAction action = null;
		
		String query = "SELECT * FROM " + dbPrefix + "ActionLog WHERE playerName=? AND actionType=? ORDER BY actionTime DESC";

		PreparedStatement st = null;
		DatabaseHandler db = null;
		//Get the preparestatement
		if(useMySQL){
			db = new MySQL(log, logPrefix, dbHost, dbPort, dbName, dbUser, dbPass);
			st = ((MySQL)db).prepare(query);
		}else{
			db = new SQLite(log, logPrefix, "banlist", dataFolder.getPath());
			st = ((SQLite)db).prepare(query);
		}
		try {
			st.setString(1, playerName);
			st.setInt(2, type);
			
			ResultSet rs = st.executeQuery();
			
			//Build the rapsheet	
			if(rs.next()){
				Timestamp time = new Timestamp(rs.getLong(1));
				Timestamp end = new Timestamp(rs.getLong(2));
				String ip = rs.getString(2);
				String name = rs.getString(3);
				String mod = rs.getString(4);
				String reason = rs.getString(6);
				action = new AdminAction(time, end, name, ip, type, mod, reason);
			}
			rs.close();
			st.close();
			if(db instanceof MySQL)
				((MySQL)db).close();
			else
				((SQLite)db).close();
			
		} catch (SQLException e) {
			log.warning(logPrefix + e.getMessage());
		}
		return action;
	}

	/**
	 * Get the rapsheet pages based on player name and page number
	 * @param playerName The name of the player to get the page for. Null gets all people
	 * @param pageNum Get the page number specified
	 * @param pageLength The number of results per page
	 * @return An array of all the RapSheet items on the page
	 */
	public AdminAction[] getActionPage(String playerName, int pageNum, int pageLength){
		AdminAction[] actions = new AdminAction[pageLength];
		
		//Format the query
		String query = "SELECT * FROM " + dbPrefix + "ActionLog ORDER BY actionTime DESC";
		
		if(playerName != null)
			query = "SELECT * FROM " + dbPrefix + "ActionLog WHERE playerName=? ORDER BY actionTime DESC";

		PreparedStatement st = null;
		DatabaseHandler db = null;
		
		//Get the preparestatement
		if(useMySQL){
			db = new MySQL(log, logPrefix, dbHost, dbPort, dbName, dbUser, dbPass);
			st = ((MySQL)db).prepare(query);
		}else{
			db = new SQLite(log, logPrefix, "banlist", dataFolder.getPath());
			st = ((SQLite)db).prepare(query);
		}
		try {
			//Insert player name if needed
			if(playerName != null){
				st.setString(1, playerName);
			}
			
			ResultSet rs = st.executeQuery();
			
			for(int i = 0; i < pageLength * (pageNum - 1); i++ ){rs.next();}
			

			//Build the rapsheet	
			for(int i = 0; rs.next() && i < pageLength; i++){
				Timestamp time = new Timestamp(rs.getLong(1));
				Timestamp end = new Timestamp(rs.getLong(2));
				String ip = rs.getString(3);
				String name = rs.getString(4);
				String mod = rs.getString(5);
				int type = rs.getInt(6);
				String reason = rs.getString(7);
				actions[i] = new AdminAction(time, end, name, ip, type, mod, reason);
			}
			rs.close();
			st.close();
			if(db instanceof MySQL)
				((MySQL)db).close();
			else
				((SQLite)db).close();
			
		} catch (SQLException e) {
			log.warning(logPrefix + e.getMessage());
		}
		return actions;
	}
	
	/**
	 * Stores an administrative action into the database table
	 * @param end The end date, used for probations
	 * @param name The name of the player
	 * @param ip The IP of the player
	 * @param type The type of administrative action taken
	 * @param mod The moderator who performed the action
	 * @param reason The reason for the action being taken
	 */
	public void sendAction(OfflinePlayer player, int type, String mod, String reason){

		String query = "INSERT INTO " + dbPrefix + "ActionLog " +
					"(actionTime, playerIP, playerName, modName, actionType, actionReason) " +
					"VALUES (?, ?, ?, ?, ?, ?);";
		
		PreparedStatement st = null;
		DatabaseHandler db = null;
		if(useMySQL){
			db = new MySQL(log, logPrefix, dbHost, dbPort, dbName, dbUser, dbPass);
			st = ((MySQL)db).prepare(query);
		}else{
			db = new SQLite(log, logPrefix, "banlist", dataFolder.getPath());
			st = ((SQLite)db).prepare(query);
		}

		try {
			//Find the ip address
			String ip = "OFFLINE";
			if(player instanceof Player)
				ip = ((Player) player).getAddress().getHostName();
			
			st.setLong(1, System.currentTimeMillis());
			st.setString(2, ip);
			st.setString(3, player.getName());
			st.setString(4, mod);
			st.setInt(5, type);
			st.setString(6, reason);
			
			st.executeUpdate();
			st.close();
			if(db instanceof MySQL)
				((MySQL)db).close();
			else
				((SQLite)db).close();
			
		} catch (SQLException e) {
			log.warning(logPrefix + e.getMessage());
		}
	}
	
	/**
	 * Logs when a player joins, their name, and their IP
	 * @param player The player to log for
	 */
	public void logPlayerJoin(Player player){
		String sql = "INSERT INTO " + dbPrefix + "ConnectionLog " +
				"(joinTime, playerIP, playerName) VALUES (?, ?, ?);";
		
		PreparedStatement st = null;
		DatabaseHandler db = null;
		
		//Get the preparestatement
		if(useMySQL){
			db = new MySQL(log, logPrefix, dbHost, dbPort, dbName, dbUser, dbPass);
			st = ((MySQL)db).prepare(sql);
		}else{
			db = new SQLite(log, logPrefix, "banlist", dataFolder.getPath());
			st = ((SQLite)db).prepare(sql);
		}
		try {
			//Find the ip address
			String ip = "OFFLINE";
			if(player instanceof Player)
				ip = ((Player) player).getAddress().getHostName();
			
			st.setLong(1, System.currentTimeMillis());
			st.setString(2, ip);
			st.setString(3, player.getName());
			
			st.executeUpdate();
			st.close();
			if(db instanceof MySQL)
				((MySQL)db).close();
			else
				((SQLite)db).close();
		} catch (SQLException e) {
			log.warning(logPrefix + e.getMessage());
		}
	}
	
	/**
	 * Updates the most recent entry to log when they quit;
	 * @param player The player to update
	 */
	public void logPlayerQuit(Player player){
		String sql = "UPDATE " + dbPrefix + "ConnectionLog " +
				"SET quitTime=? WHERE playerName=? AND joinTime=?;";
		
		PreparedStatement st = null;
		DatabaseHandler db = null;
		
		//Get the preparestatement
		if(useMySQL){
			db = new MySQL(log, logPrefix, dbHost, dbPort, dbName, dbUser, dbPass);
			st = ((MySQL)db).prepare(sql);
		}else{
			db = new SQLite(log, logPrefix, "banlist", dataFolder.getPath());
			st = ((SQLite)db).prepare(sql);
		}
		try {
			st.setLong(1, System.currentTimeMillis());
			st.setString(2, player.getName());
			st.setTimestamp(3, mostRecentJoin(player));
			
			st.executeUpdate();
			st.close();
			if(db instanceof MySQL)
				((MySQL)db).close();
			else
				((SQLite)db).close();
		} catch (SQLException e) {
			log.warning(logPrefix + e.getMessage());
		}
	}
	
	/**
	 * Gets the time when a player joined last
	 * @param player The player to look up
	 * @return The time they logged in
	 */
	public Timestamp mostRecentJoin(Player player){
		String sql = "SELECT joinTime FROM " + dbPrefix + "ConnectionLog " +
				"WHERE playerName=? ORDER BY joinTime DESC";
		
		Timestamp time = null;
		
		PreparedStatement st = null;
		DatabaseHandler db = null;
		
		//Get the preparestatement
		if(useMySQL){
			db = new MySQL(log, logPrefix, dbHost, dbPort, dbName, dbUser, dbPass);
			st = ((MySQL)db).prepare(sql);
		}else{
			db = new SQLite(log, logPrefix, "banlist", dataFolder.getPath());
			st = ((SQLite)db).prepare(sql);
		}
		try {
			st.setString(1, player.getName());
			
			ResultSet rs = st.executeQuery();
			
			if(rs.next())
				time = rs.getTimestamp(1);
			
			rs.close();
			st.close();
			if(db instanceof MySQL)
				((MySQL)db).close();
			else
				((SQLite)db).close();
		} catch (SQLException e) {
			log.warning(logPrefix + e.getMessage());
		}
		
		return time;
	}

	/**
	 * Creates an OfflinePlayer with a name from known players
	 * @param playerName The name of the player to look up
	 * @return OfflinePlayer with a name that was looked up
	 */
	public OfflinePlayer getOfflinePlayer(String playerName) {
		String sql = "SELECT playerName FROM " + dbPrefix + "ConnectionLog " +
				"WHERE playerName LIKE ? ORDER BY joinTime DESC;";
				
		PreparedStatement st = null;
		DatabaseHandler db = null;
		
		//Get the preparestatement
		if(useMySQL){
			db = new MySQL(log, logPrefix, dbHost, dbPort, dbName, dbUser, dbPass);
			st = ((MySQL)db).prepare(sql);
		}else{
			db = new SQLite(log, logPrefix, "banlist", dataFolder.getPath());
			st = ((SQLite)db).prepare(sql);
		}
		try {
			st.setString(1, playerName+"%");
			
			ResultSet rs = st.executeQuery();
			
			if(rs.next())
				playerName = rs.getString(1);
			
			rs.close();
			st.close();
			if(db instanceof MySQL)
				((MySQL)db).close();
			else
				((SQLite)db).close();
		} catch (SQLException e) {
			log.warning(logPrefix + e.getMessage());
		}
		
		return Bukkit.getOfflinePlayer(playerName);
	}
	
	/**
	 * Adds a player to the list of tracked people
	 * @param player The player to add
	 */
	public void insertPlayer(Player player){
		String sql = "INSERT INTO " + dbPrefix + "PlayerInfo (playerName) VALUES (?);";
		PreparedStatement st = null;
		DatabaseHandler db = null;
		
		//Get the preparestatement
		if(useMySQL){
			db = new MySQL(log, logPrefix, dbHost, dbPort, dbName, dbUser, dbPass);
			st = ((MySQL)db).prepare(sql);
		}else{
			db = new SQLite(log, logPrefix, "banlist", dataFolder.getPath());
			st = ((SQLite)db).prepare(sql);
		}
		try {
			st.setString(1, player.getName());
			
			st.executeUpdate();
			st.close();
			if(db instanceof MySQL)
				((MySQL)db).close();
			else
				((SQLite)db).close();
		} catch (SQLException e) {
			if(!e.getMessage().toLowerCase().contains("is not unique"))
				log.warning(logPrefix + e.getMessage());
		}
	}
	
	/**
	 * Sets the ban state of a player
	 * @param player The player to set for
	 * @param banState If the player should be banned
	 */
	public void setBanned(OfflinePlayer player, boolean banState){
		String sql = "UPDATE " + dbPrefix + "PlayerInfo SET isBanned=? WHERE playerName=?";
				
		PreparedStatement st = null;
		DatabaseHandler db = null;
		
		//Get the preparestatement
		if(useMySQL){
			db = new MySQL(log, logPrefix, dbHost, dbPort, dbName, dbUser, dbPass);
			st = ((MySQL)db).prepare(sql);
		}else{
			db = new SQLite(log, logPrefix, "banlist", dataFolder.getPath());
			st = ((SQLite)db).prepare(sql);
		}
		try {
			st.setBoolean(1, banState);
			st.setString(2, player.getName());
			
			st.executeUpdate();
			st.close();
			if(db instanceof MySQL)
				((MySQL)db).close();
			else
				((SQLite)db).close();
		} catch (SQLException e) {
			log.warning(logPrefix + e.getMessage());
		}
	}
	
	/**
	 * Gets the ban state of a player
	 * @param player The player to look up
	 * @return If the player is banned
	 */
	public boolean isBanned(String player){
		String sql = "SELECT isBanned FROM " + dbPrefix + "PlayerInfo WHERE playerName=?";
		
		boolean banState = false;
				
		PreparedStatement st = null;
		DatabaseHandler db = null;
		
		//Get the preparestatement
		if(useMySQL){
			db = new MySQL(log, logPrefix, dbHost, dbPort, dbName, dbUser, dbPass);
			st = ((MySQL)db).prepare(sql);
		}else{
			db = new SQLite(log, logPrefix, "banlist", dataFolder.getPath());
			st = ((SQLite)db).prepare(sql);
		}
		try {
			if(st != null){
				st.setString(1, player);
				ResultSet rs = st.executeQuery();

				if(rs.next())
					banState = rs.getBoolean(1);
				st.close();
			}
			if(db instanceof MySQL)
				((MySQL)db).close();
			else
				((SQLite)db).close();
		} catch (SQLException e) {
			log.warning(logPrefix + e.getMessage());
		}
		
		return banState;
	}
	
	/**
	 * Sets the end date of a player's probation
	 * @param player The player to set
	 * @param probate The end date of their probation
	 */
	public void setProbate(OfflinePlayer player, Timestamp probate){
		String sql = "UPDATE " + dbPrefix + "PlayerInfo SET endProbation=? WHERE playerName=?";
				
		PreparedStatement st = null;
		DatabaseHandler db = null;
		
		//Get the preparestatement
		if(useMySQL){
			db = new MySQL(log, logPrefix, dbHost, dbPort, dbName, dbUser, dbPass);
			st = ((MySQL)db).prepare(sql);
		}else{
			db = new SQLite(log, logPrefix, "banlist", dataFolder.getPath());
			st = ((SQLite)db).prepare(sql);
		}
		try {
			st.setTimestamp(1, probate);
			st.setString(2, player.getName());
			
			st.executeUpdate();
			st.close();
			if(db instanceof MySQL)
				((MySQL)db).close();
			else
				((SQLite)db).close();
		} catch (SQLException e) {
			log.warning(logPrefix + e.getMessage());
		}
	}
	
	/**
	 * Returns the end date of the probation, if there is one
	 * @param player The player to look up
	 * @return
	 */
	public Timestamp getProbate(String player){
		String sql = "SELECT endProbation FROM " + dbPrefix + "PlayerInfo WHERE playerName=?";
		
		Timestamp endTime = null;
				
		PreparedStatement st = null;
		DatabaseHandler db = null;
		
		//Get the preparestatement
		if(useMySQL){
			db = new MySQL(log, logPrefix, dbHost, dbPort, dbName, dbUser, dbPass);
			st = ((MySQL)db).prepare(sql);
		}else{
			db = new SQLite(log, logPrefix, "banlist", dataFolder.getPath());
			st = ((SQLite)db).prepare(sql);
		}
		try {
			if(st != null){
				st.setString(1, player);
				ResultSet rs = st.executeQuery();
				
				if(rs.next())
					endTime = rs.getTimestamp(1);
				st.close();
			}
			if(db instanceof MySQL)
				((MySQL)db).close();
			else
				((SQLite)db).close();
		} catch (SQLException e) {
			log.warning(logPrefix + e.getMessage());
		}
		
		return endTime;
	}
	
	/**
	 * Adds an IP to the ban list
	 * @param address
	 */
	public void banIP(String address){
		String sql = "INSERT INTO " + dbPrefix + "IPBans (ipAddress) VALUES (?)";
				
		PreparedStatement st = null;
		DatabaseHandler db = null;
		
		//Get the preparestatement
		if(useMySQL){
			db = new MySQL(log, logPrefix, dbHost, dbPort, dbName, dbUser, dbPass);
			st = ((MySQL)db).prepare(sql);
		}else{
			db = new SQLite(log, logPrefix, "banlist", dataFolder.getPath());
			st = ((SQLite)db).prepare(sql);
		}
		try {
			st.setString(1, address);
			
			st.executeUpdate();
			st.close();
			if(db instanceof MySQL)
				((MySQL)db).close();
			else
				((SQLite)db).close();
		} catch (SQLException e) {
			if(!e.getMessage().toLowerCase().contains("is not unique"))
				log.warning(logPrefix + e.getMessage());
		}
	}
	
	/**
	 * Removes an IP from the ban list
	 * @param address
	 */
	public void pardonIP(String address){
		String sql = "DELETE FROM " + dbPrefix + "IPBans WHERE ipAddress=?";
				
		PreparedStatement st = null;
		DatabaseHandler db = null;
		
		//Get the preparestatement
		if(useMySQL){
			db = new MySQL(log, logPrefix, dbHost, dbPort, dbName, dbUser, dbPass);
			st = ((MySQL)db).prepare(sql);
		}else{
			db = new SQLite(log, logPrefix, "banlist", dataFolder.getPath());
			st = ((SQLite)db).prepare(sql);
		}
		try {
			st.setString(1, address);
			
			st.executeUpdate();
			st.close();
			if(db instanceof MySQL)
				((MySQL)db).close();
			else
				((SQLite)db).close();
		} catch (SQLException e) {
			log.warning(logPrefix + e.getMessage());
		}
	}
	
	/**
	 * Returns if an IP is banned or not
	 * @param address The IP to check
	 * @return If the IP is banned
	 */
	public boolean isAddressBanned(String address){
		String sql = "SELECT ipAddress FROM " + dbPrefix + "IPBans WHERE ipAddress=?";
		
		boolean isBanned = false;
				
		PreparedStatement st = null;
		DatabaseHandler db = null;
		
		//Get the preparestatement
		if(useMySQL){
			db = new MySQL(log, logPrefix, dbHost, dbPort, dbName, dbUser, dbPass);
			st = ((MySQL)db).prepare(sql);
		}else{
			db = new SQLite(log, logPrefix, "banlist", dataFolder.getPath());
			st = ((SQLite)db).prepare(sql);
		}
		try {
			if(st != null){
				st.setString(1, address);
				ResultSet rs = st.executeQuery();
				
				if(rs.next())
					isBanned = rs.getBoolean(1);
				st.close();
			}
			if(db instanceof MySQL)
				((MySQL)db).close();
			else
				((SQLite)db).close();
		} catch (SQLException e) {
			log.warning(logPrefix + e.getMessage());
		}
		
		return isBanned;
	}
	
	/**
	 * Returns the IP of a player if they've ever been on
	 * @param address The IP to check
	 * @return If the IP is banned
	 */
	public String getLastIP(OfflinePlayer player){
		String sql = "SELECT playerIP FROM " + dbPrefix + "ConnectionLog WHERE playerName=? ORDER BY joinTime DESC";
		
		String address = null;
				
		PreparedStatement st = null;
		DatabaseHandler db = null;
		
		//Get the preparestatement
		if(useMySQL){
			db = new MySQL(log, logPrefix, dbHost, dbPort, dbName, dbUser, dbPass);
			st = ((MySQL)db).prepare(sql);
		}else{
			db = new SQLite(log, logPrefix, "banlist", dataFolder.getPath());
			st = ((SQLite)db).prepare(sql);
		}
		try {
			if(st != null){
				st.setString(1, player.getName());
				ResultSet rs = st.executeQuery();
				
				if(rs.next())
					address = rs.getString(1);
				st.close();
			}
			if(db instanceof MySQL)
				((MySQL)db).close();
			else
				((SQLite)db).close();
		} catch (SQLException e) {
			log.warning(logPrefix + e.getMessage());
		}
		
		return address;
	}
}
