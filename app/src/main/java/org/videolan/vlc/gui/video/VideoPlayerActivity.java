/* ***************************************************************************
 * VideoPlayerActivity.java
 *****************************************************************************
 * Copyright © 2011-2014 VLC authors and VideoLAN
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
 **************************************************************************** */

package org.videolan.vlc.gui.video;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.KeyguardManager;
import android.app.Presentation;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.databinding.DataBindingUtil;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.media.MediaRouter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.timursoft.izya.BuildConfig;
import com.timursoft.izya.R;
import com.timursoft.izya.SubsViewModel;
import com.timursoft.izya.databinding.SubsBinding;
import com.timursoft.suber.Sub;

import org.proninyaroslav.libretorrent.core.Torrent;
import org.proninyaroslav.libretorrent.core.TorrentTaskServiceIPC;
import org.proninyaroslav.libretorrent.core.stateparcel.TorrentStateParcel;
import org.proninyaroslav.libretorrent.TorrentTaskService;
import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.PlaybackServiceActivity;
import org.videolan.vlc.gui.audio.PlaylistAdapter;
import org.videolan.vlc.gui.browser.FilePickerActivity;
import org.videolan.vlc.gui.dialogs.AdvOptionsDialog;
import org.videolan.vlc.gui.helpers.OnRepeatListener;
import org.videolan.vlc.gui.helpers.SwipeDragItemTouchHelperCallback;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.preferences.PreferencesActivity;
import org.videolan.vlc.interfaces.IPlaybackSettingsController;
import org.videolan.vlc.media.MediaDatabase;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.media.MediaWrapper;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.FileUtils;
import org.videolan.vlc.util.Permissions;
import org.videolan.vlc.util.Strings;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.VLCInstance;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

import static com.timursoft.suber.Suber.suber;

