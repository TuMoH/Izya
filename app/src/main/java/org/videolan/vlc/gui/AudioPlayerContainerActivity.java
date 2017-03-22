/*
 * *************************************************************************
 *  SlidingPaneActivity.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc.gui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;

import com.timursoft.izya.R;

import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.gui.browser.MediaBrowserFragment;
import org.videolan.vlc.interfaces.IRefreshable;
import org.videolan.vlc.media.MediaLibrary;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.util.Strings;
import org.videolan.vlc.util.WeakHandler;

public class AudioPlayerContainerActivity extends AppCompatActivity implements PlaybackService.Client.Callback {

    public static final String TAG = "VLC/AudioPlayerContainerActivity";
    public static final String ACTION_SHOW_PLAYER = Strings.buildPkgString("gui.ShowPlayer");

    protected static final String ID_VIDEO = "video";
    protected static final String ID_AUDIO = "audio";
    protected static final String ID_NETWORK = "network";
    protected static final String ID_DIRECTORIES = "directories";
    protected static final String ID_HISTORY = "history";
    protected static final String ID_MRL = "mrl";
    protected static final String ID_PREFERENCES = "preferences";
    protected static final String ID_ABOUT = "about";

    protected ActionBar mActionBar;
    protected Toolbar mToolbar;
    protected SharedPreferences mSettings;
    private final PlaybackServiceActivity.Helper mHelper = new PlaybackServiceActivity.Helper(this, this);
    protected PlaybackService mService;

    protected boolean mPreventRescan = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        /* Get settings */
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);
        /* Theme must be applied before super.onCreate */
        applyTheme();

        MediaUtils.updateSubsDownloaderActivity(this);

        super.onCreate(savedInstanceState);
    }

    protected void initAudioPlayerContainerActivity() {
        mToolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(mToolbar);
        mActionBar = getSupportActionBar();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mHelper.onStart();

        //Handle external storage state
        IntentFilter storageFilter = new IntentFilter();
        storageFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        storageFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        storageFilter.addDataScheme("file");
        registerReceiver(storageReceiver, storageFilter);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        mPreventRescan = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(storageReceiver);
        mHelper.onStop();
    }

    private void applyTheme() {
        if (mSettings.getBoolean("enable_black_theme", false)) {
            setTheme(R.style.Theme_VLC_Black);
        }
    }

    public void updateLib() {
        if (mPreventRescan) {
            mPreventRescan = false;
            return;
        }
        FragmentManager fm = getSupportFragmentManager();
        Fragment current = fm.findFragmentById(R.id.fragment_placeholder);
        if (current != null && current instanceof IRefreshable)
            ((IRefreshable) current).refresh();
        else
            MediaLibrary.getInstance().scanMediaItems();
        Fragment fragment = fm.findFragmentByTag(ID_AUDIO);
        if (fragment != null && !fragment.equals(current)) {
            ((MediaBrowserFragment) fragment).clear();
        }
        fragment = fm.findFragmentByTag(ID_VIDEO);
        if (fragment != null && !fragment.equals(current)) {
            ((MediaBrowserFragment) fragment).clear();
        }
    }

    private void stopBackgroundTasks() {
        MediaLibrary ml = MediaLibrary.getInstance();
        if (ml.isWorking())
            ml.stop();
    }

    private final BroadcastReceiver storageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equalsIgnoreCase(Intent.ACTION_MEDIA_MOUNTED)) {
                mActivityHandler.sendEmptyMessage(ACTION_MEDIA_MOUNTED);
            } else if (action.equalsIgnoreCase(Intent.ACTION_MEDIA_UNMOUNTED)) {
                mActivityHandler.sendEmptyMessageDelayed(ACTION_MEDIA_UNMOUNTED, 100);
            }
        }
    };

    Handler mActivityHandler = new StorageHandler(this);

    private static final int ACTION_MEDIA_MOUNTED = 1337;
    private static final int ACTION_MEDIA_UNMOUNTED = 1338;

    private static class StorageHandler extends WeakHandler<AudioPlayerContainerActivity> {

        public StorageHandler(AudioPlayerContainerActivity owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            switch (msg.what) {
                case ACTION_MEDIA_MOUNTED:
                    removeMessages(ACTION_MEDIA_UNMOUNTED);
                    getOwner().updateLib();
                    break;
                case ACTION_MEDIA_UNMOUNTED:
                    getOwner().stopBackgroundTasks();
                    getOwner().updateLib();
                    break;
            }
        }
    }

    public PlaybackServiceActivity.Helper getHelper() {
        return mHelper;
    }

    @Override
    public void onConnected(PlaybackService service) {
        mService = service;
    }

    @Override
    public void onDisconnected() {
        mService = null;
    }
}
