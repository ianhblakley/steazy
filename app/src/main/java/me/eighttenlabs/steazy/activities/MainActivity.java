package me.eighttenlabs.steazy.activities;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import com.android.volley.Response;
import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.Spotify;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import me.eighttenlabs.steazy.MusicService;
import me.eighttenlabs.steazy.PlaylistAdapter;
import me.eighttenlabs.steazy.R;
import me.eighttenlabs.steazy.SongAdapter;
import me.eighttenlabs.steazy.networking.Requests;
import me.eighttenlabs.steazy.wrappers.Playlist;
import me.eighttenlabs.steazy.wrappers.Song;

import static me.eighttenlabs.steazy.wrappers.Song.songFromJSON;


public class MainActivity extends AppCompatActivity {

    public static final String SPOTIFY_CLIENT_ID = "e0fd082de90e4cd7b60bf6047f5033f0";
    public static final String SOUNDCLOUD_CLIENT_ID = "81ca87317b91e4051f6d8797e5cce358";
    public static final String SPOTIFY_CALLBACK = "steazy://callback";
    public static final int REQUEST_CODE = 1337;

    ArrayList<Song> searchedSongs;
    private MusicService musicService;
    private boolean musicBound;
    private Intent playIntent;

    private Playlist displayingPlaylist;

    private ImageButton playPauseButton;
    private TextView songName;
    private TextView songArtist;
    private ListView listView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // UI setup
        ImageButton skipButton;
        ImageButton previousButton;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        skipButton = (ImageButton) findViewById(R.id.next_button);
        previousButton = (ImageButton) findViewById(R.id.previous_button);
        playPauseButton = (ImageButton) findViewById(R.id.play_pause_button);
        songName = (TextView) findViewById(R.id.song_title);
        songArtist = (TextView) findViewById(R.id.song_artist);
        listView = (ListView) findViewById(R.id.songs);

