/* ****************************************************************************
 * VLCApplication.java
 *****************************************************************************
 * Copyright © 2010-2013 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/
package org.videolan.vlc;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Process;
import android.support.v4.app.DialogFragment;
import android.support.v4.util.SimpleArrayMap;
import android.util.Log;

import com.timursoft.izya.di.AndroidModule;
import com.timursoft.izya.di.AppComponent;
import com.timursoft.izya.di.DaggerAppComponent;

import org.proninyaroslav.libretorrent.core.utils.FileIOUtils;
import org.videolan.libvlc.Dialog;
import org.videolan.vlc.gui.DialogActivity;
import org.videolan.vlc.gui.dialogs.VlcProgressDialog;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.helpers.BitmapCache;
import org.videolan.vlc.media.MediaDatabase;
import org.videolan.vlc.util.Strings;
import org.videolan.vlc.util.VLCInstance;

import java.util.Calendar;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class VLCApplication extends Application {
    public final static String TAG = "VLC/VLCApplication";
    private static VLCApplication instance;
    private static AppComponent appComponent;

    public final static String SLEEP_INTENT = Strings.buildPkgString("SleepIntent");

    public static Calendar sPlayerSleepTime = null;

    private static SimpleArrayMap<String, Object> sDataMap = new SimpleArrayMap<>();

    /* Up to 2 threads maximum, inactive threads are killed after 2 seconds */
    private ThreadPoolExecutor mThreadPool = new ThreadPoolExecutor(0, 2, 2, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(), THREAD_FACTORY);
    public static final ThreadFactory THREAD_FACTORY = runnable -> {
        Thread thread = new Thread(runnable);
        thread.setPriority(Process.THREAD_PRIORITY_DEFAULT+Process.THREAD_PRIORITY_LESS_FAVORABLE);
        return thread;
    };

    private static int sDialogCounter = 0;

    @Override
    public void onCreate() {
        super.onCreate();

        instance = this;

        // Initialize the database soon enough to avoid any race condition and crash
        MediaDatabase.getInstance();
        // Prepare cache folder constants
        AudioUtil.prepareCacheFolder(this);

        if (!VLCInstance.testCompatibleCPU(this))
            return;
        Dialog.setCallbacks(VLCInstance.get(), mDialogCallbacks);

        appComponent = DaggerAppComponent.builder()
                .androidModule(new AndroidModule(this))
                .build();

        cleanTempDir();
    }

    public static AppComponent getAppComponent() {
        return appComponent;
    }

    /**
     * Called when the overall system is running low on memory
     */
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.w(TAG, "System is running low on memory");

        BitmapCache.getInstance().clear();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        Log.w(TAG, "onTrimMemory, level: "+level);

        BitmapCache.getInstance().clear();
    }

    private void cleanTempDir() {
        Handler handler = new Handler(getMainLooper());
        handler.post(() -> {
            try {
                FileIOUtils.cleanTempDir(getBaseContext());
            } catch (Exception e) {
                Log.e(TAG, "Error during setup of temp directory: ", e);
            }
        });
    }

    /**
     * @return the main context of the Application
     */
    public static Context getAppContext()
    {
        return instance;
    }

    /**
     * @return the main resources from the Application
     */
    public static Resources getAppResources()
    {
        return instance.getResources();
    }

    public static void runBackground(Runnable runnable) {
        instance.mThreadPool.execute(runnable);
    }

    public static boolean removeTask(Runnable runnable) {
        return instance.mThreadPool.remove(runnable);
    }

    public static void storeData(String key, Object data) {
        sDataMap.put(key, data);
    }

    public static Object getData(String key) {
        return sDataMap.remove(key);
    }

    Dialog.Callbacks mDialogCallbacks = new Dialog.Callbacks() {
        @Override
        public void onDisplay(Dialog.ErrorMessage dialog) {
            Log.w(TAG, "ErrorMessage "+dialog.getText());
        }

        @Override
        public void onDisplay(Dialog.LoginDialog dialog) {
            String key = DialogActivity.KEY_LOGIN + sDialogCounter++;
            fireDialog(dialog, key);
        }

        @Override
        public void onDisplay(Dialog.QuestionDialog dialog) {
            String key = DialogActivity.KEY_QUESTION + sDialogCounter++;
            fireDialog(dialog, key);
        }

        @Override
        public void onDisplay(Dialog.ProgressDialog dialog) {
            String key = DialogActivity.KEY_PROGRESS + sDialogCounter++;
            fireDialog(dialog, key);
        }

        @Override
        public void onCanceled(Dialog dialog) {
            ((DialogFragment)dialog.getContext()).dismiss();
        }

        @Override
        public void onProgressUpdate(Dialog.ProgressDialog dialog) {
            VlcProgressDialog vlcProgressDialog = (VlcProgressDialog) dialog.getContext();
            if (vlcProgressDialog != null && vlcProgressDialog.isVisible())
                vlcProgressDialog.updateProgress();
        }
    };

    private void fireDialog(Dialog dialog, String key) {
        storeData(key, dialog);
        startActivity(new Intent(instance, DialogActivity.class).setAction(key)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

}
