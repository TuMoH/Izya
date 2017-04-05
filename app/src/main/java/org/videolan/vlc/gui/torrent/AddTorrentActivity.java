package org.videolan.vlc.gui.torrent;

import android.app.FragmentTransaction;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.frostwire.jlibtorrent.Priority;
import com.timursoft.izya.R;

import org.proninyaroslav.libretorrent.core.BencodeFileItem;
import org.proninyaroslav.libretorrent.core.Torrent;
import org.proninyaroslav.libretorrent.core.TorrentFetcher;
import org.proninyaroslav.libretorrent.core.TorrentMetaInfo;
import org.proninyaroslav.libretorrent.core.TorrentTaskServiceIPC;
import org.proninyaroslav.libretorrent.core.exceptions.DecodeException;
import org.proninyaroslav.libretorrent.core.exceptions.FetchLinkException;
import org.proninyaroslav.libretorrent.core.filetree.BencodeFileTree;
import org.proninyaroslav.libretorrent.core.filetree.FileNode;
import org.proninyaroslav.libretorrent.core.stateparcel.TorrentStateParcel;
import org.proninyaroslav.libretorrent.core.utils.BencodeFileTreeUtils;
import org.proninyaroslav.libretorrent.core.utils.FileIOUtils;
import org.proninyaroslav.libretorrent.core.utils.TorrentUtils;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.dialogs.BaseAlertDialog;
import org.proninyaroslav.libretorrent.dialogs.ErrorReportAlertDialog;
import org.proninyaroslav.libretorrent.dialogs.SpinnerProgressDialog;
import org.proninyaroslav.libretorrent.fragments.FragmentCallback;
import org.proninyaroslav.libretorrent.services.TorrentTaskService;
import org.proninyaroslav.libretorrent.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class AddTorrentActivity extends AppCompatActivity
        implements BaseAlertDialog.OnClickListener, DownloadableFilesAdapter.ViewHolder.ClickListener {

    private static final String TAG = "VLC/AddTorrentActivity";

    private static final String TAG_SPINNER_PROGRESS = "spinner_progress";
    private static final String TAG_IO_EXCEPT_DIALOG = "io_except_dialog";
    private static final String TAG_DECODE_EXCEPT_DIALOG = "decode_except_dialog";
    private static final String TAG_FETCH_EXCEPT_DIALOG = "fetch_except_dialog";
    private static final String TAG_ILLEGAL_ARGUMENT = "illegal_argument";

    private static final String TAG_HAS_TORRENT = "has_torrent";
    private static final String TAG_PATH_TO_TEMP_TORRENT = "path_to_temp_torrent";
    private static final String TAG_SAVE_TORRENT_FILE = "save_torrent_file";
    private static final String TAG_LIST_FILES_STATE = "list_files_state";
    private static final String TAG_FILE_TREE = "file_tree";
    private static final String TAG_CUR_DIR = "cur_dir";

    public static final String TAG_URI = "uri";

    private SpinnerProgressDialog progress;
    private Exception sentError;

    private RecyclerView fileList;
    private LinearLayoutManager layoutManager;
    private DownloadableFilesAdapter adapter;
    /* Save state scrolling */
    private TextView torrentNameField;
    private TextView filesSize;

    BencodeFileTree fileTree;
    BencodeFileTree curDir;

    private Uri uri;
    private TorrentMetaInfo info;
    private TorrentDecodeTask decodeTask;

    private String pathToTempTorrent;
    private boolean saveTorrentFile = true;
    private boolean hasTorrent = false;

    /* Messenger for communicating with the service. */
    private Messenger serviceCallback = null;
    private Messenger clientCallback = new Messenger(new CallbackHandler());
    private TorrentTaskServiceIPC ipc = new TorrentTaskServiceIPC();
    /* Flag indicating whether we have called bind on the service. */
    private boolean bound;
    /*
   * Torrents are added to the queue, if the client is not bounded to service.
   * Trying to add torrents will be made at the first connect.
   */
    private HashSet<Torrent> torrentsQueue = new HashSet<>();
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_add_torrent);

        Toolbar toolbar = (Toolbar) findViewById(R.id.main_toolbar);
        if (toolbar != null) {
            toolbar.setTitle(R.string.add_torrent_title);
            setSupportActionBar(toolbar);
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        torrentNameField = (TextView) findViewById(R.id.torrent_name);
        filesSize = (TextView) findViewById(R.id.files_size);
        findViewById(R.id.ok_button).setOnClickListener(v -> buildTorrent());

        fileList = (RecyclerView) findViewById(R.id.file_list);
        layoutManager = new LinearLayoutManager(this);
        fileList.setLayoutManager(layoutManager);
        fileList.setItemAnimator(new DefaultItemAnimator());

        Intent intent = getIntent();
        if (intent.getData() != null) {
            /* Implicit intent with path to torrent file, http or magnet link */
            uri = intent.getData();
        } else {
            uri = intent.getParcelableExtra(TAG_URI);
        }

        if (savedInstanceState != null) {
            pathToTempTorrent = savedInstanceState.getString(TAG_PATH_TO_TEMP_TORRENT);
            saveTorrentFile = savedInstanceState.getBoolean(TAG_SAVE_TORRENT_FILE);
            hasTorrent = savedInstanceState.getBoolean(TAG_HAS_TORRENT);
            fileTree = (BencodeFileTree) savedInstanceState.getSerializable(TAG_FILE_TREE);
            curDir = (BencodeFileTree) savedInstanceState.getSerializable(TAG_CUR_DIR);
            /*
             * No initialize fragments in the event of an decode error or
             * torrent decoding in process (after configuration changes)
             */
            if (hasTorrent) {
                updateUI();
            }
        } else {
            final StringBuilder progressDialogText = new StringBuilder("");
            if (uri == null || uri.getScheme() == null) {
                progressDialogText.append(getString(R.string.decode_torrent_default_message));
            } else {
                switch (uri.getScheme()) {
                    case Utils.MAGNET_PREFIX:
                        progressDialogText.append(getString(R.string.decode_torrent_fetch_magnet_message));
                        break;
                    case Utils.HTTP_PREFIX:
                    case Utils.HTTPS_PREFIX:
                        progressDialogText.append(getString(R.string.decode_torrent_downloading_torrent_message));
                        break;
                    default:
                        progressDialogText.append(getString(R.string.decode_torrent_default_message));
                        break;
                }
            }

            decodeTask = new TorrentDecodeTask(progressDialogText.toString());
            decodeTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, uri);
        }

        bindService(new Intent(getApplicationContext(), TorrentTaskService.class),
                connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        decodeTask.cancel(true);

        if (!saveTorrentFile && pathToTempTorrent != null) {
            try {
                FileUtils.forceDelete(new File(pathToTempTorrent));
            } catch (IOException e) {
                Log.w(TAG, "Could not delete temp file: ", e);
            }
        }

        if (bound) {
            try {
                ipc.sendClientDisconnect(serviceCallback, clientCallback);
            } catch (RemoteException e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
            unbindService(connection);
            bound = false;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
        }
        return true;
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        progress = (SpinnerProgressDialog) getFragmentManager().findFragmentByTag(TAG_SPINNER_PROGRESS);
    }

    public void onPostExecuteHandleExceptions(Exception e) {
        if (e != null) {
            if (e instanceof DecodeException) {
                if (getFragmentManager().findFragmentByTag(TAG_DECODE_EXCEPT_DIALOG) == null) {
                    BaseAlertDialog errDialog = BaseAlertDialog.newInstance(
                            getString(R.string.error),
                            getString(R.string.error_decode_torrent),
                            0,
                            getString(R.string.ok),
                            null,
                            null,
                            this);

                    FragmentTransaction ft = getFragmentManager().beginTransaction();
                    ft.add(errDialog, TAG_DECODE_EXCEPT_DIALOG);
                    ft.commitAllowingStateLoss();
                }

            } else if (e instanceof FetchLinkException) {
                if (getFragmentManager().findFragmentByTag(TAG_FETCH_EXCEPT_DIALOG) == null) {
                    BaseAlertDialog errDialog = BaseAlertDialog.newInstance(
                            getString(R.string.error),
                            getString(R.string.error_fetch_link),
                            0,
                            getString(R.string.ok),
                            null,
                            null,
                            this);

                    FragmentTransaction ft = getFragmentManager().beginTransaction();
                    ft.add(errDialog, TAG_FETCH_EXCEPT_DIALOG);
                    ft.commitAllowingStateLoss();
                }

            } else if (e instanceof IllegalArgumentException) {
                if (getFragmentManager().findFragmentByTag(TAG_ILLEGAL_ARGUMENT) == null) {
                    BaseAlertDialog errDialog = BaseAlertDialog.newInstance(
                            getString(R.string.error),
                            getString(R.string.error_invalid_link_or_path),
                            0,
                            getString(R.string.ok),
                            null,
                            null,
                            this);

                    FragmentTransaction ft = getFragmentManager().beginTransaction();
                    ft.add(errDialog, TAG_ILLEGAL_ARGUMENT);
                    ft.commitAllowingStateLoss();
                }

            } else if (e instanceof IOException) {
                sentError = e;
                if (getFragmentManager().findFragmentByTag(TAG_IO_EXCEPT_DIALOG) == null) {
                    ErrorReportAlertDialog errDialog = ErrorReportAlertDialog.newInstance(
                            getApplicationContext(),
                            getString(R.string.error),
                            getString(R.string.error_io_torrent),
                            Log.getStackTraceString(e),
                            this);

                    FragmentTransaction ft = getFragmentManager().beginTransaction();
                    ft.add(errDialog, TAG_IO_EXCEPT_DIALOG);
                    ft.commitAllowingStateLoss();
                }
            }
        }
    }

    @Override
    public void onPositiveClicked(@Nullable View v) {
        if (sentError != null) {
            String comment = null;

            if (v != null) {
                EditText editText = (EditText) v.findViewById(R.id.comment);
                comment = editText.getText().toString();
            }

            Utils.reportError(sentError, comment);
        }

        finish();
    }

    @Override
    public void onNegativeClicked(@Nullable View v) {
        finish();
    }

    @Override
    public void onNeutralClicked(@Nullable View v) {
        /* Nothing */
    }

    private void dismissProgress() {
        if (progress != null) {
            try {
                progress.dismiss();
            } catch (Exception e) {
                /* Ignore */
            }
        }

        progress = null;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putParcelable(TAG_URI, uri);
        outState.putBoolean(TAG_HAS_TORRENT, hasTorrent);
        outState.putString(TAG_PATH_TO_TEMP_TORRENT, pathToTempTorrent);
        outState.putBoolean(TAG_SAVE_TORRENT_FILE, saveTorrentFile);

        outState.putSerializable(TAG_FILE_TREE, fileTree);
        outState.putSerializable(TAG_CUR_DIR, curDir);
        outState.putParcelable(TAG_LIST_FILES_STATE, layoutManager.onSaveInstanceState());

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState, PersistableBundle persistentState) {
        super.onRestoreInstanceState(savedInstanceState, persistentState);

        if (savedInstanceState != null) {
            layoutManager.onRestoreInstanceState(savedInstanceState.getParcelable(TAG_LIST_FILES_STATE));
        }
    }

    @Override
    public void onItemClicked(BencodeFileTree node) {
        if (node.getName().equals(BencodeFileTree.PARENT_DIR)) {
            backToParent();

            return;
        }

        if (node.getType() == FileNode.Type.DIR) {
            chooseDirectory(node);
            reloadData();
        }
    }

    @Override
    public void onItemCheckedChanged(BencodeFileTree node, boolean selected) {
        node.select(selected);
        updateFilesSizeText();
    }

    private void updateFilesSizeText() {
        Context context = getApplicationContext();
        String downloadDir = TorrentUtils.getTorrentDownloadPath(context);
        String freeSpace = String.format(getString(R.string.free_space),
                Formatter.formatFileSize(context, FileIOUtils.getFreeSpace(downloadDir)));

        filesSize.setText(String.format(getString(R.string.files_size),
                Formatter.formatFileSize(context, fileTree.selectedFileSize()),
                Formatter.formatFileSize(context, fileTree.size())) + "  " + freeSpace);
    }

    private List<BencodeFileTree> getChildren(BencodeFileTree node) {
        List<BencodeFileTree> children = new ArrayList<>();

        if (node.isFile()) {
            return children;
        }

        /* Adding parent dir for navigation */
        if (curDir != fileTree && curDir.getParent() != null) {
            children.add(0, new BencodeFileTree(BencodeFileTree.PARENT_DIR, 0L, FileNode.Type.DIR, curDir.getParent()));
        }

        children.addAll(curDir.getChildren());

        return children;
    }

    private void chooseDirectory(BencodeFileTree node) {
        if (node.isFile()) {
            node = fileTree;
        }
        curDir = node;
    }

    private void backToParent() {
        curDir = curDir.getParent();
        reloadData();
    }

    private synchronized void reloadData() {
        adapter.clearFiles();

        List<BencodeFileTree> children = getChildren(curDir);
        if (children.size() == 0) {
            adapter.notifyDataSetChanged();
        } else {
            adapter.addFiles(children);
        }
    }

    public ArrayList<Integer> getSelectedFileIndexes() {
        List<BencodeFileTree> files = BencodeFileTreeUtils.getFiles(fileTree);
        ArrayList<Integer> indexes = new ArrayList<>();
        for (BencodeFileTree file : files) {
            if (file.isSelected()) {
                indexes.add(file.getIndex());
            }
        }
        return indexes;
    }

    private class TorrentDecodeTask extends AsyncTask<Uri, Void, Exception> {
        String progressDialogText;

        public TorrentDecodeTask(String progressDialogText) {
            this.progressDialogText = progressDialogText;
        }

        @Override
        protected void onPreExecute() {
            progress = SpinnerProgressDialog.newInstance(
                    R.string.decode_torrent_progress_title, progressDialogText, 0, true, true);
            progress.show(getFragmentManager(), TAG_SPINNER_PROGRESS);
        }

        @Override
        protected Exception doInBackground(Uri... params) {
            Uri uri = params[0];

            if (uri == null || uri.getScheme() == null) {
                IllegalArgumentException e = new IllegalArgumentException("Can't decode link/path");
                Log.e(TAG, Log.getStackTraceString(e));
                return e;
            }

            try {
                switch (uri.getScheme()) {
                    case Utils.FILE_PREFIX:
                        pathToTempTorrent = uri.getPath();
                        break;
                    case Utils.CONTENT_PREFIX:
                        pathToTempTorrent = Utils.getRealPathFromURI(getApplicationContext(), uri);
                        break;
                    case Utils.MAGNET_PREFIX:
                    case Utils.HTTP_PREFIX:
                    case Utils.HTTPS_PREFIX:
                        File torrentFile = TorrentFetcher.fetch(getApplicationContext(),
                                uri, FileIOUtils.getTempDir(getApplicationContext()));

                        if (torrentFile != null && torrentFile.exists()) {
                            pathToTempTorrent = torrentFile.getAbsolutePath();
                            saveTorrentFile = false;
                        } else {
                            IllegalArgumentException e = new IllegalArgumentException("Unknown path to torrent file");
                            Log.e(TAG, Log.getStackTraceString(e));

                            return e;
                        }
                        break;
                    default:
                        IllegalArgumentException e =
                                new IllegalArgumentException("Unknown link/path type: " + uri.getScheme());
                        Log.e(TAG, Log.getStackTraceString(e));
                        return e;
                }
                info = new TorrentMetaInfo(pathToTempTorrent);
            } catch (Exception e) {
                Log.e(TAG, Log.getStackTraceString(e));
                return e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Exception e) {
            dismissProgress();

            onPostExecuteHandleExceptions(e);

            if (e != null) {
                return;
            }

            hasTorrent = true;
            updateUI();
        }
    }

    private void updateUI() {
        if (info != null) {
            torrentNameField.setText(info.getTorrentName());

            // from files fragment
            if (fileTree == null || curDir == null) {
                ArrayList<BencodeFileItem> files = info.getFiles();
                fileTree = BencodeFileTreeUtils.buildFileTree(files);
                fileTree.select(true);
            /* Is assigned the root dir of the file tree */
                curDir = fileTree;
            }

            updateFilesSizeText();

            adapter = new DownloadableFilesAdapter(getChildren(curDir),
                    this, R.layout.item_torrent_downloadable_file, this);

            fileList.setAdapter(adapter);
        }
    }

    private void buildTorrent() {
        ArrayList<Integer> selectedIndexes = getSelectedFileIndexes();

        if (info != null) {
            ArrayList<BencodeFileItem> files = info.getFiles();
            if (files.size() != 0 && selectedIndexes.size() != 0 && !TextUtils.isEmpty(info.getTorrentName())) {
                String downloadDir = TorrentUtils.getTorrentDownloadPath(getApplicationContext());
                if (FileIOUtils.getFreeSpace(downloadDir) >= fileTree.selectedFileSize()) {
                    ArrayList<Integer> priorities = new ArrayList<>(
                            Collections.nCopies(files.size(), Priority.IGNORE.swig()));

                    for (int index : selectedIndexes) {
                        priorities.set(index, Priority.NORMAL.swig());
                    }

                    Torrent torrent = new Torrent(info.getSha1Hash(),
                            info.getTorrentName(), priorities, downloadDir);

                    torrent.setSequentialDownload(true);
                    torrent.setTorrentFilePath(pathToTempTorrent);

                    saveTorrentFile = true;

                    addTorrentsRequest(Collections.singleton(torrent));
//                    finish();
                } else {
                    showSnackbar(R.string.error_free_space, Snackbar.LENGTH_LONG);
                }

            } else if (selectedIndexes.size() == 0) {
                showSnackbar(R.string.error_no_files_selected, Snackbar.LENGTH_LONG);
            }
        }
    }

    private void showSnackbar(@StringRes int resId, int duration) {
        View view = findViewById(android.R.id.content);
        if (view != null) {
            Snackbar.make(view, resId, duration).show();
        }
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

    private class CallbackHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Bundle b;
            TorrentStateParcel state;

            switch (msg.what) {
                case TorrentTaskServiceIPC.UPDATE_STATES_ONESHOT: {
                    b = msg.getData();
                    b.setClassLoader(TorrentStateParcel.class.getClassLoader());

//                    Bundle states = b.getParcelable(TorrentTaskServiceIPC.TAG_STATES_LIST);
//                    if (states != null) {
//                        fragment.get().torrentStates.clear();
//
//                        for (String key : states.keySet()) {
//                            state = states.getParcelable(key);
//                            if (state != null) {
//                                fragment.get().torrentStates.put(state.torrentId, state);
//                            }
//                        }
//
//                        fragment.get().reloadAdapter();
//                    }
                    break;
                }
                case TorrentTaskServiceIPC.UPDATE_STATE:
                    b = msg.getData();
                    b.setClassLoader(TorrentStateParcel.class.getClassLoader());
                    state = b.getParcelable(TorrentTaskServiceIPC.TAG_STATE);

//                    if (state != null) {
//                        fragment.get().torrentStates.put(state.torrentId, state);
//                        fragment.get().reloadAdapterItem(state);
//                    }
                    break;
                case TorrentTaskServiceIPC.TERMINATE_ALL_CLIENTS:
                    finish();
                    break;
                case TorrentTaskServiceIPC.TORRENTS_ADDED: {
                    b = msg.getData();
                    b.setClassLoader(TorrentStateParcel.class.getClassLoader());

                    List<TorrentStateParcel> states =
                            b.getParcelableArrayList(TorrentTaskServiceIPC.TAG_STATES_LIST);

//                    if (states != null && !states.isEmpty()) {
//                        for (TorrentStateParcel s : states) {
//                            fragment.get().torrentStates.put(s.torrentId, s);
//                        }
//
//                        fragment.get().reloadAdapter();
//                    }
//
//                    Object o = b.getSerializable(TorrentTaskServiceIPC.TAG_EXCEPTIONS_LIST);
//                    if (o != null) {
//                        ArrayList<Throwable> exceptions = (ArrayList<Throwable>) o;
//                        for (Throwable e : exceptions) {
//                            fragment.get().saveTorrentError(e);
//                        }
//                    }
                    break;
                }
                default:
                    super.handleMessage(msg);
            }
        }
    }

}
