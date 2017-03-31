/*
 * Copyright (C) 2016 Yaroslav Pronin <proninyaroslav@mail.ru>
 *
 * This file is part of LibreTorrent.
 *
 * LibreTorrent is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LibreTorrent is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LibreTorrent.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.proninyaroslav.libretorrent;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import org.proninyaroslav.libretorrent.core.utils.FileIOUtils;

public class MainApplication extends Application {
    private static final String TAG = MainApplication.class.getSimpleName();

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        cleanTemp();
    }

    private void cleanTemp() {
        Handler handler = new Handler(getMainLooper());
        Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    FileIOUtils.cleanTempDir(getBaseContext());
                } catch (Exception e) {
                    Log.e(TAG, "Error during setup of temp directory: ", e);
                }
            }
        };
        handler.post(r);
    }
}