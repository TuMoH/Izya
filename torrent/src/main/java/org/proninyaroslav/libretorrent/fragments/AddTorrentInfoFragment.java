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

package org.proninyaroslav.libretorrent.fragments;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.TorrentMetaInfo;
import org.proninyaroslav.libretorrent.core.utils.FileIOUtils;
import org.proninyaroslav.libretorrent.core.utils.TorrentUtils;

/*
 * The fragment for displaying torrent metainformation,
 * taken from bencode. Part of AddTorrentFragment.
 */

public class AddTorrentInfoFragment extends Fragment {
    @SuppressWarnings("unused")
    private static final String TAG = AddTorrentInfoFragment.class.getSimpleName();

    private static final String TAG_INFO = "info";

    private AppCompatActivity activity;
    private String downloadDir = "";

    private TextView torrentNameField, torrentSizeView, fileCountView, freeSpace;

    public static AddTorrentInfoFragment newInstance(TorrentMetaInfo info) {
        AddTorrentInfoFragment fragment = new AddTorrentInfoFragment();

        Bundle args = new Bundle();
        args.putParcelable(TAG_INFO, info);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        downloadDir = TorrentUtils.getTorrentDownloadPath(activity.getApplicationContext());
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        if (context instanceof AppCompatActivity) {
            activity = (AppCompatActivity) context;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_add_torrent_info, container, false);

        torrentNameField = (TextView) v.findViewById(R.id.torrent_name);
        torrentSizeView = (TextView) v.findViewById(R.id.torrent_size);
        fileCountView = (TextView) v.findViewById(R.id.torrent_file_count);
        freeSpace = (TextView) v.findViewById(R.id.free_space);

        TorrentMetaInfo info = getArguments().getParcelable(TAG_INFO);

        if (info != null) {
            torrentNameField.setText(info.getTorrentName());
            torrentSizeView.setText(Formatter.formatFileSize(activity, info.getTorrentSize()));
            fileCountView.setText(Integer.toString(info.getFileCount()));
            freeSpace.setText(String.format(getString(R.string.free_space),
                    Formatter.formatFileSize(activity.getApplicationContext(),
                            FileIOUtils.getFreeSpace(downloadDir))));
        }

        return v;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (activity == null) {
            activity = (AppCompatActivity) getActivity();
        }
    }

    public String getDownloadDir() {
        return downloadDir;
    }

    public String getTorrentName() {
        return torrentNameField.getText().toString();
    }

}