        playPauseButton.setImageResource(R.drawable.ic_action_play);
        playPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (musicService.getQueue() == null || musicService.getQueue().size() == 0){
                    return;
                }
                if (musicService.isPlaying()) {
                    musicService.pause();
                    playPauseButton.setImageResource(R.drawable.ic_action_play);
                } else {
                    musicService.start();
                    playPauseButton.setImageResource(R.drawable.ic_action_pause);
                }
                togglePlay();
            }
        });
        skipButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                musicService.playNext();
            }
        });
        previousButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                musicService.playPrevious();
            }
        });

        // Send auth request to Spotify for playback
        requestSpotify();

        // Holds songs to display
        searchedSongs = new ArrayList<>();

        displayingPlaylist = null;

        // Sets up the playback service
        setupService();
    }

    private void setupService() {
        ServiceConnection musicConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
                musicService = binder.getService();
                setMusicBound(true);
                musicService.setActivity(MainActivity.this);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                musicService = null;
                setMusicBound(false);
            }
        };
        playIntent = new Intent(getApplicationContext(), MusicService.class);
        getApplicationContext().bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
        getApplicationContext().startService(playIntent);
    }

    public void requestSpotify() {
        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(SPOTIFY_CLIENT_ID, AuthenticationResponse.Type.TOKEN, SPOTIFY_CALLBACK);
        builder.setScopes(new String[]{"streaming"});
        AuthenticationRequest request = builder.build();
        AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);
    }

    private void setSongList() {
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                songPicked(view);
            }
        });
        SongAdapter songAdapter = new SongAdapter(getApplicationContext(), searchedSongs);
        listView.setAdapter(songAdapter);
        registerForContextMenu(listView);
        invalidateOptionsMenu();
    }

    private void setPlaylistList() {
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Playlist playlist = Playlist.getPlaylist().get(Integer.parseInt(view.getTag().toString()));
                searchedSongs = playlist.getSongs();
                setSongList();
                displayingPlaylist = playlist;
            }
        });
        PlaylistAdapter playlistAdapter = new PlaylistAdapter(getApplicationContext(), Playlist.getPlaylist());
        listView.setAdapter(playlistAdapter);
        registerForContextMenu(listView);
        invalidateOptionsMenu();
        displayingPlaylist = null;
    }

    public void togglePlay() {
        if (musicService.isPlaying()) {
            playPauseButton.setImageResource(R.drawable.ic_action_pause);
        } else {
            playPauseButton.setImageResource(R.drawable.ic_action_play);
        }
    }

    public void songPicked(View view) {
        play(Integer.parseInt(view.getTag().toString()));
    }

    @Override
    protected void onActivityResult(int requestCode, int responseCode, Intent intent) {
        super.onActivityResult(requestCode, responseCode, intent);
        getUserPlaylists();
        if (requestCode == REQUEST_CODE) {
            AuthenticationResponse response = AuthenticationClient.getResponse(responseCode, intent);
            if (response.getType() == AuthenticationResponse.Type.TOKEN) {
                Config playerConfig = new Config(getApplicationContext(), response.getAccessToken(), SPOTIFY_CLIENT_ID);
                Spotify.getPlayer(playerConfig, musicService, new Player.InitializationObserver() {
                    @Override
                    public void onInitialized(Player player) {
                        musicService.setPlayers(player);
                    }
                    @Override
                    public void onError(Throwable throwable) {
                    }
                });
            }
        }
    }

    public void onSongChanged(Song song) {
        songName.setText(song.name);
        songArtist.setText(song.artists[0]);
        playPauseButton.setImageResource(R.drawable.ic_action_pause);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        playIntent = null;
        super.onDestroy();
        if (musicBound) {
            musicService.onDestroy();
            setMusicBound(false);
            musicService = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main_song, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (listView.getAdapter() instanceof SongAdapter) {
            menu.clear();
            getMenuInflater().inflate(R.menu.menu_main_song, menu);
        } else if (listView.getAdapter() instanceof PlaylistAdapter) {
            menu.clear();
            getMenuInflater().inflate(R.menu.menu_main_playlist, menu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.action_search_songs:
                if (android.os.Build.VERSION.SDK_INT < 11) {
                    supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
                }
                if (getSupportActionBar() != null && getSupportActionBar().getCustomView() != null) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(
                            Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(getSupportActionBar().getCustomView().findViewById(R.id.search_box).getWindowToken(), 0);
                    getSupportActionBar().setCustomView(null);
                    getSupportActionBar().setDisplayShowCustomEnabled(false);
                } else if (getSupportActionBar() != null) {
                    getSupportActionBar().setDisplayShowCustomEnabled(true);
                    getSupportActionBar().setCustomView(R.layout.action_bar_search);
                    final EditText box = ((EditText) getSupportActionBar().getCustomView().findViewById(R.id.search_box));
                    box.requestFocus();
                    box.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
                    final InputMethodManager imm = (InputMethodManager) getSystemService(
                            Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(box, 0);
                    box.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                        @Override
                        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                                try {
                                    searchSongs(box.getText().toString());
                                    imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    return false;
                                }
                            }
                            return true;
                        }
                    });
                    box.addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {}

                        @Override
                        public void afterTextChanged(Editable s) {
                            fastSearch(s.toString());
                        }
                    });

                }
                break;

            case R.id.action_show_queue:
                getUserPlaylists();
                setPlaylistList();
                break;

            case R.id.action_new_playlist:
                newPlaylistDialog();
                break;

            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        if (listView.getAdapter() instanceof PlaylistAdapter) {
            inflater.inflate(R.menu.context_menu_main_playlist, menu);
        } else if (displayingPlaylist != null) {
            inflater.inflate(R.menu.context_menu_main_playlist_songs, menu);
        } else {
            inflater.inflate(R.menu.context_menu_main_search_songs, menu);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int position = ((AdapterView.AdapterContextMenuInfo) item.getMenuInfo()).position;
        Song song = null;
        Playlist playlist = null;
        if (listView.getAdapter() instanceof SongAdapter) {
            song = searchedSongs.get(position);
        } else if (listView.getAdapter() instanceof PlaylistAdapter) {
            playlist = Playlist.getPlaylist().get(position);
        }
        switch (item.getItemId()) {
            case R.id.play_song:
                if (song == null) {
                    throw new AssertionError("Should only be called on song");
                }
                play(position);
                return true;
            case R.id.queue_song:
                if (song == null) {
                    throw new AssertionError("Should only be called on song");
                }
                queue(song);
                return true;
            case R.id.add_song_to_playlist:
                if (song == null) {
                    throw new AssertionError("Should only be called on song");
                }
                addSongDialog(song);
                return true;
            case R.id.remove_song_from_playlist:
                if (song == null) {
                    throw new AssertionError("Should only be called on song");
                }
                displayingPlaylist.deleteSong(song);
                return true;
            case R.id.context_menu_playlist_rename:
                if (playlist == null) {
                    throw new AssertionError("Should only be called on playlist");
                }
                renamePlaylistDialog(playlist);
                return true;
            case R.id.context_menu_playlist_delete:
                if (playlist == null) {
                    throw new AssertionError("Should only be called on playlist");
                }
                deletePlaylistDialog(playlist);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    public void getUserPlaylists() {
        Requests.getPlaylists(new Response.Listener<JSONArray>() {
            @Override
            public void onResponse(JSONArray jsonArray) {
                ArrayList<Playlist> playlists = new ArrayList<>();
                try {
                    for (int i = 0; i < jsonArray.length(); i++) {
                        playlists.add(Playlist.PlaylistFromJSON(jsonArray.getJSONObject(i)));
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                Playlist.setPlaylist(playlists);
            }
        });
    }

    public void searchSongs(String query) {
        Requests.search(query, Requests.SEARCH_ALL, new Response.Listener<JSONArray>() {
            @Override
            public void onResponse(JSONArray response) {
                ArrayList<Song> songs1 = new ArrayList<>();
                try {
                    for (int i = 0; i < response.length(); i++) {
                        songs1.add(songFromJSON(response.getJSONObject(i)));
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                MainActivity.this.searchedSongs = songs1;
                setSongList();
                displayingPlaylist = null;
            }
        });
    }

    public void fastSearch(String query) {
        Requests.search(query, Requests.SEARCH_DATABASE, new Response.Listener<JSONArray>() {
            @Override
            public void onResponse(JSONArray response) {
                ArrayList<Song> songs1 = new ArrayList<>();
                try {
                    for (int i = 0; i < response.length(); i++) {
                        songs1.add(songFromJSON(response.getJSONObject(i)));
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                MainActivity.this.searchedSongs = songs1;
                setSongList();
                displayingPlaylist = null;
            }
        });
    }

    public void setMusicBound(boolean musicBound) {
        this.musicBound = musicBound;
    }

    private void play(int pos) {
        musicService.setSongs(searchedSongs);
        musicService.setQueuePosition(pos);
        musicService.playSong();
    }

    private void queue(Song song) {
        musicService.queue.add(musicService.getQueuePosition() + 1, song);
    }

    private void addSongDialog(final Song song){
        if (Playlist.getPlaylist() == null) {
            getUserPlaylists();
            return;
        }
        final ArrayList<Playlist> playlists = Playlist.getPlaylist();
        String[] playlistNames = new String[playlists.size()];
        for (int i=0;i<playlists.size();i++) {
            playlistNames[i] = playlists.get(i).getName();
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select a Playlist").setItems(playlistNames, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Requests.addSongToPlaylist(song.getId(), playlists.get(which).getId());
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void newPlaylistDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.create_playlist_title));
        View layout = getLayoutInflater().inflate(R.layout.text_dialog, null);
        builder.setView(layout);
        final EditText nameBox = (EditText) layout.findViewById(R.id.dialog_box);
        Button cancelButton = (Button) layout.findViewById(R.id.dialog_cancel_button);
        Button createButton = (Button) layout.findViewById(R.id.dialog_confirm_button);
        final AlertDialog dialog = builder.create();
        nameBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                Requests.postPlaylist(v.getText().toString(), new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject jsonObject) {
                        ArrayList<Playlist> current = Playlist.getPlaylist();
                        current.add(Playlist.PlaylistFromJSON(jsonObject));
                        Playlist.setPlaylist(current);
                        setPlaylistList();
                    }
                });
                dialog.dismiss();
                return true;
            }
        });
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        createButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Requests.postPlaylist(nameBox.getText().toString(), new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject jsonObject) {
                        ArrayList<Playlist> current = Playlist.getPlaylist();
                        current.add(Playlist.PlaylistFromJSON(jsonObject));
                        Playlist.setPlaylist(current);
                        setPlaylistList();
                    }
                });
                dialog.dismiss();
            }
        });
        createButton.setText(getString(R.string.create_playlist_create));
        dialog.show();
        nameBox.requestFocus();
    }

    private void renamePlaylistDialog(final Playlist playlist) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.rename_playlist_title));
        View layout = getLayoutInflater().inflate(R.layout.text_dialog, null);
        builder.setView(layout);
        final EditText nameBox = (EditText) layout.findViewById(R.id.dialog_box);
        Button cancelButton = (Button) layout.findViewById(R.id.dialog_cancel_button);
        Button renameButton = (Button) layout.findViewById(R.id.dialog_confirm_button);
        final AlertDialog dialog = builder.create();
        nameBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                Requests.putPlaylist(playlist.getId(), nameBox.getText().toString(), new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject jsonObject) {
                        playlist.setName(nameBox.getText().toString());
                        setPlaylistList();
                    }
                });
                dialog.dismiss();
                return true;
            }
        });
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        renameButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Requests.putPlaylist(playlist.getId(), nameBox.getText().toString(), new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject jsonObject) {
                        playlist.setName(nameBox.getText().toString());
                        setPlaylistList();
                    }
                });
                dialog.dismiss();
            }
        });
        renameButton.setText(getString(R.string.rename_playlist_rename));
        dialog.show();
        nameBox.requestFocus();
    }

    public void deletePlaylistDialog(final Playlist playlist) {
        new AlertDialog.Builder(this).setTitle(getString(R.string.delete_playlist_title))
                .setMessage(getString(R.string.delete_playlist_delete)).
                setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Requests.deletePlaylist(playlist.getId(), null);
                        Playlist.removePlaylist(playlist.getId());
                        setPlaylistList();
                    }
                }).setNegativeButton(R.string.no, null).show();
    }
}