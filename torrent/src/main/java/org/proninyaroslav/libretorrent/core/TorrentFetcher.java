package org.proninyaroslav.libretorrent.core;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.frostwire.jlibtorrent.SessionManager;

import org.proninyaroslav.libretorrent.core.exceptions.FetchLinkException;
import org.proninyaroslav.libretorrent.core.utils.TorrentUtils;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.utils.FileUtils;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/*
 * A class for downloading metadata from magnet, http and https links and
 * then saving it in the .torrent file.
 */

public class TorrentFetcher {
    @SuppressWarnings("unused")
    private static final String TAG = TorrentFetcher.class.getSimpleName();

    private static final int FETCH_MAGNET_SECONDS = 60;

    /*
     * Returns a temporary torrent file.
     */
    public static File fetch(Context context, Uri uri, File saveDir) throws FetchLinkException {
        File tempTorrent;

        if (saveDir == null) {
            throw new FetchLinkException("Temp dir not found");
        }

        try {
            if (uri == null || uri.getScheme() == null) {
                throw new IllegalArgumentException("Can't decode link");
            }

            if (!Utils.checkNetworkConnection(context)) {
                throw new FetchLinkException("No network connection");
            }

            switch (uri.getScheme()) {
                case Utils.MAGNET_PREFIX:
                    tempTorrent = TorrentUtils.createTempTorrentFile(fetchMagnet(uri), saveDir);
                    break;
                case Utils.HTTP_PREFIX:
                case Utils.HTTPS_PREFIX:
                    tempTorrent = TorrentUtils.createTempTorrentFile(saveDir);
                    fetchHTTP(uri, tempTorrent);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown link type: " + uri.getScheme());
            }

        } catch (Exception e) {
            throw new FetchLinkException(e);
        }

        return tempTorrent;
    }

    private static byte[] fetchMagnet(Uri uri) throws FetchLinkException {
        if (uri == null || uri.getScheme() == null) {
            throw new IllegalArgumentException("Can't decode link");
        }

        final SessionManager s = new SessionManager();
        final CountDownLatch signal = new CountDownLatch(1);

        s.start();

        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                long nodes = s.stats().dhtNodes();
                /* Wait for at least 10 nodes in the DHT */
                if (nodes >= 10) {
                    Log.i(TAG, "DHT contains " + nodes + " nodes");

                    signal.countDown();
                    timer.cancel();
                }
            }
        }, 0, 1000);

        Log.i(TAG, "Waiting for nodes in DHT (10 seconds)...");
        try {
            boolean success = signal.await(10, TimeUnit.SECONDS);

            if (!success) {
                throw new FetchLinkException("DHT bootstrap timeout");
            }
        } catch (InterruptedException ignore) {
        }

        Log.i(TAG, "Fetching the magnet link...");
        byte[] data = s.fetchMagnet(uri.toString(), FETCH_MAGNET_SECONDS);

        s.stop();

        return data;
    }

    private static void fetchHTTP(Uri uri, final File targetFile) throws Exception {
        if (uri == null || uri.getScheme() == null) {
            throw new IllegalArgumentException("Can't decode link");
        }

        Log.i(TAG, "Fetching link...");
        Response response = null;
        try {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(uri.toString()).build();
            response = client.newCall(request).execute();
            FileUtils.copyToFile(response.body().byteStream(), targetFile);
        } finally {
            FileUtils.closeQuietly(response);
        }
    }
}
