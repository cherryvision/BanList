package es.jinki.BanList;

import java.io.File;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Calendar;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPreLoginEvent;
import org.bukkit.event.player.PlayerPreLoginEvent.Result;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class BanList extends JavaPlugin implements Listener {

	//Get the logger for displaying output
	private Logger log = Logger.getLogger("Minecraft");
	protected final String logPrefix = "[BanList] "; // Prefix to go in front of all this.log entries
	
	//Config file variables
	private File configFile;
	private File dataFolder;
	private FileConfiguration config;
	
	private SQLConnect sqlc = null;
	
	//The variables for the different types of actions
	protected static final int	NONE	= -1,
								BAN		=  0,
								UNBAN	=  1,
								PROBATE	=  2,
								KICK	=  3,
								IPBAN	=  4,
								COMMENT	=  5;
	
	private static final int RESULTS_PER_PAGE = 6;
	
	/**
	 * Enabling the server. Get the SQL Connection
	 */
	public void onEnable(){ 
		dataFolder = getDataFolder();
		File folder = new File(dataFolder.getPath());
		if (!folder.exists()) {
			folder.mkdir();
		}

		
		//Get the this.config file
		dataFolder = getDataFolder();
		configFile = new File(dataFolder, File.separator + "config.yml");
		config = getConfig();
		
		getConfig().options().copyDefaults(true);

		// If the this.config file doesn't exist, copy from the jar
		if (!configFile.exists()) {
			dataFolder.mkdir();
			saveDefaultConfig();
		}

		//Prepare the SQL table
		if(config.getBoolean("MySQL.Enabled")){
			String dbHost = config.getString("MySQL.Server.Address");
			String dbPass = config.getString("MySQL.Database.User.Password");
			String dbName = config.getString("MySQL.Database.Name");
			String dbUser = config.getString("MySQL.Database.User.Name");
			String dbPort = config.getString("MySQL.Server.Port");
			sqlc = new SQLConnect(log, logPrefix, dbHost, dbPort, dbName, dbUser, dbPass);
		}else{
			sqlc = new SQLConnect(log, logPrefix, "banlist", dataFolder.getPath());
		}
		
		sqlc.setupTables();
		
		this.log.info(this.logPrefix + "has been successfully enabled.");
		
		//Register the player listener
		getServer().getPluginManager().registerEvents(this, this);

	}
	 
	/**
	 * Say the plugin has been disabled even though we don't really do anything.
	 */
	public void onDisable(){ 
		this.log.info(this.logPrefix + "has been successfully disabled.");
	}
	
	/**
	 * Checks to make sure a player isn't banned or probating before letting them on
	 * @param event The PlayerPreLoginEvent passed containing player info
	 */
	@EventHandler
	public void checkBan(PlayerPreLoginEvent event) {
		String kickMessage = new String();
		Timestamp currentTime = new Timestamp(Calendar.getInstance().getTimeInMillis());
		Timestamp endTime = sqlc.getProbate(event.getName());

		event.setResult(Result.KICK_BANNED);
		
		
		if(sqlc.isAddressBanned(event.getAddress().getHostAddress())){
			kickMessage = "Your IP address is banned.";
		}
		
		//if they're banned, prevent them from connecting
		else if(sqlc.isBanned(event.getName())){
			AdminAction ban = sqlc.getMostRecent(event.getName(), BanList.BAN);
			kickMessage = "You are currently banned";
			
			if(ban != null && !ban.actionReason.isEmpty())
				kickMessage += " because \"" + ban.actionReason + "\"";
		}
		
		//If they're not banned, check for probation
		else if(endTime != null && currentTime.before(endTime)){
			AdminAction pb = sqlc.getMostRecent(event.getName(), BanList.PROBATE);
			kickMessage = "You are currently probated for another " + getTimeRemaining(endTime);
			
			if(pb != null && !pb.actionReason.isEmpty())
				kickMessage += " because \"" + pb.actionReason + "\"";
		} else
			event.setResult(Result.ALLOWED);
		
		event.setKickMessage(kickMessage);
	}
	
	/**
	 * Logs a player join for potential later use
	 * @param event
	 */
	@EventHandler
	public void logPlayerJoin(PlayerJoinEvent event) {
		sqlc.insertPlayer(event.getPlayer());
		sqlc.logPlayerJoin(event.getPlayer());
	}
	
	/**
	 * Updates a player entry on quit for potential later use
	 * @param event
	 */
	@EventHandler
	public void logPlayerQuit(PlayerQuitEvent event) {
		sqlc.logPlayerQuit(event.getPlayer());
	}
	
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args){
		
		int type = BanList.NONE;
		int reasonStart = 1; //Because some commands have more arguments
		
		Timestamp endDate = null;
		
		String[] reasonArray = null;
		OfflinePlayer player = null;

		//Get the player
		if(args.length > 0)
			player = getPlayer(args[0]);
		
		
		if(cmd.getName().equalsIgnoreCase("ban")){
		    type = BanList.BAN;
		    sqlc.setBanned(player, true);
		} else if(cmd.getName().equalsIgnoreCase("unban")){
			type = BanList.UNBAN;
		    sqlc.setBanned(player, false);
		} else if(cmd.getName().equalsIgnoreCase("kick")){
			type = BanList.KICK;
		} else if(cmd.getName().equalsIgnoreCase("banip")){
			String address = null;
			type = BanList.IPBAN;
			
			if(args.length > 0){
				address = getAddress(args[0], sender);
			}else
				return false;
			
			//IP could be found
			if(address != null){
				if(sqlc.isAddressBanned(address)){
					sender.sendMessage(this.logPrefix + address + " is already banned.");
					return true;
				}
				sqlc.banIP(address);
				sender.sendMessage(this.logPrefix + "You have banned " + address);
			}
			
			//IP could not be found
			else
				return true;
			
			//Exit without error, doesn't need to be logged.
			if(player == null)
				return true;
				
		} else if(cmd.getName().equalsIgnoreCase("pardonip")){
			String address = null;
			
			if(args.length > 0){
				address = getAddress(args[0], sender);
			}else
				return false;
			
			//IP could be found
			if(address != null){
				if(!sqlc.isAddressBanned(address)){
					sender.sendMessage(this.logPrefix + address + " is not banned.");
					return true;
				}
				sqlc.pardonIP(address);
				sender.sendMessage(this.logPrefix + "You have unbanned " + address);
			}
			
			return true;
			

		} else if(cmd.getName().equalsIgnoreCase("comment")){
			type = BanList.COMMENT;
			
			//Don't allow empty comments with this one
			if(args.length < 2)
				return false;
		} else if(cmd.getName().equalsIgnoreCase("probate")){
			
			boolean error = false;
			
			int minutes = 0,
				hours = 0,
				days = 0,
				months = 0;

			Calendar endTime = Calendar.getInstance();
			
			if(args.length > 1){
				type = BanList.PROBATE;
				reasonStart = 2;

    			try{
    				int num = Integer.parseInt(args[1]);
    				boolean setHour = true;
    				
    				if(args.length > 2){
    					reasonStart = 3;
    					
    					setHour = false;
    					
    					//Add the number based on specifier
    					if(args[2].equals("minutes"))
    						endTime.add(Calendar.MINUTE, num);
    					else if(args[2].equals("minute"))
    						endTime.add(Calendar.MINUTE, num);
    					else if(args[2].equals("mins"))
    						endTime.add(Calendar.MINUTE, num);
    					else if(args[2].equals("min"))
    						endTime.add(Calendar.MINUTE, num);
    					else if(args[2].equals("hours"))
    						endTime.add(Calendar.HOUR_OF_DAY, num);
    					else if(args[2].equals("hour"))
    						endTime.add(Calendar.HOUR_OF_DAY, num);
    					else if(args[2].equals("day"))
    						endTime.add(Calendar.DATE, num);
    					else if(args[2].equals("days"))
    						endTime.add(Calendar.DATE, num);
    					else if(args[2].equals("week"))
    						endTime.add(Calendar.DATE, num*7);
    					else if(args[2].equals("weeks"))
    						endTime.add(Calendar.DATE, num*7);
    					else if(args[2].equals("month"))
    						endTime.add(Calendar.MONTH, num);
    					else if(args[2].equals("months"))
    						endTime.add(Calendar.MONTH, num);
    					else if(args[2].equals("year"))
    						endTime.add(Calendar.YEAR, num);
    					else if(args[2].equals("years"))
    						endTime.add(Calendar.YEAR, num);
    					else
    						setHour = true; //We want to set the hour, not add anything
    					
    				}
    				
    				if(setHour){

    					//The hour specified isn't valid
    					if(num < 0 || num > 23)
    						error = true;
						
						//If we're using 12 hour format
    					else if(num < 12){
        					if(num < endTime.get(Calendar.HOUR))
        						endTime.add(Calendar.HOUR, 12);
    						endTime.set(Calendar.HOUR, num);
    					}
    					
    					//If we're using 24 hour format
    					else{
    						endTime.set(Calendar.HOUR_OF_DAY, num);
        					if(num < endTime.get(Calendar.HOUR_OF_DAY))
        						endTime.add(Calendar.DATE, 1);
    					}
    					
						endTime.set(Calendar.MINUTE, 0);//Hour should be set to 0
    				}
    			}catch(NumberFormatException e){
        			int i = 1;
    				if(args[1].equals("next"))//If they said next (optional)
    					i++;
    				if(args.length > i){
    					boolean setToMidnight = false;
    					boolean dayOffset = false;
    					boolean monthOffset = false;
    					
    					//If the specifier is a month
    					if(args[i].toLowerCase().equals("january")){
    						monthOffset = true;
	    					months = Calendar.JANUARY - endTime.get(Calendar.MONTH);
    					}else if(args[i].toLowerCase().equals("february")){
    						monthOffset = true;
    						months = Calendar.FEBRUARY - endTime.get(Calendar.MONTH);
    					}else if(args[i].toLowerCase().equals("march")){
    						monthOffset = true;
    						months = Calendar.MARCH - endTime.get(Calendar.MONTH);
    					}else if(args[i].toLowerCase().equals("april")){
    						monthOffset = true;
    						months = Calendar.APRIL - endTime.get(Calendar.MONTH);
    					}else if(args[i].toLowerCase().equals("may")){
    						monthOffset = true;
    						months = Calendar.MAY - endTime.get(Calendar.MONTH);
    					}else if(args[i].toLowerCase().equals("june")){
    						monthOffset = true;
    						months = Calendar.JUNE - endTime.get(Calendar.MONTH);
    					}else if(args[i].toLowerCase().equals("july")){
    						monthOffset = true;
    						months = Calendar.JULY - endTime.get(Calendar.MONTH);
    					}else if(args[i].toLowerCase().equals("august")){
    						monthOffset = true;
    						months = Calendar.AUGUST - endTime.get(Calendar.MONTH);
    					}else if(args[i].toLowerCase().equals("steptember")){
    						monthOffset = true;
    						months = Calendar.SEPTEMBER - endTime.get(Calendar.MONTH);
    					}else if(args[i].toLowerCase().equals("october")){
    						monthOffset = true;
    						months = Calendar.OCTOBER - endTime.get(Calendar.MONTH);
    					}else if(args[i].toLowerCase().equals("november")){
    						monthOffset = true;
    						months = Calendar.NOVEMBER - endTime.get(Calendar.MONTH);
    					}else if(args[i].toLowerCase().equals("december")){
    						monthOffset = true;
    						months = Calendar.DECEMBER - endTime.get(Calendar.MONTH);
    					}else if(args[i].toLowerCase().equals("month")){
    						monthOffset = true;
    						months = 1;
	    				}
	    				
    					//If the specifier is a day
	    				else if(args[i].toLowerCase().equals("monday")){
	    					days = Calendar.MONDAY - endTime.get(Calendar.DAY_OF_WEEK);
	    					dayOffset = true;
	    				}else if(args[i].toLowerCase().equals("tuesday")){
	    					days = Calendar.TUESDAY - endTime.get(Calendar.DAY_OF_WEEK);
	    					dayOffset = true;
	    				}else if(args[i].toLowerCase().equals("wednesday")){
	    					days = Calendar.WEDNESDAY - endTime.get(Calendar.DAY_OF_WEEK);
	    					dayOffset = true;
	    				}else if(args[i].toLowerCase().equals("thursday")){
	    					days = Calendar.THURSDAY - endTime.get(Calendar.DAY_OF_WEEK);
	    					dayOffset = true;
	    				}else if(args[i].toLowerCase().equals("friday")){
	    					days = Calendar.FRIDAY - endTime.get(Calendar.DAY_OF_WEEK);
	    					dayOffset = true;
	    				}else if(args[i].toLowerCase().equals("saturday")){
	    					days = Calendar.SATURDAY - endTime.get(Calendar.DAY_OF_WEEK);
	    					dayOffset = true;
	    				}else if(args[i].toLowerCase().equals("sunday")){
	    					days = Calendar.SUNDAY - endTime.get(Calendar.DAY_OF_WEEK);
	    					dayOffset = true;
	    				}else if(args[i].toLowerCase().equals("tomorrow")){
	    					days = 1;
	    					dayOffset = true;
	    				}else if(args[i].toLowerCase().equals("week")){
	    					days = 7;
	    					dayOffset = true;
	    				}

    	    			//If they entered a date format
    					else if(args[1].contains("-") || args[1].contains("/")){
	    					
	    					String[] date = null;
	    					
	    					if(args[1].contains("-"))
	    						date = args[1].split("-");
	    					else
	    						date = args[1].split("/");
	    					
	    					//Make sure they entered enough info
	    					if(date.length > 2){
		    					//Check if the order isn't US format
	    						
		    					int[] order = {0,1,2}; // Month/Day/Year (US)
		    					
		    					if(Integer.parseInt(date[0]) > 2000)
			    					order = new int[]{1,2,0}; //Year/Month/Day (UK)
		    					
		    					int month = Integer.parseInt(date[order[0]]) - 1;
		    					int day = Integer.parseInt(date[order[1]]);
		    					int year = Integer.parseInt(date[order[2]]);
		    					
		    					if(month < Calendar.JANUARY || month > Calendar.DECEMBER)
		    						error = true;
		    					if(day < 1 || day > getDaysInMonth(month, (year % 4) == 0) )
		    						error = true;
		    					
		    					//Set the date
		    					endTime.set(Calendar.MONTH, month);
		    					endTime.set(Calendar.DATE, day);
		    					endTime.set(Calendar.YEAR, year);
		    					
		    					setToMidnight = true;//We should set the time to midnight
		    					i++;//Move the placement forward for where we should check for time
	    					}else
	    						error = true;
	    					
	    				} else if(args[i].contains(":")){}//To make sure not to skip time
	    				else
	    					error = true;
    											
	    				//If they entered a time format
    	    			//This is outside the if/then block because it can occur in two places
    	    			//One of them being after one of the other members of that block
	    				if(args.length > i && args[i].contains(":") && !error){
							String[] time = args[i].split(":");

							//Make sure it really is a time format and not a smiley
							if(time.length > 1){
								hours = Integer.parseInt(time[0]);
								minutes = Integer.parseInt(time[1]);
								
								//If we didn't already set a day, we might need to be setting to tomorrow 
			
								//Hours isn't a valid number
								if( hours < 0 || hours > 23)
									error = true;
								
								//If using 12 hour format
								else if(hours < 12){
									endTime.set(Calendar.HOUR, hours);
									if(!setToMidnight)
										if(hours < endTime.get(Calendar.HOUR))
											endTime.add(Calendar.HOUR, 12);
								}
								
								//If using 24 hour format
								else{
									endTime.set(Calendar.HOUR_OF_DAY, hours);
									if(!setToMidnight)
										if(hours < endTime.get(Calendar.HOUR_OF_DAY))
											endTime.add(Calendar.DATE, 1);
								}
			
								//Make sure minute specified is valid
								if(minutes < 0 || minutes > 59)
									error = true;
								else
			    					endTime.set(Calendar.MINUTE, minutes);
								
								setToMidnight = false;//Making sure not to set to midnight

							} else if(i == 1)//Smilies can only happen after args[1] though, it's an error otherwise
								error = true;
							
	    					reasonStart = i + 1;
	    				}

    					//Finish the month offset
    	    			if(monthOffset){
    						if(months > 0)
    							endTime.add(Calendar.MONTH, months);
    						else
    							endTime.add(Calendar.MONTH, 12 + months);
    						
    		    			endTime.set(Calendar.DATE, 1);
    	    				setToMidnight = true;
    	    			}
    	    			
    	    			//Finish the day offset
    	    			if(dayOffset){
    						if(days > 0)
    							endTime.add(Calendar.DATE, days);
    						else
    							endTime.add(Calendar.DATE, 7 + days);
    						setToMidnight = true;
    	    			}
    	    			
    	    			//Sets the time to midnight for a lot of circumstances
    	    			if(setToMidnight){
    		    			endTime.set(Calendar.HOUR_OF_DAY, 0);
    		    			endTime.set(Calendar.MINUTE, 0);
    	    			}
    				}
    				
    			}
    			
    			endTime.set(Calendar.SECOND, 0);
    			endTime.set(Calendar.MILLISECOND, 0);
			}else
				error = true;

			endDate = new Timestamp(endTime.getTimeInMillis()); //Get the end date
			
			//End time is before current time
			if(endDate.before(new Timestamp(Calendar.getInstance().getTimeInMillis()))){
				sender.sendMessage(this.logPrefix + "The probation time specified has already expired.");
				return true;
			}
			
			//There was an error with reading the duration
			if(error){
				
				//Get the date entered from the arguments
				String[] dateArray = Arrays.copyOfRange(args, 1, reasonStart);
				String dateString = new String();
				for(String ds : dateArray)
					dateString += ds + " ";
				
				//remove the ending space
				if(!dateString.isEmpty())
					dateString = dateString.substring(0, dateString.length() - 1);
					
				sender.sendMessage(this.logPrefix + "Date could not be read: " + dateString);
				return true;
			}
			sqlc.setProbate(player, endDate); //Set the probation
		} else if(cmd.getName().equalsIgnoreCase("rapsheet")){
			
			int numPages = 1; //Number of pages
			int page = 1; //The current page
			AdminAction[] rapsheet = null;
			
			if(player == null)
				if(args.length > 0)
					page = Integer.parseInt(args[0]);
			else
				if(args.length > 1)
					page = Integer.parseInt(args[1]);
			
			
			int count = 0;
			if(player != null){
				if(args.length > 1)
					page = Integer.parseInt(args[1]);
				count = sqlc.getCountActions(player.getName());
				rapsheet = sqlc.getActionPage(player.getName(), page, BanList.RESULTS_PER_PAGE);
			}
			else{
				if(args.length > 0)
					page = Integer.parseInt(args[0]);
				count = sqlc.getCountActions();
				rapsheet = sqlc.getActionPage(page, BanList.RESULTS_PER_PAGE);
			}
			
			numPages = count / BanList.RESULTS_PER_PAGE;
			if(count % BanList.RESULTS_PER_PAGE > 0)
				numPages++;
	
			//Print the header
			String pageInfo = ChatColor.GREEN + " (" + ChatColor.WHITE + page + "/" + numPages + ChatColor.GREEN + ")";
			String header = new String();
			
			if(player != null){
				sender.sendMessage(player.getName() + "'s Rapsheet" + pageInfo);
				header = String.format(ChatColor.GREEN + "%-23s %-19s %-12s %s", 
						"Date of Action", "IP", "Action", "Moderator");
			}else{
				sender.sendMessage("Crime Report" + pageInfo);
				header = String.format(ChatColor.GREEN + "%-22s %-14s %-13s %s", 
						"Date of Action", "Horrible Jerk", "Action", "Moderator");
			}
			sender.sendMessage(header);
			
			if(numPages < 1)
				sender.sendMessage("No results to display.");
			
			for(AdminAction rs : rapsheet){
				String rsPage = new String();
				if(rs == null)
					break;
				
				//No need to display seconds or milliseconds
				String timeString = rs.actionTime.toString();
				String[] timeArray = timeString.split(":");
				timeString = timeArray[0] + ":" + timeArray[1];
				
				if(player != null){
					rsPage = String.format("%-19s %-15s %-9s %s", 
							timeString, rs.playerIP, getType(rs.actionType), rs.modName);
				}else{
					rsPage = String.format("%-19s %-12s %-9s %s", 
							timeString, rs.playerName, getType(rs.actionType), rs.modName);
				}
				sender.sendMessage(rsPage.replace("   ", "     "));
				if(!rs.actionReason.isEmpty())
					sender.sendMessage(ChatColor.GREEN + "Comment: " + ChatColor.WHITE + rs.actionReason);
			}
			
			return true;
		}
		
		//Record the action taken
		if(type > BanList.NONE && player != null){
			String reason = new String();
			
	    	//Get the comment
			if(args.length > reasonStart){
	    		reasonArray = Arrays.copyOfRange(args, 1, args.length);
		        for(String st : reasonArray)
		        	reason += st + " ";
		        reason = reason.substring(0, reason.length()-1);
			}

			//Display in console
			String logString = this.logPrefix + sender.getName() + " has " + getTypePast(type) + " " + player.getName();
			String kickString = "You have been " + getTypePast(type);
			if(type == BanList.PROBATE){
				logString += " for " + getTimeRemaining(endDate);
				kickString += " for " + getTimeRemaining(endDate);
			}
			if(!reason.isEmpty()){
				logString += " \"" + reason + "\"";
				kickString += " \"" + reason + "\"";
			}
			this.log.info(logString); //Log the action
			
			//Kick the player if they're online
			if(player != null && type != BanList.COMMENT && player instanceof Player)
				((Player) player).kickPlayer(kickString); //Kick the player if online
			
    		sqlc.sendAction(player, type, sender.getName(), reason); //Send the update
			return true;
		}
		
		return false; //Return false at the very end.
	}
	
	/**
	 * Returns a string based on type
	 * @param type The type of command issued
	 * @return
	 */
	protected static String getType(int type){
		switch (type) {
            case BanList.BAN:  return "Ban";
            case BanList.UNBAN:  return "Unban";
            case BanList.PROBATE:  return "Probate";
            case BanList.KICK:  return "Kick";
            case BanList.IPBAN:  return "IP Ban";
            case BanList.COMMENT:  return "Comment";
            default: return "Unknown Type";
		}
	}
	
	/**
	 * Returns a past tense string based on type
	 * @param type The type of command issued
	 * @return
	 */
	protected static String getTypePast(int type){
		switch (type) {
            case BanList.BAN:  return "banned";
            case BanList.UNBAN:  return "unbanned";
            case BanList.PROBATE:  return "probated";
            case BanList.KICK:  return "kicked";
            case BanList.IPBAN:  return "IP banned";
            case BanList.COMMENT:  return "commented";
            default: return "Unknown Type";
		}
	}
	
	/**
	 * Gets the remaining time in a readable format
	 * @param time The ending time
	 * @return # years, # months, # weeks, # days, # hours, # minutes
	 */
	private static String getTimeRemaining(Timestamp time){
		Calendar currentTime = Calendar.getInstance();
		Calendar endTime = Calendar.getInstance();
		endTime.setTimeInMillis(time.getTime());
		
		int year	= 0,
			month	= 0,
			week	= 0,
			day		= 0,
			hour	= 0,
			minute	= 0;

		//Get the time remaining
		year = endTime.get(Calendar.YEAR) - currentTime.get(Calendar.YEAR);
		month = endTime.get(Calendar.MONTH) - currentTime.get(Calendar.MONTH);
		day = endTime.get(Calendar.DATE) - currentTime.get(Calendar.DATE);
		hour = endTime.get(Calendar.HOUR_OF_DAY) - currentTime.get(Calendar.HOUR_OF_DAY);
		minute = endTime.get(Calendar.MINUTE) - currentTime.get(Calendar.MINUTE);
		
		//Parse the remaining time
		
		//If the minutes remaining is less than 0\
		if(minute < 0){
			minute += 60;
			hour--;
		}
		
		//If the hours remaining is less than 0 
		if(hour < 0){
			hour += 24;
			day--;
		}
		
		//If the days remaining is less than 0
		if(day < 0){
			
			//Add the number of days in the current month
			day += getDaysInMonth(currentTime.get(Calendar.MONTH), (currentTime.get(Calendar.YEAR) % 4) == 0);
			
			//Get the weeks remaining
			if(day > 0){
				week = day % 7;
				day = day / 7;
			}
			month--;
		}
		
		//If the number of months remaining is less than 0
		if(month < 0){
			month += 12;
			year--;
		}
		
		//If the number of years remaining is less than 0 then time is up
		if(year < 0){
			year = 0;
			month = 0;
			week = 0;
			day = 0;
			hour = 0;
			minute = 0;
		}
    	
		//Convert to readable format
		String output = new String();
		if(year > 0 ){
			output += ", " + year + " year";
			if(year > 1)
				output += "s";
		}
		if(month > 0 ){
			output += ", " + month + " month";
			if(month > 1)
				output += "s";
		}
		if(week > 0 ){
			output += ", " + month + " week";
			if(week > 1)
				output += "s";
		}
		if(day > 0 ){
			output += ", " + day + " day";
			if(day > 1)
				output += "s";
		}
		if(hour > 0 ){
			output += ", " + hour + " hour";
			if(hour > 1)
				output += "s";
		}
		if(minute > 0 ){
			output += ", " + minute + " minute";
			if(minute > 1)
				output += "s";
		}
		if(!output.isEmpty())
			output = output.substring(2);
    	return output;
	}
	
	/**
	 * The number of days in the specified month
	 * @param month The month to look up
	 * @param leap If it's a leap year or not
	 * @return
	 */
	private static int getDaysInMonth(int month, boolean leap){

		int day = 0;
		
		switch (month){
			case Calendar.FEBRUARY:
				day += 28;
				if(leap) //Don't forget leap years
					day += 1;
				break;
			case Calendar.APRIL:
				day += 30;
				break;
			case Calendar.JUNE:
				day += 30;
				break;
			case Calendar.SEPTEMBER:
				day += 30;
				break;
			case Calendar.NOVEMBER:
				day += 30;
				break;
			default:
				day += 31;
				break;
		}
		
		return day;
	}
	
	private OfflinePlayer getPlayer(String name){
		try{
			Integer.parseInt(name);
			return null;
		} catch(NumberFormatException e) {
			
			if(name.contains("."))
				return null;
			
			String playerName = name.toLowerCase();
			OfflinePlayer player = Bukkit.getServer().getPlayer(playerName);
			if(player == null)
				player = sqlc.getOfflinePlayer(playerName);
			
			return player;
		}
	}
	
	private String getAddress(String nameOrIP, CommandSender sender){
		
		String address = null;
		
		//The sender specified an IP address
		if(nameOrIP.contains(".")){
			
			boolean invalidIp = false;
			
			String[] addressparts = nameOrIP.split("\\.");
			
			//Not enough parts
			if(addressparts.length != 4)
				invalidIp = true;
			else{
				try{
					
					//Make sure each part of the address is a number
					for(String part: addressparts){
						log.info(part);
						//Make sure the numbers are valid
						if(Integer.parseInt(part) > 254 || Integer.parseInt(part) < 0)
							invalidIp = true;
					}
				
				//One of the parts of the IP wasn't a number
				}catch(NumberFormatException e){
					invalidIp = true;
				}
			}
			
			//Exit because the IP address they wanted to use wasn't valid.
			if(invalidIp)
				sender.sendMessage(this.logPrefix + "The ip you specified is not valid. \"" + nameOrIP + "\"");
			else
				address = nameOrIP;

		}else{
			
			OfflinePlayer player = getPlayer(nameOrIP);
			
			//If the player is online, get their current IP
			if(player instanceof Player)
				nameOrIP = ((Player) player).getAddress().getHostName();
			
			//If the player is offline, get their last used IP
			else
				address = sqlc.getLastIP(player);
			
			if(address == null)
				sender.sendMessage(this.logPrefix + "The ip address for the player you specified could not be determined.");
				
		}
		
		return address;
	}
}