public class VideoPlayerActivity extends AppCompatActivity implements IVLCVout.Callback,
        GestureDetector.OnDoubleTapListener, IPlaybackSettingsController,
        PlaybackService.Client.Callback, PlaybackService.Callback, PlaylistAdapter.IPlayer, OnClickListener, View.OnLongClickListener {

    public final static String TAG = "VLC/VideoPlayerActivity";

    // Internal intent identifier to distinguish between internal launch and
    // external intent.
    public final static String PLAY_FROM_VIDEOGRID = Strings.buildPkgString("gui.video.PLAY_FROM_VIDEOGRID");
    public final static String PLAY_FROM_SERVICE = Strings.buildPkgString("gui.video.PLAY_FROM_SERVICE");
    public final static String PLAY_FROM_TORRENT = Strings.buildPkgString("gui.video.PLAY_FROM_TORRENT");
    public final static String EXIT_PLAYER = Strings.buildPkgString("gui.video.EXIT_PLAYER");

    public final static String PLAY_EXTRA_ITEM_LOCATION = "item_location";
    public final static String PLAY_EXTRA_SUBTITLES_LOCATION = "subtitles_location";
    public final static String PLAY_EXTRA_ITEM_TITLE = "title";
    public final static String PLAY_EXTRA_FROM_START = "from_start";
    public final static String PLAY_EXTRA_START_TIME = "position";
    public final static String PLAY_EXTRA_OPENED_POSITION = "opened_position";
    public final static String PLAY_EXTRA_TORRENT = "torrent";
    public final static String PLAY_DISABLE_HARDWARE = "disable_hardware";

    public final static String ACTION_RESULT = Strings.buildPkgString("player.result");
    public final static String EXTRA_POSITION = "extra_position";
    public final static String EXTRA_DURATION = "extra_duration";
    public final static int RESULT_CONNECTION_FAILED = RESULT_FIRST_USER + 1;
    public final static int RESULT_PLAYBACK_ERROR = RESULT_FIRST_USER + 2;
    public final static int RESULT_VIDEO_TRACK_LOST = RESULT_FIRST_USER + 4;

    public static final int SUB_TIME_OFFSET = 80;
    public static final int SUB_TIME_REMOVE_OFFSET = 200;
    public static final Pattern SUB_FILE_PATTERN = Pattern.compile(".*\\.(srt|ass|ssa)$");
    public static final Pattern SUB_FILE_PATH_PATTERN = Pattern.compile("^(.*)/([^/]*)\\.[^\\./]*$");

    private final PlaybackServiceActivity.Helper mHelper = new PlaybackServiceActivity.Helper(this, this);
    private PlaybackService mService;
    private SurfaceView mSurfaceView = null;
    private View mRootView;
    private FrameLayout mSurfaceFrame;
    private MediaRouter mMediaRouter;
    private MediaRouter.SimpleCallback mMediaRouterCallback;
    private SecondaryDisplay mPresentation;
    private int mPresentationDisplayId = -1;
    private Uri mUri;
    private boolean mAskResume = true;
    private GestureDetectorCompat mDetector = null;

    private ImageView mPlaylistToggle;
    private RecyclerView mPlaylist;
    private PlaylistAdapter mPlaylistAdapter;
    private ImageView mPlaylistNext;
    private ImageView mPlaylistPrevious;

    private static final int SURFACE_BEST_FIT = 0;
    private static final int SURFACE_FIT_HORIZONTAL = 1;
    private static final int SURFACE_FIT_VERTICAL = 2;
    private static final int SURFACE_FILL = 3;
    private static final int SURFACE_16_9 = 4;
    private static final int SURFACE_4_3 = 5;
    private static final int SURFACE_ORIGINAL = 6;
    private int mCurrentSize = SURFACE_BEST_FIT;

    private SharedPreferences mSettings;
    private int mTouchControls = 0;

    /**
     * Overlay
     */
    private ActionBar mActionBar;
    private ViewGroup mActionBarView;
    private View mOverlayProgress;
    private View mOverlayBackground;
    private View mOverlayButtons;
    private static final int OVERLAY_TIMEOUT = 4000;
    private static final int OVERLAY_INFINITE = -1;
    private static final int FADE_OUT = 1;
    private static final int SHOW_PROGRESS = 2;
    private static final int FADE_OUT_INFO = 3;
    private static final int START_PLAYBACK = 4;
    private static final int AUDIO_SERVICE_CONNECTION_FAILED = 5;
    private static final int RESET_BACK_LOCK = 6;
    private static final int CHECK_VIDEO_TRACKS = 7;
    private static final int LOADING_ANIMATION = 8;
    private static final int HW_ERROR = 1000; // TODO REMOVE

    private static final int LOADING_ANIMATION_DELAY = 1000;

    private boolean mDragging;
    private boolean mShowing;
    private DelayState mPlaybackSetting = DelayState.OFF;
    private SeekBar mSeekbar;
    private TextView mTitle;
    private TextView mTime;
    private TextView mLength;
    private TextView mInfo;
    private View mOverlayInfo;
    private View mVerticalBar;
    private View mVerticalBarProgress;
    private boolean mIsLoading;
    private boolean mIsPlaying = false;
    private ImageView mLoading;
    private ImageView mTipsBackground;
    private ImageView mPlayPause;
    private ImageView mTracks;
    private ImageView mNavMenu;
    private ImageView mRewind;
    private ImageView mForward;
    private ImageView mAdvOptions;
    private ImageView mPlaybackSettingPlus;
    private ImageView mPlaybackSettingMinus;
    private View mObjectFocused;
    private boolean mEnableCloneMode;
    private boolean mDisplayRemainingTime;
    private int mScreenOrientation;
    private int mScreenOrientationLock;
    private ImageView mLock;
    private ImageView mSize;
    private String KEY_REMAINING_TIME_DISPLAY = "remaining_time_display";
    private String KEY_BLUETOOTH_DELAY = "key_bluetooth_delay";

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return super.onGenericMotionEvent(event);
    }

    private boolean mIsLocked = false;
    /* -1 is a valid track (Disable) */
    private int mLastAudioTrack = -2;
    private int mLastSpuTrack = -2;
    private int mOverlayTimeout = 0;
    private boolean mLockBackButton = false;
    boolean mWasPaused = false;

    /**
     * For uninterrupted switching between audio and video mode
     */
    private boolean mSwitchingView;
    private boolean mSwitchToPopup;
    private boolean mHardwareAccelerationError;
    private boolean mHasSubItems = false;

    // size of the video
    private int mVideoHeight;
    private int mVideoWidth;
    private int mVideoVisibleHeight;
    private int mVideoVisibleWidth;
    private int mSarNum;
    private int mSarDen;

    //Volume
    private AudioManager mAudioManager;
    private int mAudioMax;
    private boolean mMute = false;
    private int mVolSave;
    private float mVol;

    //Touch Events
    private static final int TOUCH_NONE = 0;
    private static final int TOUCH_VOLUME = 1;
    private static final int TOUCH_BRIGHTNESS = 2;
    private static final int TOUCH_SEEK = 3;
    private int mTouchAction = TOUCH_NONE;
    private int mSurfaceYDisplayRange;
    private float mInitTouchY, mTouchY = -1f, mTouchX = -1f;

    //stick event
    private static final int JOYSTICK_INPUT_DELAY = 300;
    private long mLastMove;

    // Brightness
    private boolean mIsFirstBrightnessGesture = true;
    private float mRestoreAutoBrightness = -1f;

    // Tracks & Subtitles
    private MediaPlayer.TrackDescription[] mAudioTracksList;
    private MediaPlayer.TrackDescription[] mSubtitleTracksList;
    /**
     * Used to store a selected subtitle; see onActivityResult.
     * It is possible to have multiple custom subs in one session
     * (just like desktop VLC allows you as well.)
     */
    private volatile ArrayList<String> mSubtitleSelectedFiles = new ArrayList<>();

    /**
     * Flag to indicate whether the media should be paused once loaded
     * (e.g. lock screen, or to restore the pause state)
     */
    private boolean mPlaybackStarted = false;

    // Tips
    private View mOverlayTips;
    private static final String PREF_TIPS_SHOWN = "video_player_tips_shown";

    // Navigation handling (DVD, Blu-Ray...)
    private int mMenuIdx = -1;
    private boolean mIsNavMenu = false;

    /* for getTime and seek */
    private long mForcedTime = -1;
    private long mLastTime = -1;

    private OnLayoutChangeListener mOnLayoutChangeListener;
    private AlertDialog mAlertDialog;

    private SubsBinding binding;
    private List<Sub> subs = new ArrayList<>();
    private PublishSubject<Integer> publishSubject = PublishSubject.create();
    private Subscription subChanger = null;
    private Subscription subRemover = null;
    private int lastPlayedPosition = 0;

    /* Messenger for communicating with the torrent service. */
    private Messenger torrentServiceCallback = null;
    private Messenger torrentClientCallback = new Messenger(new TorrentCallbackHandler());
    private TorrentTaskServiceIPC torrentIpc = new TorrentTaskServiceIPC();
    private TorrentServiceConnection torrentConnection = new TorrentServiceConnection();
    private HashSet<Torrent> torrentsQueue = new HashSet<>();

    @SuppressWarnings({"ResourceType", "deprecation"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!VLCInstance.testCompatibleCPU(this)) {
            exit(RESULT_CANCELED);
            return;
        }

        // Get the media router service (Miracast)
        mMediaRouter = (MediaRouter) VLCApplication.getAppContext().getSystemService(Context.MEDIA_ROUTER_SERVICE);
        mMediaRouterCallback = new MediaRouter.SimpleCallback() {
            @Override
            public void onRoutePresentationDisplayChanged(
                    MediaRouter router, MediaRouter.RouteInfo info) {
                Log.d(TAG, "onRoutePresentationDisplayChanged: info=" + info);
                final Display presentationDisplay = info.getPresentationDisplay();
                final int newDisplayId = presentationDisplay != null ? presentationDisplay.getDisplayId() : -1;
                if (newDisplayId != mPresentationDisplayId)
                    removePresentation();
            }
        };
        Log.d(TAG, "MediaRouter information : " + mMediaRouter.toString());

        mSettings = PreferenceManager.getDefaultSharedPreferences(this);

        mTouchControls = (mSettings.getBoolean("enable_volume_gesture", true) ? 1 : 0)
                + (mSettings.getBoolean("enable_brightness_gesture", true) ? 2 : 0);

        /* Services and miscellaneous */
        mAudioManager = (AudioManager) VLCApplication.getAppContext().getSystemService(AUDIO_SERVICE);
        mAudioMax = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        mEnableCloneMode = mSettings.getBoolean("enable_clone_mode", false);
        createPresentation();
        setContentView(mPresentation == null ? R.layout.player : R.layout.player_remote_control);

        /* initialize Views an their Events */
        mActionBar = getSupportActionBar();
        assert mActionBar != null;
        mActionBar.setDisplayShowHomeEnabled(false);
        mActionBar.setDisplayShowTitleEnabled(false);
        mActionBar.setBackgroundDrawable(null);
        mActionBar.setDisplayShowCustomEnabled(true);
        mActionBar.setCustomView(R.layout.player_action_bar);

        mRootView = findViewById(R.id.player_root);
        mActionBarView = (ViewGroup) mActionBar.getCustomView();

        mTitle = (TextView) mActionBarView.findViewById(R.id.player_overlay_title);

        mPlaylistToggle = (ImageView) findViewById(R.id.playlist_toggle);
        mPlaylist = (RecyclerView) findViewById(R.id.video_playlist);

        mOverlayProgress = findViewById(R.id.progress_overlay);
        RelativeLayout.LayoutParams layoutParams =
                (RelativeLayout.LayoutParams) mOverlayProgress.getLayoutParams();
        if (AndroidDevices.isPhone() || !AndroidDevices.hasNavBar()) {
            layoutParams.width = LayoutParams.MATCH_PARENT;
        } else {
            layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
        }
        mOverlayProgress.setLayoutParams(layoutParams);
        mOverlayBackground = findViewById(R.id.player_overlay_background);
        mOverlayButtons = findViewById(R.id.player_overlay_buttons);

        // Position and remaining time
        mTime = (TextView) findViewById(R.id.player_overlay_time);
        mLength = (TextView) findViewById(R.id.player_overlay_length);

        // the info textView is not on the overlay
        mInfo = (TextView) findViewById(R.id.player_overlay_textinfo);
        mOverlayInfo = findViewById(R.id.player_overlay_info);
        mVerticalBar = findViewById(R.id.verticalbar);
        mVerticalBarProgress = findViewById(R.id.verticalbar_progress);

        mScreenOrientation = Integer.valueOf(
                mSettings.getString("screen_orientation_value", "99" /*SCREEN ORIENTATION SENSOR*/));

        mPlayPause = (ImageView) findViewById(R.id.player_overlay_play);

        mTracks = (ImageView) findViewById(R.id.player_overlay_tracks);
        mTracks.setOnClickListener(this);
        mAdvOptions = (ImageView) findViewById(R.id.player_overlay_adv_function);
        mAdvOptions.setOnClickListener(this);
        mLock = (ImageView) findViewById(R.id.lock_overlay_button);

        mSize = (ImageView) findViewById(R.id.player_overlay_size);

        mNavMenu = (ImageView) findViewById(R.id.player_overlay_navmenu);

        if (mSettings.getBoolean("enable_seek_buttons", false))
            initSeekButton();

        mPlaybackSettingPlus = (ImageView) findViewById(R.id.player_delay_plus);
        mPlaybackSettingMinus = (ImageView) findViewById(R.id.player_delay_minus);

        mSurfaceView = (SurfaceView) findViewById(R.id.player_surface);

        mSurfaceFrame = (FrameLayout) findViewById(R.id.player_surface_frame);

        mSeekbar = (SeekBar) findViewById(R.id.player_overlay_seekbar);

        /* Loading view */
        mLoading = (ImageView) findViewById(R.id.player_overlay_loading);
        if (mPresentation != null)
            mTipsBackground = (ImageView) findViewById(R.id.player_remote_tips_background);
        dimStatusBar(true);
        mHandler.sendEmptyMessageDelayed(LOADING_ANIMATION, LOADING_ANIMATION_DELAY);

        mSwitchingView = false;
        mHardwareAccelerationError = false;

        mAskResume = mSettings.getBoolean("dialog_confirm_resume", false);
        mDisplayRemainingTime = mSettings.getBoolean(KEY_REMAINING_TIME_DISPLAY, false);
        // Clear the resume time, since it is only used for resumes in external
        // videos.
        Editor editor = mSettings.edit();
        editor.putLong(PreferencesActivity.VIDEO_RESUME_TIME, -1);
        // Also clear the subs list, because it is supposed to be per session
        // only (like desktop VLC). We don't want the custom subtitle files
        // to persist forever with this video.
        editor.putString(PreferencesActivity.VIDEO_SUBTITLE_FILES, null);
        // Paused flag - per session too, like the subs list.
        editor.remove(PreferencesActivity.VIDEO_PAUSED);
        Util.commitPreferences(editor);

        IntentFilter filter = new IntentFilter();
        filter.addAction(VLCApplication.SLEEP_INTENT);
        registerReceiver(mReceiver, filter);

        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // 100 is the value for screen_orientation_start_lock
        setRequestedOrientation(getScreenOrientation(mScreenOrientation));
        // Extra initialization when no secondary display is detected
        if (mPresentation == null) {
            // Orientation
            // Tips
            mOverlayTips = findViewById(R.id.player_overlay_tips);
            if (BuildConfig.DEBUG || mSettings.getBoolean(PREF_TIPS_SHOWN, false))
                mOverlayTips.setVisibility(View.GONE);
            else {
                mOverlayTips.bringToFront();
                mOverlayTips.invalidate();
            }
        }

        resetHudLayout();

        ViewGroup container = (ViewGroup) findViewById(R.id.player_ui_container);
        binding = DataBindingUtil.inflate(getLayoutInflater(), R.layout.subs, container, false);
        container.addView(binding.getRoot(), 0);
        binding.setViewModel(new SubsViewModel());

        DividerItemDecoration divider = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        divider.setDrawable(new ColorDrawable(getResources().getColor(R.color.divider)));
        binding.recycler.addItemDecoration(divider);
    }

    @Override
    protected void onResume() {
        super.onResume();

        /*
         * Set listeners here to avoid NPE when activity is closing
         */
        mSeekbar.setOnSeekBarChangeListener(mSeekListener);
        mLock.setOnClickListener(this);
        mPlayPause.setOnClickListener(this);
        mPlayPause.setOnLongClickListener(this);
        mLength.setOnClickListener(this);
        mTime.setOnClickListener(this);
        mSize.setOnClickListener(this);
        mNavMenu.setOnClickListener(this);

        if (mIsLocked && mScreenOrientation == 99)
            setRequestedOrientation(mScreenOrientationLock);

        publishSubject.observeOn(AndroidSchedulers.mainThread())
                .subscribe(i -> {
                    if (subRemover != null && !subRemover.isUnsubscribed()) {
                        subRemover.unsubscribe();
                    }
                    if (i < 0 || i >= subs.size()) {
                        binding.subText.setVisibility(View.GONE);
                        return;
                    }

                    int delay = subs.get(i).endTime - (int) getTime() + SUB_TIME_REMOVE_OFFSET;
                    if (delay > 0) {
                        changeSub(i);
                        subRemover = Observable.timer(delay, TimeUnit.MILLISECONDS)
                                .filter(d -> mService.isPlaying())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(d -> binding.subText.setVisibility(View.GONE));
                    } else {
                        binding.subText.setVisibility(View.GONE);
                    }
                });
    }

    private void changeSub(int i) {
        lastPlayedPosition = i;

        String text = subs.get(i).content.trim();
        SpannableStringBuilder ssb = new SpannableStringBuilder(Html.fromHtml(text));
        String clearText = Html.fromHtml(text).toString();
        BreakIterator iterator = BreakIterator.getWordInstance(Locale.ENGLISH);
        iterator.setText(clearText);
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String possibleWord = clearText.substring(start, end);
            if (Character.isLetterOrDigit(possibleWord.charAt(0))) {
                ClickableSpan clickSpan = new ClickableWordSpan(possibleWord);
                ssb.setSpan(clickSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        binding.subText.setMovementMethod(LinkMovementMethod.getInstance());
        binding.subText.setText(ssb, TextView.BufferType.SPANNABLE);
        binding.subText.setVisibility(View.VISIBLE);
    }

    private class ClickableWordSpan extends ClickableSpan {
        private final String word;

        private ClickableWordSpan(String word) {
            this.word = word;
        }

        @Override
        public void onClick(View widget) {
            pause();
            VLCApplication.getAppComponent().httpService().getTranslates(word)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(translations -> {
                        binding.getViewModel().translations = translations;
                        binding.invalidateAll();
                    }, Throwable::printStackTrace);
        }

        @Override
        public void updateDrawState(TextPaint ds) {
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case Permissions.PERMISSION_STORAGE_TAG: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    loadMedia();
                } else {
                    Permissions.showStoragePermissionDialog(this, true);
                }
            }
            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    @SuppressLint("SdCardPath")
    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        if (mPlaybackStarted && mService.getCurrentMediaWrapper() != null) {
            Uri uri = intent.hasExtra(PLAY_EXTRA_ITEM_LOCATION) ?
                    (Uri) intent.getExtras().getParcelable(PLAY_EXTRA_ITEM_LOCATION) : intent.getData();
            if (uri == null || uri.equals(mUri))
                return;
            if (TextUtils.equals("file", uri.getScheme()) && uri.getPath().startsWith("/sdcard")) {
                Uri convertedUri = FileUtils.convertLocalUri(uri);
                if (convertedUri == null || convertedUri.equals(mUri))
                    return;
                else
                    uri = convertedUri;
            }
            mUri = uri;
            updateSeekable(mService.isSeekable());
            updatePausable(mService.isPausable());
            mTitle.setText(mService.getCurrentMediaWrapper().getTitle());
            if (mPlaylist.getVisibility() == View.VISIBLE) {
                mPlaylistAdapter.setCurrentIndex(mService.getCurrentMediaPosition());
                mPlaylist.setVisibility(View.GONE);
            }
            showTitle();
            initUI();
            mForcedTime = mLastTime = -1;
            setOverlayProgress();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        hideOverlay(true);
        mSeekbar.setOnSeekBarChangeListener(null);
        mLock.setOnClickListener(null);
        mPlayPause.setOnClickListener(null);
        mPlayPause.setOnLongClickListener(null);
        mLength.setOnClickListener(null);
        mTime.setOnClickListener(null);
        mSize.setOnClickListener(null);

        /* Stop the earliest possible to avoid vout error */
        if (isFinishing())
            stopPlayback();

        if (subChanger != null && !subChanger.isUnsubscribed()) {
            subChanger.unsubscribe();
        }
        if (subRemover != null && !subRemover.isUnsubscribed()) {
            subRemover.unsubscribe();
        }
    }

    @Override
    public void onVisibleBehindCanceled() {
        super.onVisibleBehindCanceled();
        stopPlayback();
        exitOK();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        resetHudLayout();
    }

    public void resetHudLayout() {
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) mOverlayButtons.getLayoutParams();
        int orientation = getScreenOrientation(100);
        boolean portrait = orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT ||
                orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
        if (portrait) {
            layoutParams.addRule(RelativeLayout.BELOW, R.id.player_overlay_length);
            layoutParams.addRule(RelativeLayout.RIGHT_OF, 0);
            layoutParams.addRule(RelativeLayout.LEFT_OF, 0);
        } else {
            layoutParams.addRule(RelativeLayout.BELOW, R.id.player_overlay_seekbar);
            layoutParams.addRule(RelativeLayout.RIGHT_OF, R.id.player_overlay_time);
            layoutParams.addRule(RelativeLayout.LEFT_OF, R.id.player_overlay_length);
        }
        mOverlayButtons.setLayoutParams(layoutParams);
    }


    @Override
    protected void onStart() {
        super.onStart();
        mHelper.onStart();
        if (mSettings.getBoolean("save_brightness", false)) {
            float brightness = mSettings.getFloat("brightness_value", -1f);
            if (brightness != -1f)
                setWindowBrightness(brightness);
        }
        IntentFilter filter = new IntentFilter(PLAY_FROM_SERVICE);
        filter.addAction(EXIT_PLAYER);
        LocalBroadcastManager.getInstance(this).registerReceiver(
                mServiceReceiver, filter);
        if (mBtReceiver != null) {
            IntentFilter btFilter = new IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
            btFilter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
            registerReceiver(mBtReceiver, btFilter);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mServiceReceiver);

        if (mBtReceiver != null)
            unregisterReceiver(mBtReceiver);
        if (mAlertDialog != null && mAlertDialog.isShowing())
            mAlertDialog.dismiss();
        if (!isFinishing() && mService != null && mService.isPlaying() &&
                mSettings.getBoolean(PreferencesActivity.VIDEO_BACKGROUND, false)) {
            switchToAudioMode(false);
        }
        stopPlayback();


        restoreBrightness();
        if (mService != null)
            mService.removeCallback(this);
        mHelper.onStop();
    }

    private void restoreBrightness() {
        if (mRestoreAutoBrightness != -1f) {
            int brightness = (int) (mRestoreAutoBrightness * 255f);
            Settings.System.putInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS,
                    brightness);
            Settings.System.putInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
        }
        // Save brightness if user wants to
        if (mSettings.getBoolean("save_brightness", false)) {
            float brightness = getWindow().getAttributes().screenBrightness;
            if (brightness != -1f) {
                Editor editor = mSettings.edit();
                editor.putFloat("brightness_value", brightness);
                Util.commitPreferences(editor);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);

        // Dismiss the presentation when the activity is not visible.
        if (mPresentation != null) {
            Log.i(TAG, "Dismissing presentation because the activity is no longer visible.");
            mPresentation.dismiss();
            mPresentation = null;
        }

        mAudioManager = null;

        if (torrentConnection.bound) {
            try {
                torrentIpc.sendClientDisconnect(torrentServiceCallback, torrentClientCallback);
            } catch (RemoteException e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
            unbindService(torrentConnection);
            torrentConnection.bound = false;
        }
    }

    /**
     * Add or remove MediaRouter callbacks. This is provided for version targeting.
     *
     * @param add true to add, false to remove
     */
    private void mediaRouterAddCallback(boolean add) {
        if (mMediaRouter == null) return;

        if (add)
            mMediaRouter.addCallback(MediaRouter.ROUTE_TYPE_LIVE_VIDEO, mMediaRouterCallback);
        else
            mMediaRouter.removeCallback(mMediaRouterCallback);
    }

    private void startPlayback() {
        /* start playback only when audio service and both surfaces are ready */
        if (mPlaybackStarted || mService == null)
            return;

        mPlaybackStarted = true;

        final IVLCVout vlcVout = mService.getVLCVout();
        if (vlcVout.areViewsAttached() && mService.isPlayingPopup())
            mService.stopPlayback();
        vlcVout.detachViews();
        if (mPresentation == null) {
            vlcVout.setVideoView(mSurfaceView);
//            if (mSubtitlesSurfaceView.getVisibility() != View.GONE)
//                vlcVout.setSubtitlesView(mSubtitlesSurfaceView);
        } else {
            vlcVout.setVideoView(mPresentation.mSurfaceView);
//            if (mSubtitlesSurfaceView.getVisibility() != View.GONE)
//                vlcVout.setSubtitlesView(mPresentation.mSubtitlesSurfaceView);
        }
        vlcVout.addCallback(this);
        vlcVout.attachViews();
        mService.setVideoTrackEnabled(true);

        initUI();

        loadMedia();

        boolean ratePref = mSettings.getBoolean(PreferencesActivity.KEY_AUDIO_PLAYBACK_SPEED_PERSIST, false);
        mService.setRate(ratePref ? mSettings.getFloat(PreferencesActivity.VIDEO_RATE, 1.0f) : 1.0F, false);

        if (mBtReceiver != null && (mAudioManager.isBluetoothA2dpOn() || mAudioManager.isBluetoothScoOn()))
            toggleBtDelay(true);

        if (mService.hasPlaylist()) {
            mPlaylistPrevious = (ImageView) findViewById(R.id.playlist_previous);
            mPlaylistNext = (ImageView) findViewById(R.id.playlist_next);
            mPlaylistAdapter = new PlaylistAdapter(this);
            mPlaylistAdapter.setService(mService);
            final LinearLayoutManager layoutManager = new LinearLayoutManager(this);
            layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
            mPlaylist.setLayoutManager(layoutManager);
            mPlaylistToggle.setVisibility(View.VISIBLE);
            mPlaylistPrevious.setVisibility(View.VISIBLE);
            mPlaylistNext.setVisibility(View.VISIBLE);
            mPlaylistToggle.setOnClickListener(VideoPlayerActivity.this);
            mPlaylistPrevious.setOnClickListener(VideoPlayerActivity.this);
            mPlaylistNext.setOnClickListener(VideoPlayerActivity.this);
            mSeekbar.setNextFocusUpId(mPlaylistToggle.getId());

            ItemTouchHelper.Callback callback = new SwipeDragItemTouchHelperCallback(mPlaylistAdapter);
            ItemTouchHelper touchHelper = new ItemTouchHelper(callback);
            touchHelper.attachToRecyclerView(mPlaylist);
            if (TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL) {
                mPlaylistPrevious.setImageResource(R.drawable.ic_playlist_next_circle);
                mPlaylistNext.setImageResource(R.drawable.ic_playlist_previous_circle);
            }
        }

    }

    private void initUI() {

        cleanUI();

        /* Dispatch ActionBar touch events to the Activity */
        mActionBarView.setOnTouchListener((v, event) -> {
            onTouchEvent(event);
            return true;
        });

        if (mOnLayoutChangeListener == null) {
            mOnLayoutChangeListener = new OnLayoutChangeListener() {
                private final Runnable mRunnable = () -> changeSurfaceLayout();

                @Override
                public void onLayoutChange(View v, int left, int top, int right,
                                           int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                        /* changeSurfaceLayout need to be called after the layout changed */
                        mHandler.removeCallbacks(mRunnable);
                        mHandler.post(mRunnable);
                    }
                }
            };
        }
        mSurfaceFrame.addOnLayoutChangeListener(mOnLayoutChangeListener);
        changeSurfaceLayout();

        /* Listen for changes to media routes. */
        if (mMediaRouter != null)
            mediaRouterAddCallback(true);

        if (mRootView != null)
            mRootView.setKeepScreenOn(true);
    }

    private void stopPlayback() {
        if (!mPlaybackStarted)
            return;

        mWasPaused = !mService.isPlaying();

        if (mMute)
            mute(false);

        mPlaybackStarted = false;

        mService.setVideoTrackEnabled(false);
        mService.removeCallback(this);

        mHandler.removeCallbacksAndMessages(null);
        final IVLCVout vlcVout = mService.getVLCVout();
        vlcVout.removeCallback(this);
        vlcVout.detachViews();

        if (mSwitchingView && mService != null) {
            Log.d(TAG, "mLocation = \"" + mUri + "\"");
            if (mSwitchToPopup)
                mService.switchToPopup(mService.getCurrentMediaPosition());
            else
                mService.showWithoutParse(mService.getCurrentMediaPosition());
            return;
        }

        cleanUI();

        long time = getTime();
        long length = mService.getLength();
        //remove saved position if in the last 5 seconds
        if (length - time < 5000)
            time = 0;
        else
            time -= 2000; // go back 2 seconds, to compensate loading time
        if (isFinishing())
            mService.stop();
        else
            mService.pause();

        Editor editor = mSettings.edit();
        // Save position
        if (mService.isSeekable()) {
            if (MediaDatabase.getInstance().mediaItemExists(mUri)) {
                MediaDatabase.getInstance().updateMedia(
                        mUri,
                        MediaDatabase.INDEX_MEDIA_TIME,
                        time);
            } else {
                // Video file not in media library, store time just for onResume()
                editor.putLong(PreferencesActivity.VIDEO_RESUME_TIME, time);
            }
        }
        // Save selected subtitles
        String subtitleList_serialized = null;
        if (mSubtitleSelectedFiles.size() > 0) {
            Log.d(TAG, "Saving selected subtitle files");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                oos.writeObject(mSubtitleSelectedFiles);
                subtitleList_serialized = bos.toString();
            } catch (IOException ignored) {
            }
        }
        editor.putString(PreferencesActivity.VIDEO_SUBTITLE_FILES, subtitleList_serialized);

        boolean ratePref = mSettings.getBoolean(PreferencesActivity.KEY_AUDIO_PLAYBACK_SPEED_PERSIST, false);
        if (ratePref)
            editor.putFloat(PreferencesActivity.VIDEO_RATE, mService.getRate());
        mService.setRate(1.0f, false);
        Util.commitPreferences(editor);
    }

    private void cleanUI() {

        if (mRootView != null)
            mRootView.setKeepScreenOn(false);

        if (mDetector != null) {
            mDetector.setOnDoubleTapListener(null);
            mDetector = null;
        }

        /* Stop listening for changes to media routes. */
        if (mMediaRouter != null)
            mediaRouterAddCallback(false);

        if (mSurfaceFrame != null && mOnLayoutChangeListener != null)
            mSurfaceFrame.removeOnLayoutChangeListener(mOnLayoutChangeListener);

        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(null);

        mActionBarView.setOnTouchListener(null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        if (data == null) return;

        if (data.getData() == null)
            Log.d(TAG, "Subtitle selection dialog was cancelled");
        else {
            mService.addSubtitleTrack(data.getData(), true);
            VLCApplication.runBackground(() -> MediaDatabase.getInstance().saveSlave(
                    mService.getCurrentMediaLocation(), Media.Slave.Type.Subtitle, 2, data.getDataString()));
        }
    }

    public static void start(Context context, Torrent torrent) {
//        addTorrentsRequest(Collections.singleton(torrent));
        Intent intent = new Intent(context, VideoPlayerActivity.class);
        intent.setAction(PLAY_FROM_TORRENT);
        intent.putExtra(PLAY_EXTRA_TORRENT, torrent);
        intent.putExtra(PLAY_EXTRA_FROM_START, true);

        context.startActivity(intent);
    }

    public static void start(Context context, Uri uri) {
        start(context, uri, null, false, -1);
    }

    public static void start(Context context, Uri uri, boolean fromStart) {
        start(context, uri, null, fromStart, -1);
    }

    public static void start(Context context, Uri uri, String title) {
        start(context, uri, title, false, -1);
    }

    public static void startOpened(Context context, Uri uri, int openedPosition) {
        start(context, uri, null, false, openedPosition);
    }

    private static void start(Context context, Uri uri, String title, boolean fromStart, int openedPosition) {
        Intent intent = getIntent(context, uri, title, fromStart, openedPosition);
        context.startActivity(intent);
    }

    public static Intent getIntent(String action, MediaWrapper mw, boolean fromStart, int openedPosition) {
        return getIntent(action, VLCApplication.getAppContext(), mw.getUri(), mw.getTitle(), fromStart, openedPosition);
    }

    @NonNull
    public static Intent getIntent(Context context, Uri uri, String title, boolean fromStart, int openedPosition) {
        return getIntent(PLAY_FROM_VIDEOGRID, context, uri, title, fromStart, openedPosition);
    }

    @NonNull
    public static Intent getIntent(String action, Context context, Uri uri, String title, boolean fromStart, int openedPosition) {
        Intent intent = new Intent(context, VideoPlayerActivity.class);
        intent.setAction(action);
        intent.putExtra(PLAY_EXTRA_ITEM_LOCATION, uri);
        intent.putExtra(PLAY_EXTRA_ITEM_TITLE, title);
        intent.putExtra(PLAY_EXTRA_FROM_START, fromStart);

        if (openedPosition != -1 || !(context instanceof Activity)) {
            if (openedPosition != -1)
                intent.putExtra(PLAY_EXTRA_OPENED_POSITION, openedPosition);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return intent;
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equalsIgnoreCase(VLCApplication.SLEEP_INTENT)) {
                exitOK();
            }
        }
    };

    private void exit(int resultCode) {
        if (isFinishing())
            return;
        Intent resultIntent = new Intent(ACTION_RESULT);
        if (mUri != null && mService != null) {
//            resultIntent.setData(mUri);
            resultIntent.putExtra(EXTRA_POSITION, mService.getTime());
            resultIntent.putExtra(EXTRA_DURATION, mService.getLength());
        }
        setResult(resultCode, resultIntent);
        finish();
    }

    private void exitOK() {
        exit(RESULT_OK);
    }

    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        if (mIsLoading)
            return false;
        showOverlay();
        return true;
    }

    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (mIsLoading)
            return false;
        //Check for a joystick event
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK ||
                event.getAction() != MotionEvent.ACTION_MOVE)
            return false;

        InputDevice mInputDevice = event.getDevice();

        float dpadx = event.getAxisValue(MotionEvent.AXIS_HAT_X);
        float dpady = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
        if (mInputDevice == null || Math.abs(dpadx) == 1.0f || Math.abs(dpady) == 1.0f)
            return false;

        float x = AndroidDevices.getCenteredAxis(event, mInputDevice, MotionEvent.AXIS_X);
        float y = AndroidDevices.getCenteredAxis(event, mInputDevice, MotionEvent.AXIS_Y);
        float rz = AndroidDevices.getCenteredAxis(event, mInputDevice, MotionEvent.AXIS_RZ);

        if (System.currentTimeMillis() - mLastMove > JOYSTICK_INPUT_DELAY) {
            if (Math.abs(x) > 0.3) {
                seekDelta(x > 0.0f ? 10000 : -10000);
            } else if (Math.abs(y) > 0.3) {
                if (mIsFirstBrightnessGesture)
                    initBrightnessTouch();
                changeBrightness(-y / 10f);
            } else if (Math.abs(rz) > 0.3) {
                mVol = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                int delta = -(int) ((rz / 7) * mAudioMax);
                int vol = (int) Math.min(Math.max(mVol + delta, 0), mAudioMax);
                setAudioVolume(vol);
            }
            mLastMove = System.currentTimeMillis();
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (mLockBackButton) {
            mLockBackButton = false;
            mHandler.sendEmptyMessageDelayed(RESET_BACK_LOCK, 2000);
            Toast.makeText(VLCApplication.getAppContext(), getString(R.string.back_quit_lock), Toast.LENGTH_SHORT).show();
        } else if (mPlaylist.getVisibility() == View.VISIBLE) {
            togglePlaylist();
        } else if (mPlaybackSetting != DelayState.OFF) {
            endPlaybackSetting();
        } else {
            exitOK();
            super.onBackPressed();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B)
            return super.onKeyDown(keyCode, event);
        if (mPlaybackSetting != DelayState.OFF)
            return false;
        if (mIsLoading) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_S:
                case KeyEvent.KEYCODE_MEDIA_STOP:
                    exitOK();
                    return true;
            }
            return false;
        }
        //Handle playlist d-pad navigation
        if (mPlaylist.hasFocus()) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    mPlaylistAdapter.setCurrentIndex(mPlaylistAdapter.getCurrentIndex() - 1);
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    mPlaylistAdapter.setCurrentIndex(mPlaylistAdapter.getCurrentIndex() + 1);
                    break;
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_BUTTON_A:
                    mService.playIndex(mPlaylistAdapter.getCurrentIndex());
                    break;
            }
            return true;
        }
        if (mShowing || keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
            showOverlayTimeout(OVERLAY_TIMEOUT);
        switch (keyCode) {
            case KeyEvent.KEYCODE_F:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                seekDelta(10000);
                return true;
            case KeyEvent.KEYCODE_R:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                seekDelta(-10000);
                return true;
            case KeyEvent.KEYCODE_BUTTON_R1:
                seekDelta(60000);
                return true;
            case KeyEvent.KEYCODE_BUTTON_L1:
                seekDelta(-60000);
                return true;
            case KeyEvent.KEYCODE_BUTTON_A:
                if (mOverlayProgress.getVisibility() == View.VISIBLE)
                    return false;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_SPACE:
                if (mIsNavMenu)
                    return navigateDvdMenu(keyCode);
                else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) //prevent conflict with remote control
                    return super.onKeyDown(keyCode, event);
                else
                    doPlayPause();
                return true;
            case KeyEvent.KEYCODE_O:
            case KeyEvent.KEYCODE_BUTTON_Y:
            case KeyEvent.KEYCODE_MENU:
                showAdvancedOptions();
                return true;
            case KeyEvent.KEYCODE_V:
            case KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK:
            case KeyEvent.KEYCODE_BUTTON_X:
                onAudioSubClick(mTracks);
                return true;
            case KeyEvent.KEYCODE_N:
                showNavMenu();
                return true;
            case KeyEvent.KEYCODE_A:
                resizeVideo();
                return true;
            case KeyEvent.KEYCODE_M:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                updateMute();
                return true;
            case KeyEvent.KEYCODE_S:
            case KeyEvent.KEYCODE_MEDIA_STOP:
                exitOK();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (!mShowing) {
                    seekDelta(-10000);
                    return true;
                }
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (!mShowing) {
                    seekDelta(10000);
                    return true;
                }
            case KeyEvent.KEYCODE_DPAD_UP:
                if (!mShowing) {
                    showAdvancedOptions();
                    return true;
                }
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (!mShowing) {
                    doPlayPause();
                    return true;
                }
            case KeyEvent.KEYCODE_ENTER:
                if (mIsNavMenu)
                    return navigateDvdMenu(keyCode);
                else
                    return super.onKeyDown(keyCode, event);
            case KeyEvent.KEYCODE_J:
                delayAudio(-50000L);
                return true;
            case KeyEvent.KEYCODE_K:
                delayAudio(50000L);
                return true;
            case KeyEvent.KEYCODE_G:
                delaySubs(-50000L);
                return true;
            case KeyEvent.KEYCODE_H:
                delaySubs(50000L);
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (mMute) {
                    updateMute();
                    return true;
                } else
                    return false;
            case KeyEvent.KEYCODE_CAPTIONS:
                selectSubtitles();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean navigateDvdMenu(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                mService.navigate(MediaPlayer.Navigate.Up);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                mService.navigate(MediaPlayer.Navigate.Down);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                mService.navigate(MediaPlayer.Navigate.Left);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                mService.navigate(MediaPlayer.Navigate.Right);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BUTTON_X:
            case KeyEvent.KEYCODE_BUTTON_A:
                mService.navigate(MediaPlayer.Navigate.Activate);
                return true;
            default:
                return false;
        }
    }

    @Override
    public void showAudioDelaySetting() {
        mPlaybackSetting = DelayState.AUDIO;
        showDelayControls();
    }

    @Override
    public void showSubsDelaySetting() {
        mPlaybackSetting = DelayState.SUBS;
        showDelayControls();
    }

    @Override
    public void showPlaybackSpeedSetting() {
        mPlaybackSetting = DelayState.SPEED;
        showDelayControls();
    }

    public void showDelayControls() {
        mTouchAction = TOUCH_NONE;
        if (mPresentation != null)
            showOverlayTimeout(OVERLAY_INFINITE);
        mPlaybackSettingMinus.setOnClickListener(this);
        mPlaybackSettingPlus.setOnClickListener(this);
        mPlaybackSettingMinus.setOnTouchListener(new OnRepeatListener(this));
        mPlaybackSettingPlus.setOnTouchListener(new OnRepeatListener(this));
        mPlaybackSettingMinus.setVisibility(View.VISIBLE);
        mPlaybackSettingPlus.setVisibility(View.VISIBLE);
        mPlaybackSettingPlus.requestFocus();
        initPlaybackSettingInfo();
    }

    private void initPlaybackSettingInfo() {
        if (mPresentation == null) {
            UiTools.setViewVisibility(mVerticalBar, View.GONE);
            UiTools.setViewVisibility(mOverlayInfo, View.VISIBLE);
        } else
            mInfo.setVisibility(View.VISIBLE);
        String text = "";
        if (mPlaybackSetting == DelayState.AUDIO) {
            text += getString(R.string.audio_delay) + "\n";
            text += mService.getAudioDelay() / 1000L;
            text += " ms";
        } else if (mPlaybackSetting == DelayState.SUBS) {
            text += getString(R.string.spu_delay) + "\n";
            text += mService.getSpuDelay() / 1000L;
            text += " ms";
        } else if (mPlaybackSetting == DelayState.SPEED) {
            text += getString(R.string.playback_speed) + "\n";
            text += mService.getRate();
            text += " x";
        } else
            text += "0";
        mInfo.setText(text);
    }

    @Override
    public void endPlaybackSetting() {
        mTouchAction = TOUCH_NONE;
        if (mBtReceiver != null && mPlaybackSetting == DelayState.AUDIO
                && (mAudioManager.isBluetoothA2dpOn() || mAudioManager.isBluetoothScoOn())) {
            String msg = getString(R.string.audio_delay) + "\n"
                    + mService.getAudioDelay() / 1000L
                    + " ms";
            Snackbar sb = Snackbar.make(mInfo, msg, Snackbar.LENGTH_LONG);
            sb.setAction(R.string.save_bluetooth_delay, mBtSaveListener);
            sb.show();
        }
        mPlaybackSetting = DelayState.OFF;
        mPlaybackSettingMinus.setOnClickListener(null);
        mPlaybackSettingPlus.setOnClickListener(null);
        mPlaybackSettingMinus.setVisibility(View.INVISIBLE);
        mPlaybackSettingPlus.setVisibility(View.INVISIBLE);
        if (mPresentation == null)
            UiTools.setViewVisibility(mOverlayInfo, View.INVISIBLE);
        else
            mInfo.setVisibility(View.INVISIBLE);
        mInfo.setText("");
        mPlayPause.requestFocus();
    }

    public void delayAudio(long delta) {
        long delay = mService.getAudioDelay() + delta;
        mService.setAudioDelay(delay);
        mInfo.setText(getString(R.string.audio_delay) + "\n" + (delay / 1000L) + " ms");
        if (mPlaybackSetting == DelayState.OFF) {
            mPlaybackSetting = DelayState.AUDIO;
            initPlaybackSettingInfo();
        }
    }

    public void delaySubs(long delta) {
        long delay = mService.getSpuDelay() + delta;
        mService.setSpuDelay(delay);
        mInfo.setText(getString(R.string.spu_delay) + "\n" + (delay / 1000L) + " ms");
        if (mPlaybackSetting == DelayState.OFF) {
            mPlaybackSetting = DelayState.SUBS;
            initPlaybackSettingInfo();
        }
    }

    public void changeSpeed(float delta) {
        float rate = Math.round((mService.getRate() + delta) * 100f) / 100f;
        if (rate < 0.25f || rate > 4f)
            return;
        mService.setRate(rate, false);
        mInfo.setText(getString(R.string.playback_speed) + "\n" + rate + " x");
        if (mPlaybackSetting == DelayState.OFF) {
            mPlaybackSetting = DelayState.SPEED;
            initPlaybackSettingInfo();
        }
    }

    /**
     * Lock screen rotation
     */
    @SuppressWarnings("ResourceType")
    private void lockScreen() {
        if (mScreenOrientation != 100) {
            mScreenOrientationLock = getRequestedOrientation();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
            else
                setRequestedOrientation(getScreenOrientation(100));
        }
        showInfo(R.string.locked, 1000);
        mLock.setImageResource(R.drawable.ic_locked_circle);
        mTime.setEnabled(false);
        mSeekbar.setEnabled(false);
        mLength.setEnabled(false);
        mSize.setEnabled(false);
        if (mPlaylistNext != null)
            mPlaylistNext.setEnabled(false);
        if (mPlaylistPrevious != null)
            mPlaylistPrevious.setEnabled(false);
        hideOverlay(true);
        mLockBackButton = true;
        mIsLocked = true;
    }

    /**
     * Remove screen lock
     */
    private void unlockScreen() {
        if (mScreenOrientation != 100)
            setRequestedOrientation(mScreenOrientationLock);
        showInfo(R.string.unlocked, 1000);
        mLock.setImageResource(R.drawable.ic_lock_circle);
        mTime.setEnabled(true);
        mSeekbar.setEnabled(mService == null || mService.isSeekable());
        mLength.setEnabled(true);
        mSize.setEnabled(true);
        if (mPlaylistNext != null)
            mPlaylistNext.setEnabled(true);
        if (mPlaylistPrevious != null)
            mPlaylistPrevious.setEnabled(true);
        mShowing = false;
        mIsLocked = false;
        showOverlay();
        mLockBackButton = false;
    }

    /**
     * Show text in the info view and vertical progress bar for "duration" milliseconds
     *
     * @param barNewValue new volume/brightness value (range: 0 - 15)
     */
    private void showInfoWithVerticalBar(String text, int duration, int barNewValue) {
        showInfo(text, duration);
        if (mVerticalBarProgress == null)
            return;
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) mVerticalBarProgress.getLayoutParams();
        layoutParams.weight = barNewValue;
        mVerticalBarProgress.setLayoutParams(layoutParams);
        mVerticalBar.setVisibility(View.VISIBLE);
    }

    /**
     * Show text in the info view for "duration" milliseconds
     */
    private void showInfo(String text, int duration) {
        if (mPresentation == null) {
            UiTools.setViewVisibility(mVerticalBar, View.GONE);
            UiTools.setViewVisibility(mOverlayInfo, View.VISIBLE);
        } else
            mInfo.setVisibility(View.VISIBLE);
        mInfo.setText(text);
        mHandler.removeMessages(FADE_OUT_INFO);
        mHandler.sendEmptyMessageDelayed(FADE_OUT_INFO, duration);
    }

    private void showInfo(int textid, int duration) {
        if (mPresentation == null) {
            UiTools.setViewVisibility(mVerticalBar, View.GONE);
            UiTools.setViewVisibility(mOverlayInfo, View.VISIBLE);
        } else
            mInfo.setVisibility(View.VISIBLE);
        mInfo.setText(textid);
        mHandler.removeMessages(FADE_OUT_INFO);
        mHandler.sendEmptyMessageDelayed(FADE_OUT_INFO, duration);
    }

    private void fadeOutInfo() {
        if (mPresentation == null) {
            if (mOverlayInfo != null && mOverlayInfo.getVisibility() == View.VISIBLE) {
                mOverlayInfo.startAnimation(AnimationUtils.loadAnimation(
                        VideoPlayerActivity.this, android.R.anim.fade_out));
                UiTools.setViewVisibility(mOverlayInfo, View.INVISIBLE);
            }
        } else if (mInfo.getVisibility() == View.VISIBLE) {
            mInfo.startAnimation(AnimationUtils.loadAnimation(
                    VideoPlayerActivity.this, android.R.anim.fade_out));
            mInfo.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        if (mService == null)
            return false;
        if (!mIsLocked) {
            doPlayPause();
            return true;
        }
        return false;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }

    /* PlaybackService.Callback */

    @Override
    public void update() {
    }

    @Override
    public void updateProgress() {
    }

    @Override
    public void onMediaEvent(Media.Event event) {
        switch (event.type) {
            case Media.Event.ParsedChanged:
                updateNavStatus();
                break;
            case Media.Event.MetaChanged:
                break;
            case Media.Event.SubItemTreeAdded:
                mHasSubItems = true;
                break;
        }
    }

    @Override
    public void onMediaPlayerEvent(MediaPlayer.Event event) {
        switch (event.type) {
            case MediaPlayer.Event.Opening:
                mHasSubItems = false;
                break;
            case MediaPlayer.Event.Playing:
                onPlaying();
                break;
            case MediaPlayer.Event.Paused:
                updateOverlayPausePlay();
                break;
            case MediaPlayer.Event.Stopped:
                exitOK();
                break;
            case MediaPlayer.Event.EndReached:
                /* Don't end the activity if the media has subitems since the next child will be
                 * loaded by the PlaybackService */
                if (!mHasSubItems)
                    endReached();
                break;
            case MediaPlayer.Event.EncounteredError:
                encounteredError();
                break;
            case MediaPlayer.Event.TimeChanged:
                break;
            case MediaPlayer.Event.Vout:
                updateNavStatus();
                if (mMenuIdx == -1)
                    handleVout(event.getVoutCount());
                break;
            case MediaPlayer.Event.ESAdded:
            case MediaPlayer.Event.ESDeleted:
                if (mMenuIdx == -1 && event.getEsChangedType() == Media.Track.Type.Video) {
                    mHandler.removeMessages(CHECK_VIDEO_TRACKS);
                    mHandler.sendEmptyMessageDelayed(CHECK_VIDEO_TRACKS, 1000);
                }
                invalidateESTracks(event.getEsChangedType());
                break;
            case MediaPlayer.Event.SeekableChanged:
                updateSeekable(event.getSeekable());
                break;
            case MediaPlayer.Event.PausableChanged:
                updatePausable(event.getPausable());
                break;
            case MediaPlayer.Event.Buffering:
                if (!mIsPlaying)
                    break;
                if (event.getBuffering() == 100f)
                    stopLoading();
                else if (!mHandler.hasMessages(LOADING_ANIMATION) && !mIsLoading
                        && mTouchAction != TOUCH_SEEK && !mDragging)
                    mHandler.sendEmptyMessageDelayed(LOADING_ANIMATION, LOADING_ANIMATION_DELAY);
                break;
        }
    }

    /**
     * Handle resize of the surface and the overlay
     */
    private final Handler mHandler = new Handler(Looper.getMainLooper(), new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if (mService == null)
                return true;

            switch (msg.what) {
                case FADE_OUT:
                    hideOverlay(false);
                    break;
                case SHOW_PROGRESS:
                    int pos = setOverlayProgress();
                    if (canShowProgress()) {
                        msg = mHandler.obtainMessage(SHOW_PROGRESS);
                        mHandler.sendMessageDelayed(msg, 1000 - (pos % 1000));
                    }
                    break;
                case FADE_OUT_INFO:
                    fadeOutInfo();
                    break;
                case START_PLAYBACK:
                    startPlayback();
                    break;
                case AUDIO_SERVICE_CONNECTION_FAILED:
                    exit(RESULT_CONNECTION_FAILED);
                    break;
                case RESET_BACK_LOCK:
                    mLockBackButton = true;
                    break;
                case CHECK_VIDEO_TRACKS:
                    if (mService.getVideoTracksCount() < 1 && mService.getAudioTracksCount() > 0) {
                        Log.i(TAG, "No video track, open in audio mode");
                        switchToAudioMode(true);
                    }
                    break;
                case LOADING_ANIMATION:
                    startLoading();
                    break;
                case HW_ERROR:
                    handleHardwareAccelerationError();
                    break;
            }
            return true;
        }
    });

    private boolean canShowProgress() {
        return !mDragging && mShowing && mService != null && mService.isPlaying();
    }

    private void onPlaying() {
        mIsPlaying = true;
        stopLoading();
        updateOverlayPausePlay();
        updateNavStatus();
        if (!mService.getCurrentMediaWrapper().hasFlag(MediaWrapper.MEDIA_PAUSED))
            mHandler.sendEmptyMessageDelayed(FADE_OUT, OVERLAY_TIMEOUT);
        setESTracks();
    }

    private void endReached() {
        if (mService == null)
            return;
        if (mService.getRepeatType() == PlaybackService.REPEAT_ONE) {
            seek(0);
            return;
        }
        if (mService.expand() == 0) {
            mHandler.removeMessages(LOADING_ANIMATION);
            mHandler.sendEmptyMessageDelayed(LOADING_ANIMATION, LOADING_ANIMATION_DELAY);
            Log.d(TAG, "Found a video playlist, expanding it");
            mHandler.post(this::loadMedia);
        }
    }

    private void encounteredError() {
        if (isFinishing())
            return;
        //We may not have the permission to access files
        if (AndroidUtil.isMarshMallowOrLater() && mUri != null &&
                TextUtils.equals(mUri.getScheme(), "file") &&
                !Permissions.canReadStorage()) {
            Permissions.checkReadStoragePermission(this, true);
            return;
        }
        /* Encountered Error, exit player with a message */
        mAlertDialog = new AlertDialog.Builder(VideoPlayerActivity.this)
                .setOnCancelListener(dialog -> exit(RESULT_PLAYBACK_ERROR))
                .setPositiveButton(R.string.ok, (dialog, id) -> exit(RESULT_PLAYBACK_ERROR))
                .setTitle(R.string.encountered_error_title)
                .setMessage(R.string.encountered_error_message)
                .create();
        mAlertDialog.show();
    }

    private void handleHardwareAccelerationError() {
        mHardwareAccelerationError = true;
        if (mSwitchingView)
            return;
        Toast.makeText(VLCApplication.getAppContext(), R.string.hardware_acceleration_error, Toast.LENGTH_LONG).show();
        final boolean wasPaused = !mService.isPlaying();
        final long oldTime = mService.getTime();
        int position = mService.getCurrentMediaPosition();
        List<MediaWrapper> list = new ArrayList<>(mService.getMedias());
        final MediaWrapper mw = list.get(position);
        mService.stop();
        if (!isFinishing()) {
            if (wasPaused)
                mw.addFlags(MediaWrapper.MEDIA_PAUSED);
            mw.addFlags(MediaWrapper.MEDIA_NO_HWACCEL);
            mw.addFlags(MediaWrapper.MEDIA_VIDEO);
            mService.load(list, position);
            if (oldTime > 0)
                seek(oldTime);
        }
    }

    private void handleVout(int voutCount) {
        final IVLCVout vlcVout = mService.getVLCVout();
        if (vlcVout.areViewsAttached() && voutCount == 0) {
            /* Video track lost, open in audio mode */
            Log.i(TAG, "Video track lost, switching to audio");
            mSwitchingView = true;
            exit(RESULT_VIDEO_TRACK_LOST);
        }
    }

    public void switchToPopupMode() {
        if (mHardwareAccelerationError || mService == null)
            return;
        mSwitchingView = true;
        mSwitchToPopup = true;
        exitOK();
    }

    public void switchToAudioMode(boolean showUI) {
        if (mHardwareAccelerationError || mService == null)
            return;
        mSwitchingView = true;
        // Show the MainActivity if it is not in background.
        if (showUI) {
            Intent i = new Intent(this, MainActivity.class);
            startActivity(i);
        } else
            mSettings.edit().putBoolean(PreferencesActivity.VIDEO_RESTORE, true).apply();
        exitOK();
    }

    private void changeSurfaceLayout() {
        int sw;
        int sh;

        // get screen size
        if (mPresentation == null) {
            sw = getWindow().getDecorView().getWidth();
            sh = getWindow().getDecorView().getHeight();
        } else {
            sw = mPresentation.getWindow().getDecorView().getWidth();
            sh = mPresentation.getWindow().getDecorView().getHeight();
        }

        if (mService != null) {
            final IVLCVout vlcVout = mService.getVLCVout();
            vlcVout.setWindowSize(sw, sh);
        }

        double dw = sw, dh = sh;
        boolean isPortrait;

        // getWindow().getDecorView() doesn't always take orientation into account, we have to correct the values
        isPortrait = mPresentation == null &&
                getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;

        if (sw > sh && isPortrait || sw < sh && !isPortrait) {
            dw = sh;
            dh = sw;
        }

        // sanity check
        if (dw * dh == 0 || mVideoWidth * mVideoHeight == 0) {
            Log.e(TAG, "Invalid surface size");
            return;
        }

        // compute the aspect ratio
        double ar, vw;
        if (mSarDen == mSarNum) {
            /* No indication about the density, assuming 1:1 */
            vw = mVideoVisibleWidth;
            ar = (double) mVideoVisibleWidth / (double) mVideoVisibleHeight;
        } else {
            /* Use the specified aspect ratio */
            vw = mVideoVisibleWidth * (double) mSarNum / mSarDen;
            ar = vw / mVideoVisibleHeight;
        }

        // compute the display aspect ratio
        double dar = dw / dh;

        switch (mCurrentSize) {
            case SURFACE_BEST_FIT:
                if (dar < ar)
                    dh = dw / ar;
                else
                    dw = dh * ar;
                break;
            case SURFACE_FIT_HORIZONTAL:
                dh = dw / ar;
                break;
            case SURFACE_FIT_VERTICAL:
                dw = dh * ar;
                break;
            case SURFACE_FILL:
                break;
            case SURFACE_16_9:
                ar = 16.0 / 9.0;
                if (dar < ar)
                    dh = dw / ar;
                else
                    dw = dh * ar;
                break;
            case SURFACE_4_3:
                ar = 4.0 / 3.0;
                if (dar < ar)
                    dh = dw / ar;
                else
                    dw = dh * ar;
                break;
            case SURFACE_ORIGINAL:
                dh = mVideoVisibleHeight;
                dw = vw;
                break;
        }

        SurfaceView surface;
        FrameLayout surfaceFrame;
        if (mPresentation == null) {
            surface = mSurfaceView;
            surfaceFrame = mSurfaceFrame;
        } else {
            surface = mPresentation.mSurfaceView;
            surfaceFrame = mPresentation.mSurfaceFrame;
        }

        // set display size
        LayoutParams lp = surface.getLayoutParams();
        lp.width = (int) Math.ceil(dw * mVideoWidth / mVideoVisibleWidth);
        lp.height = (int) Math.ceil(dh * mVideoHeight / mVideoVisibleHeight);
        surface.setLayoutParams(lp);

        // set frame size (crop if necessary)
        lp = surfaceFrame.getLayoutParams();
        lp.width = (int) Math.floor(dw);
        lp.height = (int) Math.floor(dh);
        surfaceFrame.setLayoutParams(lp);

        surface.invalidate();
    }

    private void sendMouseEvent(int action, int button, int x, int y) {
        if (mService == null)
            return;
        final IVLCVout vlcVout = mService.getVLCVout();
        vlcVout.sendMouseEvent(action, button, x, y);
    }

    /**
     * show/hide the overlay
     */

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mService == null)
            return false;
        if (mPlaybackSetting != DelayState.OFF) {
            if (event.getAction() == MotionEvent.ACTION_UP)
                endPlaybackSetting();
            return true;
        } else if (mPlaylist.getVisibility() == View.VISIBLE) {
            togglePlaylist();
            return true;
        }
        if (mTouchControls == 0 || mIsLocked) {
            // locked or swipe disabled, only handle show/hide & ignore all actions
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (!mShowing) {
                    showOverlay();
                } else {
                    hideOverlay(true);
                }
            }
            return false;
        }
        if (mDetector != null && mDetector.onTouchEvent(event))
            return true;

        DisplayMetrics screen = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(screen);

        if (mSurfaceYDisplayRange == 0)
            mSurfaceYDisplayRange = Math.min(screen.widthPixels, screen.heightPixels);

        float x_changed, y_changed;
        if (mTouchX != -1f && mTouchY != -1f) {
            y_changed = event.getRawY() - mTouchY;
            x_changed = event.getRawX() - mTouchX;
        } else {
            x_changed = 0f;
            y_changed = 0f;
        }


        // coef is the gradient's move to determine a neutral zone
        float coef = Math.abs(y_changed / x_changed);
        float xgesturesize = ((x_changed / screen.xdpi) * 2.54f);
        float delta_y = Math.max(1f, (Math.abs(mInitTouchY - event.getRawY()) / screen.xdpi + 0.5f) * 2f);

        /* Offset for Mouse Events */
        int[] offset = new int[2];
        mSurfaceView.getLocationOnScreen(offset);
        int xTouch = Math.round((event.getRawX() - offset[0]) * mVideoWidth / mSurfaceView.getWidth());
        int yTouch = Math.round((event.getRawY() - offset[1]) * mVideoHeight / mSurfaceView.getHeight());

        switch (event.getAction()) {

            case MotionEvent.ACTION_DOWN:
                // Audio
                mTouchY = mInitTouchY = event.getRawY();
                mVol = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                mTouchAction = TOUCH_NONE;
                // Seek
                mTouchX = event.getRawX();
                // Mouse events for the core
                sendMouseEvent(MotionEvent.ACTION_DOWN, 0, xTouch, yTouch);
                break;

            case MotionEvent.ACTION_MOVE:
                // Mouse events for the core
                sendMouseEvent(MotionEvent.ACTION_MOVE, 0, xTouch, yTouch);

                // No volume/brightness action if coef < 2 or a secondary display is connected
                //TODO : Volume action when a secondary display is connected
                if (mTouchAction != TOUCH_SEEK && coef > 2 && mPresentation == null) {
                    if (Math.abs(y_changed / mSurfaceYDisplayRange) < 0.05)
                        return false;
                    mTouchY = event.getRawY();
                    mTouchX = event.getRawX();
                    // Volume (Up or Down - Right side)
                    if (mTouchControls == 1 || (mTouchControls == 3 && (int) mTouchX > (4 * screen.widthPixels / 7))) {
                        doVolumeTouch(y_changed);
                        hideOverlay(true);
                    }
                    // Brightness (Up or Down - Left side)
                    if (mTouchControls == 2 || (mTouchControls == 3 && (int) mTouchX < (3 * screen.widthPixels / 7))) {
                        doBrightnessTouch(y_changed);
                        hideOverlay(true);
                    }
                } else {
                    // Seek (Right or Left move)
                    doSeekTouch(Math.round(delta_y), xgesturesize, false);
                }
                break;

            case MotionEvent.ACTION_UP:
                // Mouse events for the core
                sendMouseEvent(MotionEvent.ACTION_UP, 0, xTouch, yTouch);

                if (mTouchAction == TOUCH_NONE) {
                    if (!mShowing) {
                        showOverlay();
                    } else {
                        hideOverlay(true);
                    }
                }
                // Seek
                if (mTouchAction == TOUCH_SEEK)
                    doSeekTouch(Math.round(delta_y), xgesturesize, true);
                mTouchX = -1f;
                mTouchY = -1f;
                break;
        }
        return mTouchAction != TOUCH_NONE;
    }

    @SuppressLint("DefaultLocale")
    private void doSeekTouch(int coef, float gesturesize, boolean seek) {
        if (coef == 0)
            coef = 1;
        // No seek action if coef > 0.5 and gesturesize < 1cm
        if (Math.abs(gesturesize) < 1 || !mService.isSeekable())
            return;

        if (mTouchAction != TOUCH_NONE && mTouchAction != TOUCH_SEEK)
            return;
        mTouchAction = TOUCH_SEEK;

        long length = mService.getLength();
        long time = getTime();

        // Size of the jump, 10 minutes max (600000), with a bi-cubic progression, for a 8cm gesture
        int jump = (int) ((Math.signum(gesturesize) * ((600000 * Math.pow((gesturesize / 8), 4)) + 3000)) / coef);

        // Adjust the jump
        if ((jump > 0) && ((time + jump) > length))
            jump = (int) (length - time);
        if ((jump < 0) && ((time + jump) < 0))
            jump = (int) -time;

        //Jump !
        if (seek && length > 0)
            seek(time + jump, length);

        if (length > 0)
            //Show the jump's size
            showInfo(String.format("%s%s (%s)%s",
                    jump >= 0 ? "+" : "",
                    Strings.millisToString(jump),
                    Strings.millisToString(time + jump),
                    coef > 1 ? String.format(" x%.1g", 1.0 / coef) : ""), 50);
        else
            showInfo(R.string.unseekable_stream, 1000);
    }

    private void doVolumeTouch(float y_changed) {
        if (mTouchAction != TOUCH_NONE && mTouchAction != TOUCH_VOLUME)
            return;
        float delta = -((y_changed / mSurfaceYDisplayRange) * mAudioMax);
        mVol += delta;
        int vol = (int) Math.min(Math.max(mVol, 0), mAudioMax);
        if (delta != 0f) {
            setAudioVolume(vol);
        }
    }

    private void setAudioVolume(int vol) {
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0);

        /* Since android 4.3, the safe volume warning dialog is displayed only with the FLAG_SHOW_UI flag.
         * We don't want to always show the default UI volume, so show it only when volume is not set. */
        int newVol = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (vol != newVol)
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, AudioManager.FLAG_SHOW_UI);

        mTouchAction = TOUCH_VOLUME;
        vol = vol * 100 / mAudioMax;
        showInfoWithVerticalBar(getString(R.string.volume) + "\n" + Integer.toString(vol) + '%', 1000, vol);
    }

    private void mute(boolean mute) {
        mMute = mute;
        if (mMute)
            mVolSave = mService.getVolume();
        mService.setVolume(mMute ? 0 : mVolSave);
    }

    private void updateMute() {
        mute(!mMute);
        showInfo(mMute ? R.string.sound_off : R.string.sound_on, 1000);
    }

    private void initBrightnessTouch() {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        float brightnesstemp = lp.screenBrightness != -1f ? lp.screenBrightness : 0.6f;
        // Initialize the layoutParams screen brightness
        try {
            if (Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                if (!Permissions.canWriteSettings(this)) {
                    Permissions.checkWriteSettingsPermission(this, Permissions.PERMISSION_SYSTEM_BRIGHTNESS);
                    return;
                }
                Settings.System.putInt(getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                mRestoreAutoBrightness = Settings.System.getInt(getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS) / 255.0f;
            } else if (brightnesstemp == 0.6f) {
                brightnesstemp = Settings.System.getInt(getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS) / 255.0f;
            }
        } catch (SettingNotFoundException e) {
            e.printStackTrace();
        }
        lp.screenBrightness = brightnesstemp;
        getWindow().setAttributes(lp);
        mIsFirstBrightnessGesture = false;
    }

    private void doBrightnessTouch(float y_changed) {
        if (mTouchAction != TOUCH_NONE && mTouchAction != TOUCH_BRIGHTNESS)
            return;
        if (mIsFirstBrightnessGesture) initBrightnessTouch();
        mTouchAction = TOUCH_BRIGHTNESS;

        // Set delta : 2f is arbitrary for now, it possibly will change in the future
        float delta = -y_changed / mSurfaceYDisplayRange;

        changeBrightness(delta);
    }

    private void changeBrightness(float delta) {
        // Estimate and adjust Brightness
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        float brightness = Math.min(Math.max(lp.screenBrightness + delta, 0.01f), 1f);
        setWindowBrightness(brightness);
        brightness = Math.round(brightness * 100);
        showInfoWithVerticalBar(getString(R.string.brightness) + "\n" + (int) brightness + '%', 1000, (int) brightness);
    }

    private void setWindowBrightness(float brightness) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = brightness;
        // Set Brightness
        getWindow().setAttributes(lp);
    }

    /**
     * handle changes of the seekbar (slicer)
     */
    private final OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            mDragging = true;
            showOverlayTimeout(OVERLAY_INFINITE);
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            mDragging = false;
            showOverlay(true);
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (!isFinishing() && fromUser && mService.isSeekable()) {
                seek(progress);
                setOverlayProgress();
                mTime.setText(Strings.millisToString(progress));
                showInfo(Strings.millisToString(progress), 1000);
            }
        }
    };

    public void onAudioSubClick(View anchor) {
        final AppCompatActivity context = this;
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenuInflater().inflate(R.menu.audiosub_tracks, popupMenu.getMenu());
        popupMenu.getMenu().findItem(R.id.video_menu_audio_track).setEnabled(mService.getAudioTracksCount() > 0);
        popupMenu.getMenu().findItem(R.id.video_menu_subtitles).setEnabled(mService.getSpuTracksCount() > 0);
        //FIXME network subs cannot be enabled & screen cast display is broken with picker
        popupMenu.getMenu().findItem(R.id.video_menu_subtitles_picker).setEnabled(!TextUtils.equals(mUri.getScheme(), "http") && mPresentation == null);
        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.video_menu_audio_track) {
                selectAudioTrack();
                return true;
            } else if (item.getItemId() == R.id.video_menu_subtitles) {
                selectSubtitles();
                return true;
            } else if (item.getItemId() == R.id.video_menu_subtitles_picker) {
                if (mUri == null)
                    return false;
                Intent filePickerIntent = new Intent(context, FilePickerActivity.class);
                if (!TextUtils.equals(mUri.getScheme(), "http"))
                    filePickerIntent.setData(Uri.parse(FileUtils.getParent(mUri.toString())));
                context.startActivityForResult(filePickerIntent, 0);
                return true;
            } else if (item.getItemId() == R.id.video_menu_subtitles_download) {
                if (mUri == null)
                    return false;
                MediaUtils.getSubs(VideoPlayerActivity.this, mService.getCurrentMediaWrapper(), success -> {
                    if (success)
                        getSubtitles();
                });
            }
            hideOverlay(true);
            return false;
        });
        popupMenu.show();
        showOverlay();
    }

    @Override
    public void onPopupMenu(View anchor, final int position) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenuInflater().inflate(R.menu.audio_player, popupMenu.getMenu());

        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.audio_player_mini_remove) {
                if (mService != null) {
                    mPlaylistAdapter.remove(position);
                    mService.remove(position);
                    return true;
                }
            }
            return false;
        });
        popupMenu.show();
    }

    @Override
    public void updateList() {
        int oldCount = mPlaylistAdapter.getItemCount();
        if (mService == null)
            return;

        mPlaylistAdapter.clear();

        mPlaylistAdapter.addAll(mService.getMedias());
        int count = mPlaylistAdapter.getItemCount();
        if (oldCount != count)
            mPlaylistAdapter.notifyDataSetChanged();
        else
            mPlaylistAdapter.notifyItemRangeChanged(0, count);

        final int selectionIndex = mService.getCurrentMediaPosition();
        mPlaylist.post(() -> {
            mPlaylistAdapter.setCurrentIndex(selectionIndex);
            mPlaylist.scrollToPosition(selectionIndex);
        });
    }

    @Override
    public void onSelectionSet(int position) {
        mPlaylist.scrollToPosition(position);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.player_overlay_play:
                doPlayPause();
                break;
            case R.id.playlist_toggle:
                togglePlaylist();
                break;
            case R.id.playlist_next:
                mService.next();
                break;
            case R.id.playlist_previous:
                mService.previous();
                break;
            case R.id.player_overlay_forward:
                seekDelta(10000);
                break;
            case R.id.player_overlay_rewind:
                seekDelta(-10000);
                break;
            case R.id.lock_overlay_button:
                if (mIsLocked)
                    unlockScreen();
                else
                    lockScreen();
                break;
            case R.id.player_overlay_size:
                resizeVideo();
                break;
            case R.id.player_overlay_navmenu:
                showNavMenu();
                break;
            case R.id.player_overlay_length:
            case R.id.player_overlay_time:
                mDisplayRemainingTime = !mDisplayRemainingTime;
                showOverlay();
                mSettings.edit().putBoolean(KEY_REMAINING_TIME_DISPLAY, mDisplayRemainingTime).apply();
                break;
            case R.id.player_delay_minus:
                if (mPlaybackSetting == DelayState.AUDIO)
                    delayAudio(-50000);
                else if (mPlaybackSetting == DelayState.SUBS)
                    delaySubs(-50000);
                else if (mPlaybackSetting == DelayState.SPEED)
                    changeSpeed(-0.05f);
                break;
            case R.id.player_delay_plus:
                if (mPlaybackSetting == DelayState.AUDIO)
                    delayAudio(50000);
                else if (mPlaybackSetting == DelayState.SUBS)
                    delaySubs(50000);
                else if (mPlaybackSetting == DelayState.SPEED)
                    changeSpeed(0.05f);
                break;
            case R.id.player_overlay_adv_function:
                showAdvancedOptions();
                break;
            case R.id.player_overlay_tracks:
                onAudioSubClick(v);
        }
    }

    public boolean onLongClick(View v) {
        switch (v.getId()) {
            case R.id.player_overlay_play:
                if (mService == null)
                    return false;
                if (mService.getRepeatType() == PlaybackService.REPEAT_ONE) {
                    showInfo(getString(R.string.repeat), 1000);
                    mService.setRepeatType(PlaybackService.REPEAT_NONE);
                } else {
                    mService.setRepeatType(PlaybackService.REPEAT_ONE);
                    showInfo(getString(R.string.repeat_single), 1000);
                }
                return true;
            default:
                return false;
        }
    }

    private interface TrackSelectedListener {
        boolean onTrackSelected(int trackID);
    }

    private void selectTrack(final MediaPlayer.TrackDescription[] tracks, int currentTrack, int titleId,
                             final TrackSelectedListener listener) {
        if (listener == null)
            throw new IllegalArgumentException("listener must not be null");
        if (tracks == null)
            return;
        final String[] nameList = new String[tracks.length];
        final int[] idList = new int[tracks.length];
        int i = 0;
        int listPosition = 0;
        for (MediaPlayer.TrackDescription track : tracks) {
            idList[i] = track.id;
            nameList[i] = track.name;
            // map the track position to the list position
            if (track.id == currentTrack)
                listPosition = i;
            i++;
        }

        if (!isFinishing()) {
            mAlertDialog = new AlertDialog.Builder(VideoPlayerActivity.this)
                    .setTitle(titleId)
                    .setSingleChoiceItems(nameList, listPosition, (dialog, listPosition1) -> {
                        int trackID = -1;
                        // Reverse map search...
                        for (MediaPlayer.TrackDescription track : tracks) {
                            if (idList[listPosition1] == track.id) {
                                trackID = track.id;
                                break;
                            }
                        }
                        listener.onTrackSelected(trackID);
                        dialog.dismiss();
                    })
                    .create();
            mAlertDialog.setCanceledOnTouchOutside(true);
            mAlertDialog.setOwnerActivity(VideoPlayerActivity.this);
            mAlertDialog.show();
        }
    }

    private void selectAudioTrack() {
        setESTrackLists();
        selectTrack(mAudioTracksList, mService.getAudioTrack(), R.string.track_audio,
                trackID -> {
                    if (trackID < -1 || mService == null)
                        return false;
                    MediaDatabase.getInstance().updateMedia(
                            mUri,
                            MediaDatabase.INDEX_MEDIA_AUDIOTRACK,
                            trackID);
                    mService.setAudioTrack(trackID);
                    return true;
                });
    }

    private void selectSubtitles() {
        setESTrackLists();
        selectTrack(mSubtitleTracksList, mService.getSpuTrack(), R.string.track_text,
                trackID -> {
                    if (trackID < -1 || mService == null)
                        return false;

                    MediaDatabase.getInstance().updateMedia(
                            mUri,
                            MediaDatabase.INDEX_MEDIA_SPUTRACK,
                            trackID);
                    mService.setSpuTrack(trackID);
                    return true;
                });
    }

    private void showNavMenu() {
        if (mMenuIdx >= 0)
            mService.setTitleIdx(mMenuIdx);
    }

    private void updateSeekable(boolean seekable) {
        if (mRewind != null) {
            mRewind.setEnabled(seekable);
            mRewind.setImageResource(seekable
                    ? R.drawable.ic_rewind_circle
                    : R.drawable.ic_rewind_circle_disable_o);
        }
        if (mForward != null) {
            mForward.setEnabled(seekable);
            mForward.setImageResource(seekable
                    ? R.drawable.ic_forward_circle
                    : R.drawable.ic_forward_circle_disable_o);
        }
        if (!mIsLocked)
            mSeekbar.setEnabled(seekable);
    }

    private void updatePausable(boolean pausable) {
        mPlayPause.setEnabled(pausable);
        if (!pausable)
            mPlayPause.setImageResource(R.drawable.ic_play_circle_disable_o);
        else {
            mDetector = new GestureDetectorCompat(this, mGestureListener);
            mDetector.setOnDoubleTapListener(this);
        }
    }

    private void doPlayPause() {
        if (!mService.isPausable())
            return;

        if (mService.isPlaying()) {
            pause();
            showOverlayTimeout(OVERLAY_INFINITE);
        } else {
            play();
            showOverlayTimeout(OVERLAY_TIMEOUT);
        }
        mPlayPause.requestFocus();
    }

    private long getTime() {
        long time = mService.getTime();
        if (mForcedTime != -1 && mLastTime != -1) {
            /* XXX: After a seek, mService.getTime can return the position before or after
             * the seek position. Therefore we return mForcedTime in order to avoid the seekBar
             * to move between seek position and the actual position.
             * We have to wait for a valid position (that is after the seek position).
             * to re-init mLastTime and mForcedTime to -1 and return the actual position.
             */
            if (mLastTime > mForcedTime) {
                if (time <= mLastTime && time > mForcedTime || time > mLastTime)
                    mLastTime = mForcedTime = -1;
            } else {
                if (time > mForcedTime)
                    mLastTime = mForcedTime = -1;
            }
        } else if (time == 0)
            time = (int) mService.getCurrentMediaWrapper().getTime();
        return mForcedTime == -1 ? time : mForcedTime;
    }

    private void seek(long position) {
        seek(position, mService.getLength());
    }

    private void seek(long position, long length) {
        mForcedTime = position;
        mLastTime = mService.getTime();
        mService.seek(position, length);

        for (int i = 0; i < subs.size(); i++) {
            Sub sub = subs.get(i);
            if (position < sub.endTime) {
                if (position >= sub.startTime) {
                    lastPlayedPosition = i;
                } else {
                    lastPlayedPosition = i - 1;
                }
                break;
            }
        }
        hideTranslate();
        reInitSubChangerWithCheckPlay();
    }

    private void reInitSubChangerWithCheckPlay() {
        if (mService.isPlaying()) {
            reInitSubChanger();
        } else {
            if (subChanger != null && !subChanger.isUnsubscribed()) {
                subChanger.unsubscribe();
            }
            publishSubject.onNext(lastPlayedPosition);
        }
    }

    private void seekDelta(int delta) {
        // unseekable stream
        if (mService.getLength() <= 0 || !mService.isSeekable()) return;

        long position = getTime() + delta;
        if (position < 0) position = 0;
        seek(position);
        showInfo(Strings.millisToString(mService.getTime()) + "/" + Strings.millisToString(mService.getLength()), 1000);
    }

    private void initSeekButton() {
        mRewind = (ImageView) findViewById(R.id.player_overlay_rewind);
        mForward = (ImageView) findViewById(R.id.player_overlay_forward);
        mRewind.setVisibility(View.VISIBLE);
        mForward.setVisibility(View.VISIBLE);
        mRewind.setOnClickListener(this);
        mForward.setOnClickListener(this);
        mRewind.setOnTouchListener(new OnRepeatListener(this));
        mForward.setOnTouchListener(new OnRepeatListener(this));
    }

    private void resizeVideo() {
        if (mCurrentSize < SURFACE_ORIGINAL) {
            mCurrentSize++;
        } else {
            mCurrentSize = 0;
        }
        changeSurfaceLayout();
        switch (mCurrentSize) {
            case SURFACE_BEST_FIT:
                showInfo(R.string.surface_best_fit, 1000);
                break;
            case SURFACE_FIT_HORIZONTAL:
                showInfo(R.string.surface_fit_horizontal, 1000);
                break;
            case SURFACE_FIT_VERTICAL:
                showInfo(R.string.surface_fit_vertical, 1000);
                break;
            case SURFACE_FILL:
                showInfo(R.string.surface_fill, 1000);
                break;
            case SURFACE_16_9:
                showInfo("16:9", 1000);
                break;
            case SURFACE_4_3:
                showInfo("4:3", 1000);
                break;
            case SURFACE_ORIGINAL:
                showInfo(R.string.surface_original, 1000);
                break;
        }
        showOverlay();
    }

    /**
     * show overlay
     *
     * @param forceCheck: adjust the timeout in function of playing state
     */
    private void showOverlay(boolean forceCheck) {
        if (forceCheck)
            mOverlayTimeout = 0;
        showOverlayTimeout(0);
    }

    /**
     * show overlay with the previous timeout value
     */
    private void showOverlay() {
        showOverlay(false);
    }

    /**
     * show overlay
     */
    private void showOverlayTimeout(int timeout) {
        if (mService == null)
            return;
        if (timeout != 0)
            mOverlayTimeout = timeout;
        if (mOverlayTimeout == 0)
            mOverlayTimeout = mService.isPlaying() ? OVERLAY_TIMEOUT : OVERLAY_INFINITE;
        if (mIsNavMenu) {
            mShowing = true;
            return;
        }
        mHandler.sendEmptyMessage(SHOW_PROGRESS);
        if (!mShowing) {
            mShowing = true;
            if (!mIsLocked) {
                mPlayPause.setVisibility(View.VISIBLE);
                UiTools.setViewVisibility(mTracks, View.VISIBLE);
                UiTools.setViewVisibility(mAdvOptions, View.VISIBLE);
                UiTools.setViewVisibility(mSize, View.VISIBLE);
                UiTools.setViewVisibility(mRewind, View.VISIBLE);
                UiTools.setViewVisibility(mForward, View.VISIBLE);
                UiTools.setViewVisibility(mPlaylistNext, View.VISIBLE);
                UiTools.setViewVisibility(mPlaylistPrevious, View.VISIBLE);
            }
            dimStatusBar(false);
            mOverlayProgress.setVisibility(View.VISIBLE);
            if (mPresentation != null) mOverlayBackground.setVisibility(View.VISIBLE);
        }
        mHandler.removeMessages(FADE_OUT);
        if (mOverlayTimeout != OVERLAY_INFINITE)
            mHandler.sendMessageDelayed(mHandler.obtainMessage(FADE_OUT), mOverlayTimeout);
        updateOverlayPausePlay();
        if (!(mObjectFocused == null)) {
            if (mObjectFocused.isFocusable())
                mObjectFocused.requestFocus();
            mObjectFocused = null;
        }
    }


    /**
     * hider overlay
     */
    private void hideOverlay(boolean fromUser) {
        if (mShowing) {
            mHandler.removeMessages(FADE_OUT);
            mHandler.removeMessages(SHOW_PROGRESS);
            Log.i(TAG, "remove View!");
            mObjectFocused = getCurrentFocus();
            if (mOverlayTips != null) mOverlayTips.setVisibility(View.INVISIBLE);
            if (!fromUser && !mIsLocked) {
                mOverlayProgress.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
                mPlayPause.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
                if (mTracks != null)
                    mTracks.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
                if (mAdvOptions != null)
                    mAdvOptions.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
                if (mRewind != null)
                    mRewind.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
                if (mForward != null)
                    mForward.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
                if (mPlaylistNext != null)
                    mPlaylistNext.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
                if (mPlaylistPrevious != null)
                    mPlaylistPrevious.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
                mSize.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
            }
            if (mPresentation != null) {
                mOverlayBackground.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
                mOverlayBackground.setVisibility(View.INVISIBLE);
            }
            mOverlayProgress.setVisibility(View.INVISIBLE);
            mPlayPause.setVisibility(View.INVISIBLE);
            UiTools.setViewVisibility(mTracks, View.INVISIBLE);
            UiTools.setViewVisibility(mAdvOptions, View.INVISIBLE);
            UiTools.setViewVisibility(mSize, View.INVISIBLE);
            UiTools.setViewVisibility(mRewind, View.INVISIBLE);
            UiTools.setViewVisibility(mForward, View.INVISIBLE);
            UiTools.setViewVisibility(mPlaylistNext, View.INVISIBLE);
            UiTools.setViewVisibility(mPlaylistPrevious, View.INVISIBLE);
            mShowing = false;
            dimStatusBar(true);
        } else if (!fromUser) {
            /*
             * Try to hide the Nav Bar again.
             * It seems that you can't hide the Nav Bar if you previously
             * showed it in the last 1-2 seconds.
             */
            dimStatusBar(true);
        }
    }

    /**
     * Dim the status bar and/or navigation icons when needed on Android 3.x.
     * Hide it on Android 4.0 and later
     */
    private void dimStatusBar(boolean dim) {
        if (dim || mIsLocked)
            mActionBar.hide();
        else
            mActionBar.show();
        if (mIsNavMenu)
            return;

        int visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        int navbar = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (dim || mIsLocked) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            navbar |= View.SYSTEM_UI_FLAG_LOW_PROFILE;
            if (!AndroidDevices.hasCombBar()) {
                navbar |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
                if (AndroidUtil.isKitKatOrLater())
                    visibility |= View.SYSTEM_UI_FLAG_IMMERSIVE;
                visibility |= View.SYSTEM_UI_FLAG_FULLSCREEN;
            }
        } else {
            mActionBar.show();
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            visibility |= View.SYSTEM_UI_FLAG_VISIBLE;
        }

        if (AndroidDevices.hasNavBar())
            visibility |= navbar;
        getWindow().getDecorView().setSystemUiVisibility(visibility);
    }

    private void showTitle() {
        if (mIsNavMenu)
            return;
        mActionBar.show();

        int visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        int navbar = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        navbar |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;

        if (AndroidDevices.hasNavBar())
            visibility |= navbar;
        getWindow().getDecorView().setSystemUiVisibility(visibility);

    }

    private void updateOverlayPausePlay() {
        if (mService == null)
            return;
        if (mService.isPausable())
            mPlayPause.setImageResource(mService.isPlaying() ? R.drawable.ic_pause_circle
                    : R.drawable.ic_play_circle);
    }

    /**
     * update the overlay
     */
    private int setOverlayProgress() {
        if (mService == null) {
            return 0;
        }
        int time = (int) getTime();
        int length = (int) mService.getLength();
        if (length == 0) {
            MediaWrapper media = MediaDatabase.getInstance().getMedia(mUri);
            if (media != null)
                length = (int) media.getLength();
        }

        // Update all view elements
        mSeekbar.setMax(length);
        mSeekbar.setProgress(time);
        if (time >= 0) mTime.setText(Strings.millisToString(time));
        if (length >= 0) mLength.setText(mDisplayRemainingTime && length > 0
                ? "-" + '\u00A0' + Strings.millisToString(length - time)
                : Strings.millisToString(length));

        return time;
    }

    private void invalidateESTracks(int type) {
        switch (type) {
            case Media.Track.Type.Audio:
                mAudioTracksList = null;
                break;
            case Media.Track.Type.Text:
                mSubtitleTracksList = null;
                break;
        }
    }

    private void setESTracks() {
        if (mLastAudioTrack >= -1) {
            mService.setAudioTrack(mLastAudioTrack);
            mLastAudioTrack = -2;
        }
        if (mLastSpuTrack >= -1) {
            mService.setSpuTrack(mLastSpuTrack);
            mLastSpuTrack = -2;
        }
    }

    private void setESTrackLists() {
        if (mAudioTracksList == null && mService.getAudioTracksCount() > 0)
            mAudioTracksList = mService.getAudioTracks();
        if (mSubtitleTracksList == null && mService.getSpuTracksCount() > 0)
            mSubtitleTracksList = mService.getSpuTracks();
    }


    /**
     *
     */
    private void play() {
        mService.play();
        if (mRootView != null)
            mRootView.setKeepScreenOn(true);

        hideTranslate();
        reInitSubChanger();
    }

    private void hideTranslate() {
        binding.getViewModel().translations = null;
        binding.invalidateAll();
    }

    private void reInitSubChanger() {
        if (subChanger != null && !subChanger.isUnsubscribed()) {
            subChanger.unsubscribe();
        }
        subChanger = publishSubject.map(i -> i + 1)
                .filter(i -> i >= 0 && i < subs.size())
                .delay(i -> {
                    int delay = subs.get(i).startTime - (int) getTime() - SUB_TIME_OFFSET;
                    if (delay < 0) delay = 0;
                    return Observable.timer(delay, TimeUnit.MILLISECONDS);
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(i -> publishSubject.onNext(i));

        publishSubject.onNext(lastPlayedPosition);
    }

    /**
     *
     */
    private void pause() {
        mService.pause();
        if (mRootView != null)
            mRootView.setKeepScreenOn(false);

        if (subChanger != null && !subChanger.isUnsubscribed()) {
            subChanger.unsubscribe();
        }
        if (subRemover != null && !subRemover.isUnsubscribed()) {
            subRemover.unsubscribe();
        }
    }

    /*
     * Additionnal method to prevent alert dialog to pop up
     */
    @SuppressWarnings({"unchecked"})
    private void loadMedia(boolean fromStart) {
        mAskResume = false;
        getIntent().putExtra(PLAY_EXTRA_FROM_START, fromStart);
        loadMedia();
    }

    /**
     * External extras:
     * - position (long) - position of the video to start with (in ms)
     * - subtitles_location (String) - location of a subtitles file to load
     * - from_start (boolean) - Whether playback should start from start or from resume point
     * - title (String) - video title, will be guessed from file if not set.
     */
    @SuppressLint("SdCardPath")
    @SuppressWarnings({"unchecked"})
    private void loadMedia() {
        if (mService == null)
            return;
        mUri = null;
        mIsPlaying = false;
        String title = getResources().getString(R.string.title);
        boolean fromStart = false;
        String itemTitle = null;
        int positionInPlaylist = -1;
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        long savedTime = extras != null ? extras.getLong(PLAY_EXTRA_START_TIME) : 0L; // position passed in by intent (ms)
        if (extras != null && savedTime == 0L)
            savedTime = extras.getInt(PLAY_EXTRA_START_TIME);
        /*
         * If the activity has been paused by pressing the power button, then
         * pressing it again will show the lock screen.
         * But onResume will also be called, even if vlc-android is still in
         * the background.
         * To workaround this, pause playback if the lockscreen is displayed.
         */
        final KeyguardManager km = (KeyguardManager) VLCApplication.getAppContext().getSystemService(KEYGUARD_SERVICE);
        if (km.inKeyguardRestrictedInputMode())
            mWasPaused = true;
        if (mWasPaused)
            Log.d(TAG, "Video was previously paused, resuming in paused mode");

        if (intent.getData() != null)
            mUri = intent.getData();
        if (extras != null) {
            if (PLAY_FROM_TORRENT.equals(intent.getAction())) {
                Torrent torrent = intent.getParcelableExtra(PLAY_EXTRA_TORRENT);

                if (!torrentConnection.bound) {
                    startService(new Intent(this, TorrentTaskService.class));
                    bindService(new Intent(getApplicationContext(), TorrentTaskService.class),
                            torrentConnection, Context.BIND_AUTO_CREATE);
                    addTorrentsRequest(Collections.singletonList(torrent));
                    return;
                } else {
                    mUri = Uri.parse("file://" + torrent.getDownloadPath() + "/" + torrent.getName());
                }
            }
            if (intent.hasExtra(PLAY_EXTRA_ITEM_LOCATION))
                mUri = extras.getParcelable(PLAY_EXTRA_ITEM_LOCATION);
            fromStart = extras.getBoolean(PLAY_EXTRA_FROM_START, true);
            mAskResume &= !fromStart;
            positionInPlaylist = extras.getInt(PLAY_EXTRA_OPENED_POSITION, -1);

            if (intent.hasExtra(PLAY_EXTRA_SUBTITLES_LOCATION))
                mSubtitleSelectedFiles.add(extras.getString(PLAY_EXTRA_SUBTITLES_LOCATION));
            if (intent.hasExtra(PLAY_EXTRA_ITEM_TITLE))
                itemTitle = extras.getString(PLAY_EXTRA_ITEM_TITLE);
        }

        if (positionInPlaylist != -1 && mService.hasMedia()) {
            // Provided externally from AudioService
            Log.d(TAG, "Continuing playback from PlaybackService at index " + positionInPlaylist);
            MediaWrapper openedMedia = mService.getMedias().get(positionInPlaylist);
            if (openedMedia == null) {
                encounteredError();
                return;
            }
            mUri = openedMedia.getUri();
            itemTitle = openedMedia.getTitle();
            updateSeekable(mService.isSeekable());
            updatePausable(mService.isPausable());
        }

        if (mUri != null) {
            if (mService.hasMedia() && !mUri.equals(mService.getCurrentMediaWrapper().getUri()))
                mService.stop();
            // restore last position
            MediaWrapper media = MediaDatabase.getInstance().getMedia(mUri);
            if (media == null && TextUtils.equals(mUri.getScheme(), "file") &&
                    mUri.getPath() != null && mUri.getPath().startsWith("/sdcard")) {
                mUri = FileUtils.convertLocalUri(mUri);
                media = MediaDatabase.getInstance().getMedia(mUri);
            }
            if (media != null) {
                // in media library
                if (media.getTime() > 0 && !fromStart && positionInPlaylist == -1) {
                    if (mAskResume) {
                        showConfirmResumeDialog();
                        return;
                    }
                }
                // Consume fromStart option after first use to prevent
                // restarting again when playback is paused.
                intent.putExtra(PLAY_EXTRA_FROM_START, false);
                if (fromStart || mService.isPlaying())
                    media.setTime(0L);
                else if (savedTime <= 0L)
                    savedTime = media.getTime();

                mLastAudioTrack = media.getAudioTrack();
                mLastSpuTrack = media.getSpuTrack();
            } else {
                // not in media library

                if (savedTime > 0L && mAskResume) {
                    showConfirmResumeDialog();
                    return;
                } else {
                    long rTime = mSettings.getLong(PreferencesActivity.VIDEO_RESUME_TIME, -1);
                    if (rTime > 0 && !fromStart) {
                        if (mAskResume) {
                            showConfirmResumeDialog();
                            return;
                        } else {
                            Editor editor = mSettings.edit();
                            editor.putLong(PreferencesActivity.VIDEO_RESUME_TIME, -1);
                            Util.commitPreferences(editor);
                            savedTime = rTime;
                        }
                    }
                }
            }

            // Start playback & seek
            mService.addCallback(this);
            /* prepare playback */
            boolean hasMedia = mService.hasMedia();
            if (hasMedia)
                media = mService.getCurrentMediaWrapper();
            else if (media == null)
                media = new MediaWrapper(mUri);
            if (mWasPaused)
                media.addFlags(MediaWrapper.MEDIA_PAUSED);
            if (mHardwareAccelerationError || intent.hasExtra(PLAY_DISABLE_HARDWARE))
                media.addFlags(MediaWrapper.MEDIA_NO_HWACCEL);
            media.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
            media.addFlags(MediaWrapper.MEDIA_VIDEO);

            if (savedTime <= 0L && media.getTime() > 0L)
                savedTime = media.getTime();
            if (savedTime > 0L && !mService.isPlaying())
                mService.saveTimeToSeek(savedTime);

            // Handle playback
            if (!hasMedia)
                mService.load(media);
            else if (!mService.isPlaying())
                mService.playIndex(positionInPlaylist);
            else {
                onPlaying();
            }

            // Get possible subtitles
            getSubtitles();

            // Get the title
            if (itemTitle == null)
                title = mUri.getLastPathSegment();
        } else {
            mService.addCallback(this);
            mService.loadLastPlaylist(PlaybackService.TYPE_VIDEO);
            MediaWrapper mw = mService.getCurrentMediaWrapper();
            if (mw == null) {
                finish();
                return;
            }
            mUri = mService.getCurrentMediaWrapper().getUri();
        }
        if (itemTitle != null)
            title = itemTitle;
        mTitle.setText(title);

        if (mWasPaused)
            showOverlay(true);
    }

    @SuppressWarnings({"unchecked"})
    public void getSubtitles() {
        final String subtitleList_serialized = mSettings.getString(PreferencesActivity.VIDEO_SUBTITLE_FILES, null);
        VLCApplication.runBackground(() -> {
            ArrayList<String> prefsList = new ArrayList<>();
            if (subtitleList_serialized != null) {
                ByteArrayInputStream bis = new ByteArrayInputStream(subtitleList_serialized.getBytes());
                try {
                    ObjectInputStream ois = new ObjectInputStream(bis);
                    prefsList = (ArrayList<String>) ois.readObject();
                } catch (Exception ignored) {
                }
            }
            if (!TextUtils.equals(mUri.getScheme(), "fd"))
                prefsList.addAll(MediaDatabase.getInstance().getSubtitles(mUri.getLastPathSegment()));
            for (String x : prefsList) {
                if (!mSubtitleSelectedFiles.contains(x))
                    mSubtitleSelectedFiles.add(x);
            }

            // Add any selected subtitle file from the file picker
            if (mSubtitleSelectedFiles.size() > 0) {
                mHandler.post(() -> {
                    try {
//                        String subPath = findSubPath(mUri.getPath());
                        subs = suber().parse(new File(mSubtitleSelectedFiles.get(0))).subs;
                        reInitSubChanger();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (mService != null)
                        for (String file : mSubtitleSelectedFiles) {
                            Log.i(TAG, "Adding user-selected subtitle " + file);
                            mService.addSubtitleTrack(file, true);
                        }
                });
            }
        });
    }

    @Nullable
    private String findSubPath(String videoPath) {
        Matcher matcher = SUB_FILE_PATH_PATTERN.matcher(videoPath);
        if (matcher.find()) {
            String dir = matcher.group(1);
            String fileName = matcher.group(2);

            File dirFile = new File(dir);
            if (dirFile.isDirectory()) {
                for (File file : dirFile.listFiles()) {
                    String name = file.getName();
                    String fileNameWithoutExt = name.replaceFirst("[.][^.]+$", "");
                    if (fileName.equalsIgnoreCase(fileNameWithoutExt) && SUB_FILE_PATTERN.matcher(name).matches()) {
                        return file.getAbsolutePath();
                    }
                }
            }
        }
        return null;
    }

    private int getScreenRotation() {
        WindowManager wm = (WindowManager) VLCApplication.getAppContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO /* Android 2.2 has getRotation */) {
//            try {
//                Method m = display.getClass().getDeclaredMethod("getRotation");
//                return (Integer) m.invoke(display);
//            } catch (Exception e) {
//                return Surface.ROTATION_0;
//            }
//        } else {
        return display.getRotation();
//        }
    }

    private int getScreenOrientation(int mode) {
        switch (mode) {
            case 99: //screen orientation user
                return AndroidUtil.isJellyBeanMR2OrLater() ?
                        ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR :
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR;
            case 101: //screen orientation landscape
                return ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
            case 102: //screen orientation portrait
                return ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
        }
        /*
         mScreenOrientation = 100, we lock screen at its current orientation
         */
        WindowManager wm = (WindowManager) VLCApplication.getAppContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        int rot = getScreenRotation();
        /*
         * Since getRotation() returns the screen's "natural" orientation,
         * which is not guaranteed to be SCREEN_ORIENTATION_PORTRAIT,
         * we have to invert the SCREEN_ORIENTATION value if it is "naturally"
         * landscape.
         */
        @SuppressWarnings("deprecation")
        boolean defaultWide = display.getWidth() > display.getHeight();
        if (rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270)
            defaultWide = !defaultWide;
        if (defaultWide) {
            switch (rot) {
                case Surface.ROTATION_0:
                    return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                case Surface.ROTATION_90:
                    return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                case Surface.ROTATION_180:
                    // SCREEN_ORIENTATION_REVERSE_PORTRAIT only available since API
                    return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                case Surface.ROTATION_270:
                    // SCREEN_ORIENTATION_REVERSE_LANDSCAPE only available since API
                    return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                default:
                    return 0;
            }
        } else {
            switch (rot) {
                case Surface.ROTATION_0:
                    return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                case Surface.ROTATION_90:
                    return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                case Surface.ROTATION_180:
                    // SCREEN_ORIENTATION_REVERSE_PORTRAIT only available since API
                    return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                case Surface.ROTATION_270:
                    // SCREEN_ORIENTATION_REVERSE_LANDSCAPE only available since API
                    return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                default:
                    return 0;
            }
        }
    }

    public void showConfirmResumeDialog() {
        if (isFinishing())
            return;
        mService.pause();
        /* Encountered Error, exit player with a message */
        mAlertDialog = new AlertDialog.Builder(VideoPlayerActivity.this)
                .setMessage(R.string.confirm_resume)
                .setPositiveButton(R.string.resume_from_position, (dialog, id) -> loadMedia(false))
                .setNegativeButton(R.string.play_from_start, (dialog, id) -> loadMedia(true))
                .create();
        mAlertDialog.setCancelable(false);
        mAlertDialog.show();
    }

    public void showAdvancedOptions() {
        FragmentManager fm = getSupportFragmentManager();
        AdvOptionsDialog advOptionsDialog = new AdvOptionsDialog();
        advOptionsDialog.setOnDismissListener(dialog -> dimStatusBar(true));
        advOptionsDialog.show(fm, "fragment_adv_options");
        hideOverlay(false);
    }

    private void togglePlaylist() {
        if (mPlaylist.getVisibility() == View.VISIBLE) {
            mPlaylist.setVisibility(View.GONE);
            mPlaylist.setOnClickListener(null);
            return;
        }
        hideOverlay(true);
        mPlaylist.setVisibility(View.VISIBLE);
        mPlaylist.setAdapter(mPlaylistAdapter);
        updateList();
    }

    private BroadcastReceiver mBtReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED:
                case BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED:
                    long savedDelay = mSettings.getLong(KEY_BLUETOOTH_DELAY, 0L);
                    long currentDelay = mService.getAudioDelay();
                    if (savedDelay != 0L) {
                        boolean connected = intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, -1) == BluetoothA2dp.STATE_CONNECTED;
                        if (connected && currentDelay == 0L)
                            toggleBtDelay(true);
                        else if (!connected && savedDelay == currentDelay)
                            toggleBtDelay(false);
                    }
            }
        }
    };

    private void toggleBtDelay(boolean connected) {
        mService.setAudioDelay(connected ? mSettings.getLong(KEY_BLUETOOTH_DELAY, 0) : 0L);
    }

    private OnClickListener mBtSaveListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            mSettings.edit().putLong(KEY_BLUETOOTH_DELAY, mService.getAudioDelay()).apply();
        }
    };

    private void createPresentation() {
        if (mMediaRouter == null || mEnableCloneMode)
            return;

        // Get the current route and its presentation display.
        MediaRouter.RouteInfo route = mMediaRouter.getSelectedRoute(
                MediaRouter.ROUTE_TYPE_LIVE_VIDEO);

        Display presentationDisplay = route != null ? route.getPresentationDisplay() : null;

        if (presentationDisplay != null) {
            // Show a new presentation if possible.
            Log.i(TAG, "Showing presentation on display: " + presentationDisplay);
            mPresentation = new SecondaryDisplay(this, presentationDisplay);
            mPresentation.setOnDismissListener(mOnDismissListener);
            try {
                mPresentation.show();
                mPresentationDisplayId = presentationDisplay.getDisplayId();
            } catch (WindowManager.InvalidDisplayException ex) {
                Log.w(TAG, "Couldn't show presentation!  Display was removed in "
                        + "the meantime.", ex);
                mPresentation = null;
            }
        } else
            Log.i(TAG, "No secondary display detected");
    }

    private void removePresentation() {
        if (mMediaRouter == null)
            return;

        // Dismiss the current presentation if the display has changed.
        Log.i(TAG, "Dismissing presentation because the current route no longer "
                + "has a presentation display.");
        if (mPresentation != null) mPresentation.dismiss();
        mPresentation = null;
        mPresentationDisplayId = -1;
        stopPlayback();

        recreate();
    }

    /**
     * Listens for when presentations are dismissed.
     */
    private final DialogInterface.OnDismissListener mOnDismissListener = new DialogInterface.OnDismissListener() {
        @Override
        public void onDismiss(DialogInterface dialog) {
            if (dialog == mPresentation) {
                Log.i(TAG, "Presentation was dismissed.");
                mPresentation = null;
            }
        }
    };

    private static final class SecondaryDisplay extends Presentation {
        public final static String TAG = "VLC/SecondaryDisplay";

        private SurfaceView mSurfaceView;
        private SurfaceView mSubtitlesSurfaceView;
        private FrameLayout mSurfaceFrame;

        public SecondaryDisplay(Context context, Display display) {
            super(context, display);
            if (context instanceof AppCompatActivity) {
                setOwnerActivity((AppCompatActivity) context);
            }
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.player_remote);

            mSurfaceView = (SurfaceView) findViewById(R.id.remote_player_surface);
            mSubtitlesSurfaceView = (SurfaceView) findViewById(R.id.remote_subtitles_surface);
            mSurfaceFrame = (FrameLayout) findViewById(R.id.remote_player_surface_frame);

            mSubtitlesSurfaceView.setZOrderMediaOverlay(true);
            mSubtitlesSurfaceView.getHolder().setFormat(PixelFormat.TRANSLUCENT);

            VideoPlayerActivity activity = (VideoPlayerActivity) getOwnerActivity();
            if (activity == null) {
                Log.e(TAG, "Failed to get the VideoPlayerActivity instance, secondary display won't work");
                return;
            }

            Log.i(TAG, "Secondary display created");
        }
    }

    /**
     * Start the video loading animation.
     */
    private void startLoading() {
        if (mIsLoading)
            return;
        mIsLoading = true;
        AnimationSet anim = new AnimationSet(true);
        RotateAnimation rotate = new RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotate.setDuration(800);
        rotate.setInterpolator(new DecelerateInterpolator());
        rotate.setRepeatCount(RotateAnimation.INFINITE);
        anim.addAnimation(rotate);
        mLoading.setVisibility(View.VISIBLE);
        mLoading.startAnimation(anim);
    }

    /**
     * Stop the video loading animation.
     */
    private void stopLoading() {
        mHandler.removeMessages(LOADING_ANIMATION);
        if (!mIsLoading)
            return;
        mIsLoading = false;
        mLoading.setVisibility(View.INVISIBLE);
        mLoading.clearAnimation();
        if (mPresentation != null) {
            mTipsBackground.setVisibility(View.VISIBLE);
        }
    }

    public void onClickOverlayTips(View v) {
        mOverlayTips.setVisibility(View.GONE);
    }

    public void onClickDismissTips(View v) {
        mOverlayTips.setVisibility(View.GONE);
        Editor editor = mSettings.edit();
        editor.putBoolean(PREF_TIPS_SHOWN, true);
        Util.commitPreferences(editor);
    }

    private void updateNavStatus() {
        mIsNavMenu = false;
        mMenuIdx = -1;

        final MediaPlayer.Title[] titles = mService.getTitles();
        if (titles != null) {
            final int currentIdx = mService.getTitleIdx();
            for (int i = 0; i < titles.length; ++i) {
                final MediaPlayer.Title title = titles[i];
                if (title.isMenu()) {
                    mMenuIdx = i;
                    break;
                }
            }
            mIsNavMenu = mMenuIdx == currentIdx;
        }

        if (mIsNavMenu) {
            /*
             * Keep the overlay hidden in order to have touch events directly
             * transmitted to navigation handling.
             */
            hideOverlay(false);
        } else if (mMenuIdx != -1)
            setESTracks();

        mNavMenu.setVisibility(mMenuIdx >= 0 ? View.VISIBLE : View.GONE);
        supportInvalidateOptionsMenu();
    }

    private GestureDetector.OnGestureListener mGestureListener = new GestureDetector.OnGestureListener() {
        @Override
        public boolean onDown(MotionEvent e) {
            return false;
        }

        @Override
        public void onShowPress(MotionEvent e) {
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            return false;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            return false;
        }

        @Override
        public void onLongPress(MotionEvent e) {
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            return false;
        }
    };

    public PlaybackServiceActivity.Helper getHelper() {
        return mHelper;
    }

    @Override
    public void onConnected(PlaybackService service) {
        mService = service;
        if (!mSwitchingView)
            mHandler.sendEmptyMessage(START_PLAYBACK);
        mSwitchingView = false;
        mSettings.edit().putBoolean(PreferencesActivity.VIDEO_RESTORE, false).apply();
    }

    @Override
    public void onDisconnected() {
        mService = null;
        mHandler.sendEmptyMessage(AUDIO_SERVICE_CONNECTION_FAILED);
    }

    @Override
    public void onNewLayout(IVLCVout vlcVout, int width, int height, int visibleWidth, int visibleHeight, int sarNum, int sarDen) {
        if (width * height == 0)
            return;

        // store video size
        mVideoWidth = width;
        mVideoHeight = height;
        mVideoVisibleWidth = visibleWidth;
        mVideoVisibleHeight = visibleHeight;
        mSarNum = sarNum;
        mSarDen = sarDen;
        changeSurfaceLayout();
    }

    @Override
    public void onSurfacesCreated(IVLCVout vlcVout) {
    }

    @Override
    public void onSurfacesDestroyed(IVLCVout vlcVout) {
    }

    @Override
    public void onHardwareAccelerationError(IVLCVout vlcVout) {
        mHandler.sendEmptyMessage(HW_ERROR);
    }

    private BroadcastReceiver mServiceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TextUtils.equals(intent.getAction(), PLAY_FROM_SERVICE))
                onNewIntent(intent);
            else if (TextUtils.equals(intent.getAction(), EXIT_PLAYER))
                exitOK();
        }
    };

    private class TorrentCallbackHandler extends Handler {
        boolean played = false;

        @Override
        public void handleMessage(Message msg) {
            Bundle b;
            TorrentStateParcel state;

            switch (msg.what) {
                case TorrentTaskServiceIPC.UPDATE_STATE: {
                    b = msg.getData();
                    b.setClassLoader(TorrentStateParcel.class.getClassLoader());
                    state = b.getParcelable(TorrentTaskServiceIPC.TAG_STATE);
                    if (state.isReadyForPlaing && !played) {
                        loadMedia();
                        played = true;
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

            if (!torrentsQueue.isEmpty()) {
                addTorrentsRequest(torrentsQueue);
                torrentsQueue.clear();
            }

            try {
                torrentIpc.sendClientConnect(torrentServiceCallback, torrentClientCallback);
            } catch (RemoteException e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            torrentServiceCallback = null;
            bound = false;
        }
    }

    private void addTorrentsRequest(Collection<Torrent> torrents) {
        if (!torrentConnection.bound || torrentServiceCallback == null) {
            torrentsQueue.addAll(torrents);
            return;
        }
        try {
            torrentIpc.sendAddTorrents(torrentServiceCallback, new ArrayList<>(torrents));
        } catch (RemoteException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

}
