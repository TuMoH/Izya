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

import android.content.Context;
import android.media.RingtoneManager;

import net.grandcentrix.tray.TrayPreferences;

import org.proninyaroslav.libretorrent.core.TorrentEngine;
import org.proninyaroslav.libretorrent.core.sorting.TorrentSorting;
import org.proninyaroslav.libretorrent.core.utils.FileIOUtils;

public class SettingsManager extends TrayPreferences
{
    public static final String MODULE_NAME = "settings";

    public SettingsManager(Context context)
    {
        super(context, MODULE_NAME, 1);
    }

    public static void initPreferences(Context context)
    {
        SettingsManager pref = new SettingsManager(context);

        String keyTheme = context.getString(R.string.pref_key_theme);
        if (pref.getInt(keyTheme, -1) == -1) {
            pref.put(keyTheme, Integer.parseInt(context.getString(R.string.pref_theme_light_value)));
        }

        String keyPort = context.getString(R.string.pref_key_port);
        if (pref.getInt(keyPort, -1) == -1) {
            pref.put(keyPort, TorrentEngine.DEFAULT_PORT);
        }

        String keySaveTorrentIn = context.getString(R.string.pref_key_save_torrents_in);
        if (pref.getString(keySaveTorrentIn, null) == null) {
            pref.put(keySaveTorrentIn, FileIOUtils.getDefaultDownloadPath());
        }

        String keyFileManagerLastDir = context.getString(R.string.pref_key_filemanager_last_dir);
        if (pref.getString(keyFileManagerLastDir, null) == null) {
            pref.put(keyFileManagerLastDir, FileIOUtils.getDefaultDownloadPath());
        }

        String keyMaxDownloadSpeedLimit = context.getString(R.string.pref_key_max_download_speed);
        if (pref.getInt(keyMaxDownloadSpeedLimit, -1) == -1) {
            pref.put(keyMaxDownloadSpeedLimit, 0);
        }

        String keyMaxUploadSpeedLimit = context.getString(R.string.pref_key_max_upload_speed);
        if (pref.getInt(keyMaxUploadSpeedLimit, -1) == -1) {
            pref.put(keyMaxUploadSpeedLimit, 0);
        }

        String keyNotifySound = context.getString(R.string.pref_key_notify_sound);
        if (pref.getString(keyNotifySound, null) == null) {
            pref.put(keyNotifySound, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).toString());
        }

        String keySortTorrentBy = context.getString(R.string.pref_key_sort_torrent_by);
        if (pref.getString(keySortTorrentBy, null) == null) {
            pref.put(keySortTorrentBy, TorrentSorting.SortingColumns.name.name());
        }

        String keySortTorrentDirection = context.getString(R.string.pref_key_sort_torrent_direction);
        if (pref.getString(keySortTorrentDirection, null) == null) {
            pref.put(keySortTorrentDirection, TorrentSorting.Direction.ASC.name());
        }
    }
}