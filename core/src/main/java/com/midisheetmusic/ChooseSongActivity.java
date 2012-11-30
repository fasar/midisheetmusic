/*
 * Copyright (c) 2011-2012 Madhav Vaidyanathan
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 */

package com.midisheetmusic;

import java.io.*;
import java.util.*;
import android.net.*;
import android.app.*;
import android.os.*;
import android.widget.*;
import android.view.*;
import android.content.*;
import android.content.res.*;
import android.provider.*;
import android.database.*;
import android.text.*;
import com.midisheetmusic.helper.FileUri;


/** @class ScanMidiFiles
 * The ScanMidiFiles class is used to scan for midi files
 * on a background thread.
 */
class ScanMidiFiles extends AsyncTask<Integer, Integer, ArrayList<FileUri> > {
    private ArrayList<FileUri> songlist;
    private File rootdir;
    private ChooseSongActivity activity;

    public ScanMidiFiles() {
    }

    public void setActivity(ChooseSongActivity activity) {
        this.activity = activity;
    }

    @Override
    protected void onPreExecute() {
        songlist = new ArrayList<FileUri>();
        try {
            rootdir = Environment.getExternalStorageDirectory();
            Toast message = Toast.makeText(activity, "Scanning " + rootdir.getAbsolutePath() + " for MIDI files", Toast.LENGTH_SHORT);
            message.show();
        }
        catch (Exception e) {}
    }

    @Override
    protected ArrayList<FileUri> doInBackground(Integer... params) {
        if (rootdir == null) {
            return songlist;
        }
        try {
            loadMidiFilesFromDirectory(rootdir, 1);
        }
        catch (Exception e) {
        }
        return songlist;
    }

    @Override
    protected void onProgressUpdate(Integer... progress) {
    }

    @Override
    protected void onPostExecute(ArrayList<FileUri> result) {
        ChooseSongActivity act = activity;
        this.activity = null;
        act.scanDone(songlist);
        Toast message = Toast.makeText(act, "Found " + songlist.size() + " MIDI files", Toast.LENGTH_SHORT);
        message.show();
    }

    @Override
    protected void onCancelled() {
        this.activity = null;
    }
    
    /* Given a directory, add MIDI files (ending in .mid) to the songlist.
     * If the directory contains subdirectories, call this method recursively.
     */
    private void loadMidiFilesFromDirectory(File dir, int depth) throws IOException {
        if (isCancelled()) {
            return;
        }
        if (depth > 10) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }        
        for (File file : files) {
            if (file == null) {
                continue;
            }
            if (isCancelled()) {
                return;
            }
            if (file.getName().endsWith(".mid") || file.getName().endsWith(".MID") ||
                file.getName().endsWith(".midi")) {
                FileUri song = new FileUri(file.getAbsolutePath());
                songlist.add(song);
            }
        }
        for (File file : files) {
            if (isCancelled()) {
                return;
            }
            try {
                if (file.isDirectory()) {
                    loadMidiFilesFromDirectory(file, depth+1);
                }
            }
            catch (Exception e) {}
        }
    }
}




/** @class ChooseSongActivity
 * The ChooseSongActivity class is used to display a list of
 * songs to choose from.  The list is created from the songs
 * shipped with MidiSheetMusic (in the assets directory), and 
 * also by searching for midi files in the internal/external 
 * device storage.
 *
 * When a song is chosen, this calls the SheetMusicAcitivty, passing
 * the raw midi byte[] data as a parameter in the Intent.
 */ 
public class ChooseSongActivity extends ListActivity implements TextWatcher {

    /** The complete list of midi files */
    ArrayList<FileUri> songlist;

    /** Textbox to filter the songs by name */
    EditText filterText;

    /** Task to scan for midi files */
    ScanMidiFiles scanner;

    IconArrayAdapter<FileUri> adapter;

