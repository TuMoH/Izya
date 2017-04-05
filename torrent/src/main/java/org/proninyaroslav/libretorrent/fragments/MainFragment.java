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

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcelable;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

import org.proninyaroslav.libretorrent.DetailTorrentActivity;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.adapters.TorrentListAdapter;
import org.proninyaroslav.libretorrent.core.Torrent;
import org.proninyaroslav.libretorrent.core.TorrentTaskServiceIPC;
import org.proninyaroslav.libretorrent.core.exceptions.FileAlreadyExistsException;
import org.proninyaroslav.libretorrent.core.sorting.TorrentSortingComparator;
import org.proninyaroslav.libretorrent.core.stateparcel.TorrentStateParcel;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.customviews.EmptyRecyclerView;
import org.proninyaroslav.libretorrent.customviews.RecyclerViewDividerDecoration;
import org.proninyaroslav.libretorrent.dialogs.BaseAlertDialog;
import org.proninyaroslav.libretorrent.dialogs.ErrorReportAlertDialog;
import org.proninyaroslav.libretorrent.dialogs.filemanager.FileManagerDialog;
import org.proninyaroslav.libretorrent.receivers.NotificationReceiver;
import org.proninyaroslav.libretorrent.services.TorrentTaskService;
import org.proninyaroslav.libretorrent.settings.SettingsActivity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/*
 * The list of torrents.
 */

