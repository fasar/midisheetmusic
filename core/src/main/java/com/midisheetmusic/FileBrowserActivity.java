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
import android.app.*;
import android.os.*;
import android.widget.*;
import android.view.*;
import android.content.*;
import com.midisheetmusic.helper.FileUri;


public class FileBrowserActivity extends ListActivity {
    private ArrayList<FileUri> filelist; /* List of files in the directory */
    private String directory;            /* Current directory being displayed */
    private TextView directoryView;      /* TextView showing directory name */
    private String rootdir;              /* The top level root directory */
    private IconArrayAdapter<FileUri> adapter;


    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.file_browser);
        setTitle("MidiSheetMusic: Browse Files");
    }

    @Override
    public void onResume() {
        super.onResume();
        rootdir = Environment.getExternalStorageDirectory().getAbsolutePath();
        directoryView = (TextView) findViewById(R.id.directory);
        loadDirectory(rootdir);
    }

    /* Scan the files in the new directory, and store them in the filelist.
     * Update the UI by refreshing the list adapter.
     */
    private void loadDirectory(String newdirectory) {
        if (newdirectory.equals("../")) {
            directory = new File(directory).getParent();
        }
        else {
            directory = newdirectory;
        }
        directoryView.setText(directory);

        filelist = new ArrayList<FileUri>();
        ArrayList<FileUri> sortedDirs = new ArrayList<FileUri>();
        ArrayList<FileUri> sortedFiles = new ArrayList<FileUri>();
        if (!newdirectory.equals(rootdir)) {
            sortedDirs.add(new FileUri("../"));
        }
        try {
            File dir = new File(directory);
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file == null) {
                        continue;
                    }
                    String filename = file.getName();
                    if (file.isDirectory()) {
                        FileUri fileuri = new FileUri(file.getAbsolutePath() + "/");
                        sortedDirs.add(fileuri);
                    }
                    else if (filename.endsWith(".mid") || filename.endsWith(".MID") ||
                             filename.endsWith(".midi") || filename.endsWith(".MIDI")) {
                        
                        FileUri fileuri = new FileUri(file.getAbsolutePath());
                        sortedFiles.add(fileuri);
                    }
                }
            }
        }
        catch (Exception e) {
        }

        if (sortedDirs.size() > 0) {
            Collections.sort(sortedDirs, sortedDirs.get(0));
        }
        if (sortedFiles.size() > 0) {
            Collections.sort(sortedFiles, sortedFiles.get(0));
        }
        filelist.addAll(sortedDirs);
        filelist.addAll(sortedFiles);
        adapter = new IconArrayAdapter<FileUri>(this, android.R.layout.simple_list_item_1, filelist);
        this.setListAdapter(adapter);
    }
    

    /** When a user selects an item:
     * - If it's a directory, load that directory.
     * - If it's a file, ??
     */
    @Override
    protected void onListItemClick(ListView parent, View view, int position, long id) {
        super.onListItemClick(parent, view, position, id);
        FileUri file = (FileUri) this.getListAdapter().getItem(position);
        if (file.isDirectory()) {
            this.loadDirectory(file.filePath());
             return;
        }
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


