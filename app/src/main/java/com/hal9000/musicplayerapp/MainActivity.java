package com.hal9000.musicplayerapp;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.gson.Gson;

import java.io.File;
import java.util.ArrayList;

import static com.hal9000.musicplayerapp.MediaPlayerService.LOG_FILES_TAG;
import static com.hal9000.musicplayerapp.SettingsActivity.GeneralPreferenceFragment.KEY_CHOOSE_MUSIC_DIR;
import static com.hal9000.musicplayerapp.SettingsActivity.wasPathModified;
import static com.hal9000.musicplayerapp.SettingsActivity.wereModified;

public class MainActivity extends AppCompatActivity {

    ArrayList<String> songsPaths = null;

    // Requesting permission to READ_EXTERNAL_STORAGE
    private boolean permissionToReadExtStorageAccepted = false;
    private String [] permissions = {Manifest.permission.READ_EXTERNAL_STORAGE};
    private static final int REQUEST_READ_EXTERNAL_STORAGE_PERMISSION_ID = 100;
    private static final String KEY_SONGS_PATHS = "songs_paths";
    private static final String KEY_LEFT_WHILE_PLAYING = "left_while_playing";
    public static final String KEY_SERVICE = "KEY_SERVICE";
    public static final String KEY_PLAY_INTENT = "KEY_PLAY_INTENT";
    public static final String LOG_ERR_TAG = "Err";

    //song list variables
    //private ArrayList<Song> songList;
    //private ListView songView;
    private ToggleButton playPauseButton = null;
    private ImageButton fastRewindButton = null;
    private ImageButton fastForwardButton = null;
    private TextView currentSongTitle = null;
    private SeekBar mSeekBar;

    //service
    private MediaPlayerService mService;
    private Intent playIntent;
    MediaPlayerService.MusicBinder binder;
    //binding
    private boolean musicBound = false;

    // Restore media player state info
    private boolean leftWhilePlaying = false;   // TODO implement one-time initialization class for this field (field should be read-only after first initialization not counting this line)
    private boolean playButtonUnusualState;     // for switching play/pause button state without any effects
    private boolean enteredSettings = false;
    private boolean isMoveForwadCompleted = true;

    private Handler requestHandler;
    private Runnable mRunnable;

    private RecyclerView mRecyclerView;
    private SongListRecAdapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(LOG_ERR_TAG, "onCreate() begin ");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        restoreMediaPlayerStateInfo();
        if (playIntent==null)
            bindAndStartSrv();

        ActivityCompat.requestPermissions(this, permissions, REQUEST_READ_EXTERNAL_STORAGE_PERMISSION_ID);  // This call is asynchronous !!!

        requestHandler = new Handler(Looper.getMainLooper());
        currentSongTitle = findViewById(R.id.textView_now_playing_title);

        initToolbar();
        initPlayPauseButton();
        restorePlayPauseButtonState();
        initTrackNavButtons();
        initializeSeekBar();