public class MainFragment extends Fragment
        implements
        TorrentListAdapter.ViewHolder.ClickListener,
        BaseAlertDialog.OnClickListener,
        BaseAlertDialog.OnDialogShowListener {
    @SuppressWarnings("unused")
    private static final String TAG = MainFragment.class.getSimpleName();

    private static final String TAG_PREV_IMPL_INTENT = "prev_impl_intent";
    private static final String TAG_SELECTABLE_ADAPTER = "selectable_adapter";
    private static final String TAG_SELECTED_TORRENTS = "selected_torrents";
    private static final String TAG_IN_ACTION_MODE = "in_action_mode";
    private static final String TAG_DELETE_TORRENT_DIALOG = "delete_torrent_dialog";
    private static final String TAG_ADD_LINK_DIALOG = "add_link_dialog";
    private static final String TAG_ERROR_OPEN_TORRENT_FILE_DIALOG = "error_open_torrent_file_dialog";
    private static final String TAG_SAVE_ERROR_DIALOG = "save_error_dialog";
    private static final String TAG_TORRENTS_LIST_STATE = "torrents_list_state";

    private static final int ADD_TORRENT_REQUEST = 1;
    private static final int TORRENT_FILE_CHOOSE_REQUEST = 2;

    private AppCompatActivity activity;
    private FloatingActionButton addTorrentButton;
    private CoordinatorLayout coordinatorLayout;
    private LinearLayoutManager layoutManager;
    /* Save state scrolling */
    private Parcelable torrentsListState;
    private Map<String, TorrentStateParcel> torrentStates = new HashMap<>();
    private TorrentListAdapter adapter;
    private ActionMode actionMode;
    private ActionModeCallback actionModeCallback = new ActionModeCallback();
    private boolean inActionMode = false;
    private ArrayList<String> selectedTorrents = new ArrayList<>();
    private boolean addTorrentMenu = false;

    /* Prevents re-adding the torrent, obtained through implicit intent */
    private Intent prevImplIntent;
    /* Messenger for communicating with the service. */
    private Messenger serviceCallback = null;
    private Messenger clientCallback = new Messenger(new CallbackHandler(this));
    private TorrentTaskServiceIPC ipc = new TorrentTaskServiceIPC();
    /* Flag indicating whether we have called bind on the service. */
    private boolean bound;
    private ReentrantLock sync;
    /*
     * Torrents are added to the queue, if the client is not bounded to service.
     * Trying to add torrents will be made at the first connect.
     */
    private HashSet<Torrent> torrentsQueue = new HashSet<>();

    private Throwable sentError;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_main, container, false);
        coordinatorLayout = (CoordinatorLayout) v.findViewById(R.id.main_coordinator_layout);

        return v;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        if (context instanceof AppCompatActivity) {
            activity = (AppCompatActivity) context;
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (activity == null) {
            activity = (AppCompatActivity) getActivity();
        }

        showBlankFragment();

        sync = new ReentrantLock();

        Toolbar toolbar = (Toolbar) activity.findViewById(R.id.toolbar);

        if (toolbar != null) {
            toolbar.setTitle(R.string.app_name);
        }

        activity.setSupportActionBar(toolbar);
        setHasOptionsMenu(true);

        addTorrentButton = (FloatingActionButton) activity.findViewById(R.id.add_torrent_button);
        addTorrentButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addLinkDialog();
            }
        });

        if (savedInstanceState != null) {
            prevImplIntent = savedInstanceState.getParcelable(TAG_PREV_IMPL_INTENT);
        }

        activity.bindService(new Intent(activity.getApplicationContext(), TorrentTaskService.class),
                connection, Context.BIND_AUTO_CREATE);

        EmptyRecyclerView torrentsList = (EmptyRecyclerView) activity.findViewById(R.id.torrent_list);
        layoutManager = new LinearLayoutManager(activity);
        torrentsList.setLayoutManager(layoutManager);

        /*
         * A RecyclerView by default creates another copy of the ViewHolder in order to
         * fade the views into each other. This causes the problem because the old ViewHolder gets
         * the payload but then the new one doesn't. So needs to explicitly tell it to reuse the old one.
         */
        DefaultItemAnimator animator = new DefaultItemAnimator() {
            @Override
            public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder) {
                return true;
            }
        };

        TypedArray a = activity.obtainStyledAttributes(new TypedValue().data, new int[]{R.attr.divider});

        torrentsList.setItemAnimator(animator);
        torrentsList.addItemDecoration(
                new RecyclerViewDividerDecoration(a.getDrawable(0)));
        torrentsList.setEmptyView(activity.findViewById(R.id.empty_view_torrent_list));

        a.recycle();

        adapter = new TorrentListAdapter(
                new ArrayList<TorrentStateParcel>(),
                activity, R.layout.item_torrent_list, this,
                new TorrentSortingComparator(Utils.getTorrentSorting(activity.getApplicationContext())));

        torrentsList.setAdapter(adapter);

        Intent i = activity.getIntent();
        /* If add torrent dialog has been called by an implicit intent */
        if (i != null && i.hasExtra("TAG_RESULT_TORRENT")) {
            if (prevImplIntent == null || !prevImplIntent.equals(i)) {
                prevImplIntent = i;
                Torrent torrent = i.getParcelableExtra("TAG_RESULT_TORRENT");

                if (torrent != null) {
                    ArrayList<Torrent> list = new ArrayList<>();
                    list.add(torrent);
                    addTorrentsRequest(list);
                }
            }

        } else if (i != null && i.getAction() != null) {
            switch (i.getAction()) {
                case NotificationReceiver.NOTIFY_ACTION_ADD_TORRENT:
                    addTorrentMenu = true;
                    /* Prevents re-reading action after device configuration changes */
                    i.setAction(null);
                    break;
            }
        }

        /* Show add torrent menu (called from service) after window is displayed */
        activity.getWindow().findViewById(android.R.id.content).post(new Runnable() {
            @Override
            public void run() {
                if (addTorrentMenu) {
                    /* Hide notification bar */
                    activity.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
                    addTorrentMenu = false;

                    View v = activity.getWindow().findViewById(android.R.id.content);
                    registerForContextMenu(v);
                    activity.openContextMenu(v);
                    unregisterForContextMenu(v);
                }
            }
        });

        if (savedInstanceState != null) {
            selectedTorrents = savedInstanceState.getStringArrayList(TAG_SELECTED_TORRENTS);
            if (savedInstanceState.getBoolean(TAG_IN_ACTION_MODE, false)) {
                actionMode = activity.startActionMode(actionModeCallback);
                adapter.setSelectedItems(savedInstanceState.getIntegerArrayList(TAG_SELECTABLE_ADAPTER));
                actionMode.setTitle(String.valueOf(adapter.getSelectedItemCount()));
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (bound) {
            try {
                ipc.sendClientDisconnect(serviceCallback, clientCallback);

            } catch (RemoteException e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }

            getActivity().unbindService(connection);
            bound = false;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (torrentsListState != null) {
            layoutManager.onRestoreInstanceState(torrentsListState);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putParcelable(TAG_PREV_IMPL_INTENT, prevImplIntent);
        outState.putIntegerArrayList(TAG_SELECTABLE_ADAPTER, adapter.getSelectedItems());
        outState.putBoolean(TAG_IN_ACTION_MODE, inActionMode);
        outState.putStringArrayList(TAG_SELECTED_TORRENTS, selectedTorrents);
        torrentsListState = layoutManager.onSaveInstanceState();
        outState.putParcelable(TAG_TORRENTS_LIST_STATE, torrentsListState);

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);

        if (savedInstanceState != null) {
            torrentsListState = savedInstanceState.getParcelable(TAG_TORRENTS_LIST_STATE);
        }
    }

    private ServiceConnection connection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            serviceCallback = new Messenger(service);
            bound = true;

            if (!torrentsQueue.isEmpty()) {
                addTorrentsRequest(torrentsQueue);
                torrentsQueue.clear();
            }

            try {
                ipc.sendClientConnect(serviceCallback, clientCallback);

            } catch (RemoteException e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            serviceCallback = null;
            bound = false;
        }
    };

    static class CallbackHandler extends Handler {
        WeakReference<MainFragment> fragment;

        public CallbackHandler(MainFragment fragment) {
            this.fragment = new WeakReference<>(fragment);
        }

        @Override
        public void handleMessage(Message msg) {
            if (fragment.get() == null) {
                return;
            }

            Bundle b;
            TorrentStateParcel state;

            switch (msg.what) {
                case TorrentTaskServiceIPC.UPDATE_STATES_ONESHOT: {
                    b = msg.getData();
                    b.setClassLoader(TorrentStateParcel.class.getClassLoader());

                    Bundle states = b.getParcelable(TorrentTaskServiceIPC.TAG_STATES_LIST);
                    if (states != null) {
                        fragment.get().torrentStates.clear();

                        for (String key : states.keySet()) {
                            state = states.getParcelable(key);
                            if (state != null) {
                                fragment.get().torrentStates.put(state.torrentId, state);
                            }
                        }

                        fragment.get().reloadAdapter();
                    }
                    break;
                }
                case TorrentTaskServiceIPC.UPDATE_STATE:
                    b = msg.getData();
                    b.setClassLoader(TorrentStateParcel.class.getClassLoader());
                    state = b.getParcelable(TorrentTaskServiceIPC.TAG_STATE);

                    if (state != null) {
                        fragment.get().torrentStates.put(state.torrentId, state);
                        fragment.get().reloadAdapterItem(state);
                    }
                    break;
                case TorrentTaskServiceIPC.TERMINATE_ALL_CLIENTS:
                    fragment.get().finish(new Intent(), FragmentCallback.ResultCode.SHUTDOWN);
                    break;
                case TorrentTaskServiceIPC.TORRENTS_ADDED: {
                    b = msg.getData();
                    b.setClassLoader(TorrentStateParcel.class.getClassLoader());

                    List<TorrentStateParcel> states =
                            b.getParcelableArrayList(TorrentTaskServiceIPC.TAG_STATES_LIST);

                    if (states != null && !states.isEmpty()) {
                        for (TorrentStateParcel s : states) {
                            fragment.get().torrentStates.put(s.torrentId, s);
                        }

                        fragment.get().reloadAdapter();
                    }

                    Object o = b.getSerializable(TorrentTaskServiceIPC.TAG_EXCEPTIONS_LIST);
                    if (o != null) {
                        ArrayList<Throwable> exceptions = (ArrayList<Throwable>) o;
                        for (Throwable e : exceptions) {
                            fragment.get().saveTorrentError(e);
                        }
                    }
                    break;
                }
                default:
                    super.handleMessage(msg);
            }
        }
    }

    private class ActionModeCallback implements ActionMode.Callback {
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            inActionMode = true;
            mode.getMenuInflater().inflate(R.menu.main_action_mode, menu);
            Utils.showActionModeStatusBar(activity, true);

            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            ArrayList<Integer> indexes = adapter.getSelectedItems();

            int i1 = item.getItemId();
            if (i1 == R.id.delete_torrent_menu) {
                mode.finish();

                if (getFragmentManager().findFragmentByTag(TAG_DELETE_TORRENT_DIALOG) == null) {
                    BaseAlertDialog deleteTorrentDialog = BaseAlertDialog.newInstance(
                            getString(R.string.deleting),
                            (indexes.size() > 1 ? getString(R.string.delete_selected_torrents) : getString(R.string.delete_selected_torrent)),
                            R.layout.dialog_delete_torrent,
                            getString(R.string.ok),
                            getString(R.string.cancel),
                            null,
                            MainFragment.this);

                    deleteTorrentDialog.show(getFragmentManager(), TAG_DELETE_TORRENT_DIALOG);
                }


            } else if (i1 == R.id.select_all_torrent_menu) {
                for (int i = 0; i < adapter.getItemCount(); i++) {
                    if (adapter.isSelected(i)) {
                        continue;
                    }

                    onItemSelected(adapter.getItem(i).torrentId, i);
                }


            } else if (i1 == R.id.force_recheck_torrent_menu) {
                mode.finish();

                forceRecheckRequest();

            } else if (i1 == R.id.force_announce_torrent_menu) {
                mode.finish();

                forceAnnounceRequest();

            }

            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            adapter.clearSelection();
            actionMode = null;
            inActionMode = false;
            Utils.showActionModeStatusBar(activity, false);
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        activity.getMenuInflater().inflate(R.menu.main_context, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int i = item.getItemId();
        if (i == R.id.add_link_menu) {
            addLinkDialog();

        }

        return true;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        inflater.inflate(R.menu.main, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int i = item.getItemId();
        if (i == R.id.settings_menu) {
            startActivity(new Intent(activity, SettingsActivity.class));

        } else if (i == R.id.shutdown_app_menu) {
            activity.stopService(new Intent(activity.getApplicationContext(), TorrentTaskService.class));
                /* FIXME: Fix leaking popup menu after close app */
            activity.closeOptionsMenu();
            finish(new Intent(), FragmentCallback.ResultCode.OK);

        }
        return true;
    }

    private void addLinkDialog() {
        if (getFragmentManager().findFragmentByTag(TAG_ADD_LINK_DIALOG) == null) {
            BaseAlertDialog addLinkDialog = BaseAlertDialog.newInstance(
                    getString(R.string.dialog_add_link_title),
                    null,
                    R.layout.dialog_text_input,
                    getString(R.string.ok),
                    getString(R.string.cancel),
                    null,
                    this);

            addLinkDialog.show(getFragmentManager(), TAG_ADD_LINK_DIALOG);
        }
    }

    private boolean checkEditTextField(String s, TextInputLayout layout) {
        if (s == null || layout == null) {
            return false;
        }

        if (TextUtils.isEmpty(s)) {
            layout.setErrorEnabled(true);
            layout.setError(getString(R.string.error_empty_link));
            layout.requestFocus();

            return false;
        }

        if (s.startsWith(Utils.MAGNET_PREFIX)) {
            layout.setErrorEnabled(false);
            layout.setError(null);

            return true;
        }

        if (!Patterns.WEB_URL.matcher(s).matches()) {
            layout.setErrorEnabled(true);
            layout.setError(getString(R.string.error_invalid_link));
            layout.requestFocus();

            return false;
        }

        layout.setErrorEnabled(false);
        layout.setError(null);

        return true;
    }

    @Override
    public void onPauseButtonClicked(int position, TorrentStateParcel torrentState) {
        pauseResumeTorrentRequest(torrentState.torrentId);
    }

    @Override
    public void onItemClicked(int position, TorrentStateParcel torrentState) {
        if (actionMode == null) {
            /* Mark this torrent as open in the list */
            adapter.markAsOpen(torrentState);

            showDetailTorrent(torrentState.torrentId);
        } else {
            onItemSelected(torrentState.torrentId, position);
        }
    }

    @Override
    public boolean onItemLongClicked(int position, TorrentStateParcel torrentState) {
        if (actionMode == null) {
            actionMode = activity.startActionMode(actionModeCallback);
        }

        onItemSelected(torrentState.torrentId, position);

        return true;
    }

    private void onItemSelected(String id, int position) {
        toggleSelection(position);

        if (selectedTorrents.contains(id)) {
            selectedTorrents.remove(id);
        } else {
            selectedTorrents.add(id);
        }
    }

    private void toggleSelection(int position) {
        adapter.toggleSelection(position);
        int count = adapter.getSelectedItemCount();

        if (count == 0) {
            actionMode.finish();
        } else {
            actionMode.setTitle(String.valueOf(count));
            actionMode.invalidate();
        }
    }

    /*
     * Uncheck current torrent from the list and set blank fragment.
     */

    public void resetCurOpenTorrent() {
        adapter.markAsOpen(null);
        showBlankFragment();
    }

    @Override
    public void onShow(final AlertDialog dialog) {
        if (dialog != null) {
            if (getFragmentManager().findFragmentByTag(TAG_ADD_LINK_DIALOG) != null) {
                initAddDialog(dialog);
            }
        }
    }

    private void initAddDialog(final AlertDialog dialog) {
        final TextInputEditText field =
                (TextInputEditText) dialog.findViewById(R.id.text_input_dialog);
        final TextInputLayout fieldLayout =
                (TextInputLayout) dialog.findViewById(R.id.layout_text_input_dialog);

        /* Dismiss error label if user has changed the text */
        if (field != null && fieldLayout != null) {
            field.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    /* Nothing */
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    fieldLayout.setErrorEnabled(false);
                    fieldLayout.setError(null);
                }

                @Override
                public void afterTextChanged(Editable s) {
                    /* Nothing */
                }
            });
        }

        /*
         * It is necessary in order to the dialog is not closed by
         * pressing positive button if the text checker gave a false result
         */
        Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);

        positiveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (field != null && fieldLayout != null) {
                    String link = field.getText().toString();

                    if (checkEditTextField(link, fieldLayout)) {
                        String url;

                        if (link.startsWith(Utils.MAGNET_PREFIX)) {
                            url = link;
                        } else {
                            url = Utils.normalizeURL(link);
                        }

                        if (url != null) {
                            addTorrentDialog(Uri.parse(url));
                        }

                        dialog.dismiss();
                    }
                }
            }
        });

        /* Inserting a link from the clipboard */
        String clipboard = Utils.getClipboard(activity.getApplicationContext());
        String url;

        if (clipboard != null) {
            if (!clipboard.startsWith(Utils.MAGNET_PREFIX)) {
                url = Utils.normalizeURL(clipboard);
            } else {
                url = clipboard;
            }

            if (field != null && url != null) {
                field.setText(url);
            }
        }
    }

    @Override
    public void onPositiveClicked(@Nullable View v) {
        if (v != null) {
            if (getFragmentManager().findFragmentByTag(TAG_DELETE_TORRENT_DIALOG) != null) {
                CheckBox withFiles = (CheckBox) v.findViewById(R.id.dialog_delete_torrent_with_downloaded_files);

                DetailTorrentFragment f = getCurrentDetailFragment();
                if (f != null) {
                    String id = f.getTorrentId();
                    if (selectedTorrents.contains(id)) {
                        resetCurOpenTorrent();
                    }
                }

                deleteTorrentsRequest(withFiles.isChecked());

                selectedTorrents.clear();
            } else if (getFragmentManager().findFragmentByTag(TAG_ERROR_OPEN_TORRENT_FILE_DIALOG) != null ||
                    getFragmentManager().findFragmentByTag(TAG_SAVE_ERROR_DIALOG) != null) {
                if (sentError != null) {
                    EditText editText = (EditText) v.findViewById(R.id.comment);
                    String comment = editText.getText().toString();

                    Utils.reportError(sentError, comment);
                }
            }
        }
    }

    @Override
    public void onNegativeClicked(@Nullable View v) {
        if (getFragmentManager().findFragmentByTag(TAG_DELETE_TORRENT_DIALOG) != null) {
            selectedTorrents.clear();
        }
    }

    @Override
    public void onNeutralClicked(@Nullable View v) {
        /* Nothing */
    }

    private void reloadAdapterItem(TorrentStateParcel state) {
        sync.lock();

        try {
            adapter.updateItem(state);

        } finally {
            sync.unlock();
        }
    }

    final synchronized void reloadAdapter() {
        adapter.clearAll();

        if (torrentStates == null || torrentStates.size() == 0) {
            adapter.notifyDataSetChanged();
        } else {
            adapter.addItems(torrentStates.values());
        }
    }

    private void addTorrentDialog(Uri uri) {
//        if (uri == null) {
//            return;
//        }
//
//        Intent i = new Intent(activity, AddTorrentActivity.class);
//        i.putExtra(AddTorrentActivity.TAG_URI, uri);
//        startActivityForResult(i, ADD_TORRENT_REQUEST);
    }

    private void showDetailTorrent(String id) {
        if (Utils.isTwoPane(activity.getApplicationContext())) {
            FragmentManager fm = getFragmentManager();

            DetailTorrentFragment detail = DetailTorrentFragment.newInstance(id);

            Fragment fragment = fm.findFragmentById(R.id.detail_torrent_fragmentContainer);
            if (fragment != null && fragment instanceof DetailTorrentFragment) {
                String oldId = ((DetailTorrentFragment) fragment).getTorrentId();

                if (oldId != null && id.equals(oldId)) {
                    return;
                }
            }

            fm.beginTransaction()
                    .replace(R.id.detail_torrent_fragmentContainer, detail)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .commit();

        } else {
            Intent i = new Intent(activity, DetailTorrentActivity.class);
            i.putExtra(DetailTorrentActivity.TAG_TORRENT_ID, id);
            startActivity(i);
        }
    }

    private void showBlankFragment() {
        if (Utils.isTablet(activity.getApplicationContext())) {
            FragmentManager fm = getFragmentManager();

            BlankFragment blank = BlankFragment.newInstance();

            fm.beginTransaction()
                    .replace(R.id.detail_torrent_fragmentContainer, blank)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
                    .commitAllowingStateLoss();
        }
    }

    public DetailTorrentFragment getCurrentDetailFragment() {
        if (!Utils.isTwoPane(activity.getApplicationContext())) {
            return null;
        }

        Fragment fragment = getFragmentManager()
                .findFragmentById(R.id.detail_torrent_fragmentContainer);

        return (fragment instanceof DetailTorrentFragment ? (DetailTorrentFragment) fragment : null);
    }

    private void deleteTorrentsRequest(boolean withFiles) {
        if (!bound || serviceCallback == null) {
            return;
        }

        try {
            ipc.sendDeleteTorrents(serviceCallback, selectedTorrents, withFiles);

        } catch (RemoteException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    private void forceRecheckRequest() {
        if (!bound || serviceCallback == null) {
            return;
        }

        try {
            ipc.sendForceRecheck(serviceCallback, selectedTorrents);

        } catch (RemoteException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }

        selectedTorrents.clear();
    }

    private void forceAnnounceRequest() {
        if (!bound || serviceCallback == null) {
            return;
        }

        try {
            ipc.sendForceAnnounce(serviceCallback, selectedTorrents);

        } catch (RemoteException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }

        selectedTorrents.clear();
    }

    private void addTorrentsRequest(Collection<Torrent> torrents) {
        if (!bound || serviceCallback == null) {
            torrentsQueue.addAll(torrents);

            return;
        }

        try {
            ipc.sendAddTorrents(serviceCallback, new ArrayList<>(torrents));

        } catch (RemoteException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    private void saveTorrentError(Throwable e) {
        if (e == null || !isAdded()) {
            return;
        }

        sentError = e;

        if (e instanceof FileNotFoundException) {
            ErrorReportAlertDialog errDialog = ErrorReportAlertDialog.newInstance(
                    activity.getApplicationContext(),
                    getString(R.string.error),
                    getString(R.string.error_file_not_found_add_torrent),
                    Log.getStackTraceString(e),
                    this);

            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.add(errDialog, TAG_SAVE_ERROR_DIALOG);
            ft.commitAllowingStateLoss();

        } else if (e instanceof IOException) {
            ErrorReportAlertDialog errDialog = ErrorReportAlertDialog.newInstance(
                    activity.getApplicationContext(),
                    getString(R.string.error),
                    getString(R.string.error_io_add_torrent),
                    Log.getStackTraceString(e),
                    this);

            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.add(errDialog, TAG_SAVE_ERROR_DIALOG);
            ft.commitAllowingStateLoss();

        } else if (e instanceof FileAlreadyExistsException) {
            Snackbar.make(coordinatorLayout,
                    R.string.torrent_exist,
                    Snackbar.LENGTH_LONG)
                    .show();
        } else {
            ErrorReportAlertDialog errDialog = ErrorReportAlertDialog.newInstance(
                    activity.getApplicationContext(),
                    getString(R.string.error),
                    getString(R.string.error_add_torrent),
                    Log.getStackTraceString(e),
                    this);

            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.add(errDialog, TAG_SAVE_ERROR_DIALOG);
            ft.commitAllowingStateLoss();
        }
    }

    private void pauseResumeTorrentRequest(String id) {
        if (!bound || serviceCallback == null) {
            return;
        }

        ArrayList<String> list = new ArrayList<>();
        list.add(id);

        try {
            ipc.sendPauseResumeTorrents(serviceCallback, list);

        } catch (RemoteException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case TORRENT_FILE_CHOOSE_REQUEST:
                if (resultCode == Activity.RESULT_OK) {
                    if (data.hasExtra(FileManagerDialog.TAG_RETURNED_PATH)) {
                        String path = data.getStringExtra(FileManagerDialog.TAG_RETURNED_PATH);

                        try {
                            addTorrentDialog(Uri.fromFile(new File(path)));

                        } catch (Exception e) {
                            sentError = e;

                            Log.e(TAG, Log.getStackTraceString(e));

                            if (getFragmentManager()
                                    .findFragmentByTag(TAG_ERROR_OPEN_TORRENT_FILE_DIALOG) == null) {
                                ErrorReportAlertDialog errDialog = ErrorReportAlertDialog.newInstance(
                                        activity.getApplicationContext(),
                                        getString(R.string.error),
                                        getString(R.string.error_open_torrent_file),
                                        Log.getStackTraceString(e),
                                        this);

                                FragmentTransaction ft = getFragmentManager().beginTransaction();
                                ft.add(errDialog, TAG_ERROR_OPEN_TORRENT_FILE_DIALOG);
                                ft.commitAllowingStateLoss();
                            }
                        }
                    }
                }
                break;
            case ADD_TORRENT_REQUEST:
//                if (resultCode == Activity.RESULT_OK) {
//                    if (data.hasExtra(AddTorrentActivity.TAG_RESULT_TORRENT)) {
//                        Torrent torrent = data.getParcelableExtra(AddTorrentActivity.TAG_RESULT_TORRENT);
//                        if (torrent != null) {
//                            ArrayList<Torrent> list = new ArrayList<>();
//                            list.add(torrent);
//                            addTorrentsRequest(list);
//                        }
//                    }
//                }
                break;
        }
    }

    private void finish(Intent intent, FragmentCallback.ResultCode code) {
        ((FragmentCallback) activity).fragmentFinished(intent, code);
    }
}
