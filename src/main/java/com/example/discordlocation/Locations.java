package com.example.discordlocation;



import java.sql.Connection;
import java.sql.Timestamp;

public class Locations {
    public class LocStamp{
        public int x;
        public int y;
        public int z;
        public String user;
        public String name;
        public Timestamp created_at;
    }

    public static Connection conn;
    public DiscordLocationPlugin plugin = DiscordLocationPlugin.getInstance();

    public Locations(){
        try{
            SqliteDB.connect("Locations");
            conn = SqliteDB.getConnection();
        }
        catch(Exception e){
            plugin.getLogger().info("Locations couldn't connect to DB");
        }
    }
    public
}