    /* When this activity changes orientation, save the songlist,
     * so we don't have to re-scan for midi songs.
     */
    @Override
    public Object onRetainNonConfigurationInstance() {
        return songlist;
    }
    
    
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.choose_song);
        setTitle("MidiSheetMusic: Choose Song");
        
        /* If we're restarting from an orientation change,
         * load the saved song list.
         */
        songlist = (ArrayList<FileUri>) getLastNonConfigurationInstance();
        if (songlist != null) {
            adapter = new IconArrayAdapter<FileUri>(this, android.R.layout.simple_list_item_1, songlist);
            this.setListAdapter(adapter);
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        if (songlist == null || songlist.size() == 0) {
            songlist = new ArrayList<FileUri>();
            loadAssetMidiFiles();
            loadMidiFilesFromProvider(MediaStore.Audio.Media.INTERNAL_CONTENT_URI);
            loadMidiFilesFromProvider(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);
            // Sort the songlist by name
            Collections.sort(songlist, songlist.get(0));

            // Remove duplicates
            ArrayList<FileUri> origlist = songlist;
            songlist = new ArrayList<FileUri>();
            String prevname = "";
            for (FileUri file : origlist) {
                if (!file.toString().equals(prevname)) {
                    songlist.add(file);
                }
                prevname = file.toString();
            }

            adapter = new IconArrayAdapter<FileUri>(this, android.R.layout.simple_list_item_1, songlist);
            this.setListAdapter(adapter);
        }
        filterText = (EditText) findViewById(R.id.name_filter);
        filterText.addTextChangedListener(this);
        filterText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        SharedPreferences settings = getPreferences(0);
        boolean showedBrowseMenu = settings.getBoolean("showedBrowseMenu", false);
        if (!showedBrowseMenu) {
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean("showedBrowseMenu", true);
            editor.commit();
            Toast message = Toast.makeText(this, "To search for additional MIDI files, use the Menu", Toast.LENGTH_LONG);
            message.show();
        }
    }


    /** Scan the SD card for midi songs.  Since this is a lengthy
     *  operation, perform the scan in a background thread.
     */
    public void scanForSongs() {
        if (scanner != null) {
            return;
        }
        scanner = new ScanMidiFiles();
        scanner.setActivity(this);
        scanner.execute(0);
    }

    public void scanDone(ArrayList<FileUri> newfiles) {
        if (songlist == null || newfiles == null) {
            return;
        }
        for (FileUri file : newfiles) {
            songlist.add(file);
        }
        // Sort the songlist by name
        Collections.sort(songlist, songlist.get(0));

        // Remove duplicates
        ArrayList<FileUri> origlist = songlist;
        songlist = new ArrayList<FileUri>();
        String prevname = "";
        for (FileUri file : origlist) {
            if (!file.toString().equals(prevname)) {
                songlist.add(file);
            }
            prevname = file.toString();
        }
        adapter = new IconArrayAdapter<FileUri>(this, android.R.layout.simple_list_item_1, songlist);
        this.setListAdapter(adapter);
        scanner = null;
    }

    /** Load all the sample midi songs from the assets directory into songlist.
     *  Look for files ending with ".mid"
     */
    void loadAssetMidiFiles() {
        try {
            AssetManager assets = this.getResources().getAssets();
            String[] files = assets.list("");
            for (String path: files) {
                if (path.endsWith(".mid")) {
                    FileUri file = new FileUri(assets, path, path);
                    songlist.add(file);
                }
            }
        }
        catch (IOException e) {
        }
    }

    
    /** Look for midi files (with mime-type audio/midi) in the 
     * internal/external storage. Add them to the songlist.
     */
    private void loadMidiFilesFromProvider(Uri content_uri) {
        ContentResolver resolver = getContentResolver();
        String columns[] = { 
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE, 
            MediaStore.Audio.Media.MIME_TYPE 
        };
        String selection = MediaStore.Audio.Media.MIME_TYPE + " LIKE '%mid%'";
        Cursor cursor = resolver.query(content_uri, columns, selection, null, null);
        if (cursor == null) {
            return;
        }
        if (!cursor.moveToFirst()) {
            cursor.close();
            return;
        }
        
        do {
            int idColumn = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
            int titleColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
            int mimeColumn = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE);

            long id = cursor.getLong(idColumn);
            String title = cursor.getString(titleColumn);
            String mime = cursor.getString(mimeColumn);

            if (mime.endsWith("/midi") || mime.endsWith("/mid")) {
                Uri uri = Uri.withAppendedPath(content_uri, "" + id);
                FileUri file = new FileUri(resolver, uri, title);
                songlist.add(file);
            }
        } while (cursor.moveToNext());
        cursor.close();
    }

    /** When a song is clicked on, start a SheetMusicActivity.
     *  Read the raw byte[] data of the midi file.
     *  Pass the raw byte[] data as a parameter in the Intent.
     *  Pass the midi file Title as a parameter in the Intent.
     */
    @Override
    protected void onListItemClick(ListView parent, View view, int position, long id) {
        super.onListItemClick(parent, view, position, id);
        if (scanner != null) {
            scanner.cancel(true);
            scanner = null;
        }
        FileUri file = (FileUri) this.getListAdapter().getItem(position);
        byte[] data = file.getData();
        if (data == null || data.length <= 6 || !hasMidiHeader(data)) {
            showErrorDialog("Error: Unable to open song: " + file.toString());
            return;
        }
        Intent intent = new Intent(this, SheetMusicActivity.class);
        intent.putExtra(SheetMusicActivity.MidiDataID, data);
        intent.putExtra(SheetMusicActivity.MidiTitleID, file.toString());
        startActivity(intent);

    }


    /** As text is entered in the filter box, filter the list of
     *  midi songs to display.
     */
    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        adapter.getFilter().filter(s);
    }

    @Override
    public void afterTextChanged(Editable s) {
    }

   
    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }


    /** Return true if the data starts with the header MTrk */
    boolean hasMidiHeader(byte[] data) {
        String s;
        try {
            s = new String(data, 0, 4, "US-ASCII");
            if (s.equals("MThd"))
                return true;
            else
                return false;
        }
        catch (UnsupportedEncodingException e) {
            return false;
        }
    }

    /** Start the FileBrowser activity, which is used to select a midi file */
    void browseForSongs() {
        Intent intent = new Intent(this, FileBrowserActivity.class);
        startActivity(intent);
    }

    /** When the menu button is pressed, initialize the menus. */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.choose_song_menu, menu);
        return true;
    }

    /** Callback when a menu item is selected.
     *  - Scan for Midi Files : Scan the SD card for midi files
     *  - Browse Midi Files : Let the user browser for midi files
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
          case R.id.scan_files:
            scanForSongs();
            return true;
          case R.id.browse_files:
            browseForSongs();
            return true;
          default:
            return super.onOptionsItemSelected(item);
        }
    }

    
    /** Show an error dialog with the given message */
    void showErrorDialog(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message);
        builder.setCancelable(false);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
           public void onClick(DialogInterface dialog, int id) {
           }
        });
        AlertDialog alert = builder.create();
        alert.show();
    }
}

