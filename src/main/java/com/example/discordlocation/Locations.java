package com.example.discordlocation;



import java.sql.Connection;

public class Notes {

    public static Connection conn;

    public void initConn(){
        try{
            SqliteDB.connect("");
            conn = SqliteDB.connect();
        }
    }

}
