package org.videolan.vlc.gui.video;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import org.proninyaroslav.libretorrent.TorrentTaskService;
import org.proninyaroslav.libretorrent.core.Torrent;
import org.proninyaroslav.libretorrent.core.TorrentTaskServiceIPC;
import org.proninyaroslav.libretorrent.core.stateparcel.TorrentStateParcel;

import java.util.List;

public class TorrentController {

    public interface Listener {
        void ready(String filePath);

        void pause();
    }

    private final Context context;
    private final Listener listener;

    private Messenger torrentServiceCallback;
    private final Messenger torrentClientCallback;
    private final TorrentTaskServiceIPC torrentIpc;
    private final TorrentServiceConnection torrentConnection;
    private Torrent torrent;

    public TorrentController(Context context, Torrent torrent, Listener listener) {
        this.context = context;
        this.listener = listener;
        this.torrent = torrent;

        String filePath = "file://" + this.torrent.getDownloadPath() + "/" + this.torrent.getName();
        torrentClientCallback = new Messenger(new TorrentCallbackHandler(listener, filePath));
        torrentIpc = new TorrentTaskServiceIPC();
        torrentConnection = new TorrentServiceConnection();

        start();
    }

    private void start() {
        context.startService(new Intent(context, TorrentTaskService.class));
        context.bindService(new Intent(context.getApplicationContext(), TorrentTaskService.class),
                torrentConnection, Context.BIND_AUTO_CREATE);
        addTorrentsRequest(torrent);
    }

    public void terminate() {
        if (torrentConnection.bound) {
            try {
                torrentIpc.sendClientDisconnect(torrentServiceCallback, torrentClientCallback);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            context.unbindService(torrentConnection);
            torrentConnection.bound = false;
        }
    }

    public void onTimeChanged(int pos) {

    }

    public boolean isReady(int pos) {
        return false;
    }


    private static class TorrentCallbackHandler extends Handler {
        private final Listener listener;
        private final String filePath;
        private boolean ready = false;

        private TorrentCallbackHandler(Listener listener, String filePath) {
            this.listener = listener;
            this.filePath = filePath;
        }

        @Override
        public void handleMessage(Message msg) {
            Bundle b;
            TorrentStateParcel state;

            switch (msg.what) {
                case TorrentTaskServiceIPC.UPDATE_STATE: {
                    b = msg.getData();
                    b.setClassLoader(TorrentStateParcel.class.getClassLoader());
                    state = b.getParcelable(TorrentTaskServiceIPC.TAG_STATE);
                    if (state != null && state.isReadyForPlaing && !ready) {
                        listener.ready(filePath);
                        ready = true;
                    }
                    break;
                }
                case TorrentTaskServiceIPC.TORRENTS_ADDED: {
                    b = msg.getData();
                    b.setClassLoader(TorrentStateParcel.class.getClassLoader());

                    List<TorrentStateParcel> states =
                            b.getParcelableArrayList(TorrentTaskServiceIPC.TAG_STATES_LIST);
                    break;
                }
                default:
                    super.handleMessage(msg);
            }
        }
    }

    private class TorrentServiceConnection implements ServiceConnection {
        /* Flag indicating whether we have called bind on the service. */
        public boolean bound;

        public void onServiceConnected(ComponentName className, IBinder service) {
            torrentServiceCallback = new Messenger(service);
            bound = true;

            if (torrent != null) {
                addTorrentsRequest(torrent);
                torrent = null;
            }

            try {
                torrentIpc.sendClientConnect(torrentServiceCallback, torrentClientCallback);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            torrentServiceCallback = null;
            bound = false;
        }
    }

    private void addTorrentsRequest(Torrent torrent) {
        if (!torrentConnection.bound || torrentServiceCallback == null) {
            this.torrent = torrent;
            return;
        }
        try {
            torrentIpc.sendAddTorrent(torrentServiceCallback, torrent);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

}
