package me.eighttenlabs.steazy;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Class to wrap a playlist
 * <p/>
 * Created by Ian on 6/22/2015.
 */
public class Playlist {

    private int id;
    private String name;
    private String owner;
    private int ownerId;
    private ArrayList<Song> songs;
    private String createDate;

    public Playlist(int id, String name, String owner, int ownerId, ArrayList<Song> songs,
                    String createDate) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.ownerId = ownerId;
        this.songs = songs;
        this.createDate = createDate;
    }

    public static Playlist PlaylistFromJSON(JSONObject playlistJSON) {
        try {
            int id = playlistJSON.getInt("id");
            String name = playlistJSON.getString("name");
            String owner = playlistJSON.getString("owner_name");
            int ownerId = playlistJSON.getInt("owner");
            String createDate = playlistJSON.getString("data_created");
            JSONArray songs = playlistJSON.getJSONArray("songs");
            ArrayList<Song> songList = new ArrayList<>();
            for (int i = 0; i < songs.length(); i++) {
                songList.add(Song.songFromJSON(songs.optJSONObject(i)));
            }
            return new Playlist(id, name, owner, ownerId, songList, createDate);
        } catch (JSONException e) {
            Log.d("JSON Error", "Unable to parse playlist");
            return null;
        }
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getOwner() {
        return owner;
    }

    public ArrayList<Song> getSongs() {
        return songs;
    }

    public String getCreateDate() {
        return createDate;
    }

}