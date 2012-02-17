package es.jinki.BanList;

import java.sql.Timestamp;

/**
 * A rapsheet item.
 * @author cerevisiae
 * @version 1.0
 */
public class AdminAction {
	Timestamp actionTime;
	String playerName = new String();
	String playerIP = new String();
	int actionType;
	String modName = new String();
	String actionReason = new String();
	
	public AdminAction(Timestamp time, Timestamp end, String name, String ip, int type, String mod, String reason){
		actionTime = time;
		playerName = name;
		playerIP = ip;
		actionType = type;
		modName = mod;
		actionReason = reason;
	}
}