        Log.d(LOG_ERR_TAG, "onCreate() end ");
    }

    private void initializeSongsRecyclerView() {
        mRecyclerView = findViewById(R.id.main_activity_recycler_view);

        // use this setting to improve performance if you know that changes
        // in content do not change the layout size of the RecyclerView
        mRecyclerView.setHasFixedSize(true);

        // use a linear layout manager
        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);
        addListElemDivider();
        specifyAdapter();

        mAdapter.notifyDataSetChanged();
    }

    private void addListElemDivider() {
        // add divider to RecycleView
        DividerItemDecoration listDivider = new DividerItemDecoration(this, LinearLayoutManager.VERTICAL);
        listDivider.setDrawable(this.getResources().getDrawable(R.drawable.song_list_divider_line, null));
        mRecyclerView.addItemDecoration(listDivider);
    }

    private void specifyAdapter() {
        mAdapter = new SongListRecAdapter(songsPaths, new SongListRecAdapterClickListener());
        mRecyclerView.setAdapter(mAdapter);
    }

    private class SongListRecAdapterClickListener implements SongListRecAdapter.ClickListener{
        @Override
        public void onItemPlayButtonClick(int position){
            if (mService != null && mService.playSong(position)){
                currentSongTitle.setText(mService.getCurrentSongTitle());
                if (!playPauseButton.isChecked()) {
                    playButtonUnusualState = true;
                    playPauseButton.setChecked(!playPauseButton.isChecked());
                }
            }
        }
    }

    private void initializeSeekBar() {
        mSeekBar = findViewById(R.id.seek_bar);

        // Set a change listener for seek bar
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int newProgress, boolean isFromUser) {   // newProgress is in seconds
                if(mService != null && isFromUser){
                    if(mService.isPlayerInitialized())
                        mService.setPlayerPosition(newProgress*1000);
                    else
                        seekBar.setProgress(0);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    protected void initializeSeekBarRunnable(){
        Log.d(LOG_ERR_TAG, "Inside initializeSeekBarRunnable: ");
        mRunnable = new Runnable() {
            @Override
            public void run() {
                //Log.d(LOG_ERR_TAG, "Inside runnable: ");
                if(mService!=null){
                    int mCurrentPosition = mService.getCurrentPlayerPosition()/1000; // In milliseconds
                    mSeekBar.setProgress(mCurrentPosition);
                }
                requestHandler.postDelayed(mRunnable,250);
            }
        };
        requestHandler.post(mRunnable);
    }

    private void initTrackNavButtons() {
        fastRewindButton = findViewById(R.id.imageButton_fast_rewind);
        fastRewindButton.setOnClickListener(new ImageButton.OnClickListener(){
            @Override
            public void onClick(View v){
                mService.moveForward(-10000); // +10 sec
            }
        });

        fastForwardButton = findViewById(R.id.imageButton_fast_forward);
        fastForwardButton.setOnClickListener(new ImageButton.OnClickListener(){
            @Override
            public void onClick(View v){
                if (isMoveForwadCompleted) {
                    isMoveForwadCompleted = false;
                    mService.moveForward(10000); // +10 sec
                }
            }
        });
    }

    private void restoreMediaPlayerStateInfo(){
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        playButtonUnusualState = leftWhilePlaying = sharedPref.getBoolean(KEY_LEFT_WHILE_PLAYING, false);
    }

    private void processMusicFiles(String folderPath) {
        File[] files;
        //Log.d(LOG_FILES_TAG, "Path: " + folderPath);
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

    @NonNull
    private String getDefaultMusicFolderPath() {
        File[] files = getExternalFilesDirs(Environment.MEDIA_MOUNTED);
        String folderPath = "";
        //for (int i = files.length - 1; i >= 0 && folderPath.length() == 0; i--){
        for (int i = 0; i < files.length && folderPath.length() == 0; i--){
            String tempStr = files[i].getAbsolutePath();
            tempStr = tempStr.substring(0, tempStr.indexOf("/Android/")) + "/Music/";
            //Log.d(LOG_FILES_TAG, "Path_temp: " + tempStr);
            if (new File(tempStr).exists())
                folderPath = tempStr;
        }
        return folderPath;
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
            restoreState();
            if (!mService.isPlaying())
                mService.setNewSongPaths(songsPaths);
            initializeSongsRecyclerView();
        }
    }

    private void initPlayPauseButton() {
        playPauseButton = findViewById(R.id.toggleButton_play_pause);
        playPauseButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if(isChecked){
                    //Toast.makeText(MainActivity.this, "Service not started", Toast.LENGTH_SHORT).show();

                    if (playButtonUnusualState) {  // service was running & playing before activity was launched
                        playButtonUnusualState = false;
                    }
                    else if (songsPaths == null){
                        playPauseButton.setChecked(!isChecked);
                        Toast.makeText(MainActivity.this, "Song not yet loaded", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    else if (mService != null) {
                        if (!mService.playSong())
                            playPauseButton.setChecked(!isChecked);
                        currentSongTitle.setText(mService.getCurrentSongTitle());
                    }
                    initializeSeekBarRunnable();
                }
                else
                {
                    //Toast.makeText(MainActivity.this, "Service not stopped", Toast.LENGTH_SHORT).show();
                    if (playButtonUnusualState){
                        playButtonUnusualState = false;
                    }
                    else if (mService != null) {
                        mService.pausePlaying();
                    }
                }
            }
        });
    }

    //start and bind the service when the activity starts
    @Override
    protected void onStart() {
        super.onStart();

    }

    private void bindAndStartSrv() {
        Log.d(LOG_ERR_TAG, "bindAndStartSrv() begin ");
        playIntent = new Intent(this, MediaPlayerService.class);
        if(!leftWhilePlaying)
            startService(playIntent);
        bindService(playIntent, musicConnection, 0); // Context.BIND_AUTO_CREATE
        Log.d(LOG_ERR_TAG, "bindAndStartSrv() end ");
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

                Intent aboutIntent = new Intent(this, FullscreenAboutActivity.class);
                this.startActivity(aboutIntent);

                return true;

            case R.id.settings:

                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                this.startActivity(settingsIntent);

                enteredSettings = true;

                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onResume(){
        super.onResume();
        if (enteredSettings) {
            if (wereModified()) {
                mService.notifySettingsChanged(this);
            }
            if (wasPathModified()) {
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
                if (sharedPref.contains(KEY_CHOOSE_MUSIC_DIR)) {
                    String folderPath = sharedPref.getString(KEY_CHOOSE_MUSIC_DIR, "");
                    songsPaths = null;
                    processMusicFiles(folderPath);

                    mAdapter = new SongListRecAdapter(songsPaths, new SongListRecAdapterClickListener());
                    mRecyclerView.swapAdapter(mAdapter, true);
                }
            }
            enteredSettings = false;
        }
    }

    //connect to the service
    private ServiceConnection musicConnection = new ServiceConnection(){

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            binder = (MediaPlayerService.MusicBinder)service;
            //get service
            mService = binder.getService();
            //pass list
            //mService.setList(songList);
            musicBound = true;
            binder.registerActivity(MainActivity.this, listener);

            if (mService.isPlaying()){
                mSeekBar.setMax(mService.getPlayerDuration()/1000);
                currentSongTitle.setText(mService.getCurrentSongTitle());
            }

            mService.notifySettingsChanged(MainActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicBound = false;
            binder = null;
        }
    };

    @Override
    public void onStop(){
        super.onStop();
        saveSongsArrayState();
        if(mService != null && mService.isPlaying()){
            playButtonUnusualState = true;
            savePlayPauseState();
        }
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        if (mService != null && !mService.isPlaying()) {
            MediaPlayerService.stop(MainActivity.this);
        }
        else {
            playButtonUnusualState = true;
            savePlayPauseState();
        }
        if (binder != null)
            binder.unregisterActivity(this);
        if (mService != null)
            unbindService(musicConnection);
        if (requestHandler != null) {
            requestHandler.removeCallbacks(mRunnable);
        }
    }

    private void savePlayPauseState(){
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor prefsEditor = sharedPref.edit();
        prefsEditor.putBoolean(KEY_LEFT_WHILE_PLAYING, playButtonUnusualState);
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

        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        if (!permissionToReadExtStorageAccepted)
            return;

        // Restore songs array
        if(sharedPref.contains(KEY_SONGS_PATHS)) {
            Gson gson = new Gson();
            String jsonText = sharedPref.getString(KEY_SONGS_PATHS, null);
            songsPaths = gson.fromJson(jsonText, ArrayList.class);
        }
        else {
            processMusicFiles(getDefaultMusicFolderPath());
        }

        SharedPreferences.Editor preferencesEditor = sharedPref.edit();
        preferencesEditor.clear();
        preferencesEditor.apply();
    }

    private void restorePlayPauseButtonState() {
        // Restore play / pause button state
        if(leftWhilePlaying) {
            playPauseButton.setChecked(!playPauseButton.isChecked());
        }
    }

    // Callback for service to use to notify activity about something
    private IListenerFunctions listener = new IListenerFunctions() {
        public void setSeekBarMaxDuration(int maxDuration) {    // in ms
            if (mSeekBar != null)
                mSeekBar.setMax(maxDuration/1000);
        }
        public void onMediaPlayerCompletion(boolean isStopped){
            if(isStopped) {
                playButtonUnusualState = true;
                playPauseButton.setChecked(!playPauseButton.isChecked());
            }
            else{
                currentSongTitle.setText(mService.getCurrentSongTitle());
            }
        }
    };
}
