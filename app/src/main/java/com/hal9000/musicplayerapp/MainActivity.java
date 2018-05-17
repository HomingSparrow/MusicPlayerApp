package com.hal9000.musicplayerapp;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.ToggleButton;

import com.google.gson.Gson;

import java.io.File;
import java.util.ArrayList;

import static com.hal9000.musicplayerapp.MediaPlayerService.LOG_FILES_TAG;

public class MainActivity extends AppCompatActivity {

    ArrayList<String> songsPaths = null;

    // Requesting permission to READ_EXTERNAL_STORAGE
    private boolean permissionToReadExtStorageAccepted = false;
    private String [] permissions = {Manifest.permission.READ_EXTERNAL_STORAGE};
    private static final int REQUEST_READ_EXTERNAL_STORAGE_PERMISSION_ID = 100;
    private static final String KEY_SONGS_PATHS = "songs_paths";
    private static final String KEY_LEFT_WHILE_PLAYING = "left_while_playing";

    //song list variables
    //private ArrayList<Song> songList;
    //private ListView songView;
    private ToggleButton playPauseButton = null;

    //service
    private MediaPlayerService mService;
    private Intent playIntent;
    //binding
    private boolean musicBound=false;
    private boolean leftWhilePlaying=false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initToolbar();


        ActivityCompat.requestPermissions(this, permissions, REQUEST_READ_EXTERNAL_STORAGE_PERMISSION_ID);


        playPauseButton = findViewById(R.id.toggleButton_play_pause);
        playPauseButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    //Toast.makeText(MainActivity.this, "Service not started", Toast.LENGTH_SHORT).show();
                    if (leftWhilePlaying)
                        leftWhilePlaying = false;
                    else
                        mService.playSong(songsPaths.get(1118));
                }
                else
                {
                    //Toast.makeText(MainActivity.this, "Service not stopped", Toast.LENGTH_SHORT).show();
                    mService.pausePlaying();
                }
            }
        });

        restoreState(); // TODO should be before buttons
    }

    private void processMusicFiles() {
        File[] files = getExternalFilesDirs(Environment.MEDIA_MOUNTED);
        String folderPath = "";
        for (int i = files.length - 1; i >= 0 && folderPath.length() == 0; i--){
            String tempStr = files[i].getAbsolutePath();
            tempStr = tempStr.substring(0, tempStr.indexOf("/Android/")) + "/Music/";
            //Log.d(LOG_FILES_TAG, "Path_temp: " + tempStr);
            if (new File(tempStr).exists())
                folderPath = tempStr;
        }
        //Log.d(LOG_FILES_TAG, "Path: " + folderPath);
/*  DO THAT IN ADAPTER
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        mmr.setDataSource(path);
        String authorName = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
        */
        File file = new File(folderPath);
        files = file.listFiles();
        int songsCount = getFilesCount(file);
        songsPaths = new ArrayList<String>(songsCount+50);
        addSongs(file);

        Log.d(LOG_FILES_TAG, "Size: " + files.length);
        for (int i=0; i<songsPaths.size(); i++){
            String path = songsPaths.get(i);
            Log.d(LOG_FILES_TAG, i + ": File path: " + path);
        }
    }

    public int getFilesCount(File dir) {
        File[] files = dir.listFiles();
        int count = 0;
        for (File f : files)
            if (f.isDirectory())
                count += getFilesCount(f);
            else
                count++;

        return count;
    }

    public void addSongs(File dir) {
        File[] files = dir.listFiles();
        for (File f : files)
            if (f.isDirectory())
                getFilesCount(f);
            else
                songsPaths.add(f.getAbsolutePath());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case REQUEST_READ_EXTERNAL_STORAGE_PERMISSION_ID:
                permissionToReadExtStorageAccepted  = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionToReadExtStorageAccepted ) finish();
        else {
            if (playIntent==null) bindAndStartSrv();
            if (songsPaths==null) restoreState();
        }
    }

    //start and bind the service when the activity starts
    @Override
    protected void onStart() {
        super.onStart();
        if(playIntent==null && permissionToReadExtStorageAccepted){
            bindAndStartSrv();
        }
    }

    private void bindAndStartSrv() {
        playIntent = new Intent(this, MediaPlayerService.class);
        bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE); // Context.BIND_AUTO_CREATE
        startService(playIntent);
    }

    private void initToolbar() {
        Toolbar displayActToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(displayActToolbar);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.about:

                return true;

            case R.id.settings:

                //Intent fullScrAuthorInfoIntent = new Intent(this, FullscreenActivityAuthorDisplay.class);
                //this.startActivity(fullScrAuthorInfoIntent);

                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);
        }
    }

    //connect to the service
    private ServiceConnection musicConnection = new ServiceConnection(){

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MediaPlayerService.MusicBinder binder = (MediaPlayerService.MusicBinder)service;
            //get service
            mService = binder.getService();
            //pass list
            //mService.setList(songList);
            musicBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicBound = false;
        }
    };

    @Override
    public void onStop(){
        super.onStop();
        saveSongsArrayState();
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        if (!mService.isPlaying())
            MediaPlayerService.stop(MainActivity.this);
        else {
            leftWhilePlaying = true;
            savePlayPauseState();
        }
    }

    private void savePlayPauseState(){
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor prefsEditor = sharedPref.edit();
        prefsEditor.putBoolean(KEY_LEFT_WHILE_PLAYING, leftWhilePlaying);
        prefsEditor.apply();
    }

    private void saveSongsArrayState() {
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor prefsEditor = sharedPref.edit();
        Gson gson = new Gson();
        String jsonText = gson.toJson(songsPaths);
        prefsEditor.putString(KEY_SONGS_PATHS, jsonText);
        prefsEditor.apply();
    }

    private void restoreState(){
        // Restore songs array
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        if (!permissionToReadExtStorageAccepted)
            return;

        if(sharedPref.contains(KEY_SONGS_PATHS)) {
            Gson gson = new Gson();
            String jsonText = sharedPref.getString(KEY_SONGS_PATHS, null);
            songsPaths = gson.fromJson(jsonText, ArrayList.class);
        }
        else
            processMusicFiles();

        // Restore play / pause button state
        leftWhilePlaying = sharedPref.getBoolean(KEY_LEFT_WHILE_PLAYING, false);
        if(leftWhilePlaying)
            playPauseButton.setChecked(!playPauseButton.isChecked());

        SharedPreferences.Editor preferencesEditor = sharedPref.edit();
        preferencesEditor.clear();
        preferencesEditor.apply();
    }
}