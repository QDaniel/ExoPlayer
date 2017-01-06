package com.example.rahulkumarpandey.mp3exoplayer;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.drm.FrameworkMediaDrm;
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback;
import com.google.android.exoplayer2.drm.StreamingDrmSessionManager;
import com.google.android.exoplayer2.drm.UnsupportedDrmException;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveVideoTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelections;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.DebugTextViewHelper;
import com.google.android.exoplayer2.ui.PlaybackControlView;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Util;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Created by Rahul Kumar Pandey on 16-11-2016.
 */

public class ExoPlayerActivity extends AppCompatActivity implements ExoPlayer.EventListener, View.OnClickListener,
        PlaybackControlView.VisibilityListener, TrackSelector.EventListener<MappingTrackSelector.MappedTrackInfo> {

    // private String url = "http://storage.googleapis.com/exoplayer-test-media-0/play.mp3";
    // private String url = "https://devimages.apple.com.edgekey.net/streaming/examples/bipbop_4x3/gear1/prog_index.m3u8";
    private String url = "http://ndtvstream-lh.akamaihd.net/i/ndtv_24x7_1@300633/master.m3u8";
    private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();
    private static final CookieManager DEFAULT_COOKIE_MANAGER;

    static {
        DEFAULT_COOKIE_MANAGER = new CookieManager();
        DEFAULT_COOKIE_MANAGER.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    }

    private Handler mMainHandler;
    private Timeline.Window mWindow;
    private EventLogger mEventLogger;
    private SimpleExoPlayerView mSimpleExoPlayerView;
    private LinearLayout mDebugRootView;
    private TextView mDebugTextView;
    private Button mRetryButton;
    private DataSource.Factory mMediaDataSourceFactory;
    private SimpleExoPlayer mSimpleExoPlayer;
    private MappingTrackSelector mMappingTrackSelector;
    private DebugTextViewHelper mDebugTextViewHelper;
    private boolean mPlayerNeedsSource;
    private boolean mShouldAutoPlay;
    private boolean mIsTimelineStatic;
    private int mPlayerWindow;
    private long mPlayerPosition;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mShouldAutoPlay = true;
        mMediaDataSourceFactory = buildDataSourceFactory(true);
        mMainHandler = new Handler();
        mWindow = new Timeline.Window();
        if (CookieHandler.getDefault() != DEFAULT_COOKIE_MANAGER) {
            CookieHandler.setDefault(DEFAULT_COOKIE_MANAGER);
        }

        setContentView(R.layout.activity_exo_player);
        initView();
    }

    private void initView() {
        View rootView = findViewById(R.id.root);
        rootView.setOnClickListener(this);
        mDebugRootView = (LinearLayout) findViewById(R.id.controls_root);
        mDebugTextView = (TextView) findViewById(R.id.debug_text_view);
        mRetryButton = (Button) findViewById(R.id.retry_button);
        mRetryButton.setOnClickListener(this);
        mSimpleExoPlayerView = (SimpleExoPlayerView) findViewById(R.id.player_view);
        mSimpleExoPlayerView.setControllerVisibilityListener(this);
        mSimpleExoPlayerView.requestFocus();
    }

    // Entry point for single top activity , which already run somewhere else in the stack and therefore can't call onCreate
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        releasePlayer();
        mIsTimelineStatic = false;
        setIntent(intent);
    }

    private void releasePlayer() {
        if (mSimpleExoPlayer != null) {
            mDebugTextViewHelper.stop();
            mDebugTextViewHelper = null;
            mShouldAutoPlay = mSimpleExoPlayer.getPlayWhenReady();
            mPlayerWindow = mSimpleExoPlayer.getCurrentWindowIndex();
            mPlayerPosition = C.TIME_UNSET;
            Timeline timeline = mSimpleExoPlayer.getCurrentTimeline();
            if (timeline != null && timeline.getWindow(mPlayerWindow, mWindow).isSeekable) {
                mPlayerPosition = mSimpleExoPlayer.getCurrentPosition();
            }
            mSimpleExoPlayer.release();
            mSimpleExoPlayer = null;
            mMappingTrackSelector = null;
            // trackSelectionHelper = null;
            mEventLogger = null;
        }
    }


    @Override
    public void onStart() {
        super.onStart();
        if (Util.SDK_INT > 23) {
            initializePlayer();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if ((Util.SDK_INT <= 23 || mSimpleExoPlayer == null)) {
            initializePlayer();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (Util.SDK_INT <= 23) {
            releasePlayer();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (Util.SDK_INT > 23) {
            releasePlayer();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initializePlayer();
        } else {
            showToast(R.string.storage_permission_denied);
            finish();
        }
    }

    private void initializePlayer() {
        if (mSimpleExoPlayer == null) {
            // if u want to give DRMSeesionManager while initializing mSimpleExoPlayer , then u must have scheme uid and license url of video
            UUID drmSchemeUuid = null;
            DrmSessionManager<FrameworkMediaCrypto> drmSessionManager = null;
            if (drmSchemeUuid != null) {
                String drmLicenseUrl = null;
                String[] keyRequestPropertiesArray = null;
                Map<String, String> keyRequestProperties;
                if (keyRequestPropertiesArray == null || keyRequestPropertiesArray.length < 2) {
                    keyRequestProperties = null;
                } else {
                    keyRequestProperties = new HashMap<>();
                    for (int i = 0; i < keyRequestPropertiesArray.length - 1; i += 2) {
                        keyRequestProperties.put(keyRequestPropertiesArray[i],
                                keyRequestPropertiesArray[i + 1]);
                    }
                }
                try {
                    drmSessionManager = buildDrmSessionManager(drmSchemeUuid, drmLicenseUrl,
                            keyRequestProperties);
                } catch (UnsupportedDrmException e) {
                    int errorStringId = Util.SDK_INT < 18 ? R.string.error_drm_not_supported
                            : (e.reason == UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME
                            ? R.string.error_drm_unsupported_scheme : R.string.error_drm_unknown);
                    showToast(errorStringId);
                    return;
                }
            }
            mEventLogger = new EventLogger();
            TrackSelection.Factory videoTrackSelectionFactory =
                    new AdaptiveVideoTrackSelection.Factory(BANDWIDTH_METER);
            mMappingTrackSelector = new DefaultTrackSelector(mMainHandler, videoTrackSelectionFactory);
            mMappingTrackSelector.addListener(this);
            mMappingTrackSelector.addListener(mEventLogger);
            // trackSelectionHelper = new TrackSelectionHelper(mMappingTrackSelector, videoTrackSelectionFactory);
            mSimpleExoPlayer = ExoPlayerFactory.newSimpleInstance(this, mMappingTrackSelector, new DefaultLoadControl(),
                    null, false);
            mSimpleExoPlayer.addListener(this);
            mSimpleExoPlayer.addListener(mEventLogger);
            mSimpleExoPlayer.setAudioDebugListener(mEventLogger);
            mSimpleExoPlayer.setVideoDebugListener(mEventLogger);
            mSimpleExoPlayer.setId3Output(mEventLogger);
            mSimpleExoPlayerView.setPlayer(mSimpleExoPlayer);
            if (mIsTimelineStatic) {
                if (mPlayerPosition == C.TIME_UNSET) {
                    mSimpleExoPlayer.seekToDefaultPosition(mPlayerWindow);
                } else {
                    mSimpleExoPlayer.seekTo(mPlayerWindow, mPlayerPosition);
                }
            }
            mSimpleExoPlayer.setPlayWhenReady(mShouldAutoPlay);
            mDebugTextViewHelper = new DebugTextViewHelper(mSimpleExoPlayer, mDebugTextView);
            mDebugTextViewHelper.start();
            mPlayerNeedsSource = true;
        }
        if (mPlayerNeedsSource) {
            Uri uris = Uri.parse(url);
            String extensions = null;
            MediaSource mediaSource = buildMediaSource(uris, extensions);
            mSimpleExoPlayer.prepare(mediaSource, !mIsTimelineStatic, !mIsTimelineStatic);
            mPlayerNeedsSource = false;
            updateButtonVisibilities();
        }
    }

    private DrmSessionManager<FrameworkMediaCrypto> buildDrmSessionManager(UUID uuid,
                                                                           String licenseUrl, Map<String, String> keyRequestProperties) throws UnsupportedDrmException {
        if (Util.SDK_INT < 18) {
            return null;
        }
        HttpMediaDrmCallback drmCallback = new HttpMediaDrmCallback(licenseUrl,
                buildHttpDataSourceFactory(false), keyRequestProperties);
        return new StreamingDrmSessionManager<>(uuid,
                FrameworkMediaDrm.newInstance(uuid), drmCallback, null, mMainHandler, mEventLogger);
    }

    /**
     * Returns a new HttpDataSource factory.
     *
     * @param useBandwidthMeter Whether to set {@link #BANDWIDTH_METER} as a listener to the new
     *     DataSource factory.
     * @return A new HttpDataSource factory.
     */
    private HttpDataSource.Factory buildHttpDataSourceFactory(boolean useBandwidthMeter) {
        return ((DemoApplication) getApplication())
                .buildHttpDataSourceFactory(useBandwidthMeter ? BANDWIDTH_METER : null);
    }

    private MediaSource buildMediaSource(Uri uri, String extension) {
        int type = Util.inferContentType(!TextUtils.isEmpty(extension) ? "." + extension
                : uri.getLastPathSegment());
        switch (type) {
            case C.TYPE_SS:
                return new SsMediaSource(uri, buildDataSourceFactory(false),
                        new DefaultSsChunkSource.Factory(mMediaDataSourceFactory), mMainHandler, mEventLogger);
            case C.TYPE_DASH:
                return new DashMediaSource(uri, buildDataSourceFactory(false),
                        new DefaultDashChunkSource.Factory(mMediaDataSourceFactory), mMainHandler, mEventLogger);
            case C.TYPE_HLS:
                return new HlsMediaSource(uri, mMediaDataSourceFactory, mMainHandler, mEventLogger);
            case C.TYPE_OTHER:
                return new ExtractorMediaSource(uri, mMediaDataSourceFactory, new DefaultExtractorsFactory(),
                        mMainHandler, mEventLogger);
            default: {
                throw new IllegalStateException("Unsupported type: " + type);
            }
        }
    }

    /**
     * Returns a new DataSource factory.
     *
     * @param useBandwidthMeter Whether to set {@link #BANDWIDTH_METER} as a listener to the new
     *                          DataSource factory.
     * @return A new DataSource factory.
     */
    private DataSource.Factory buildDataSourceFactory(boolean useBandwidthMeter) {
        return ((DemoApplication) getApplication())
                .buildDataSourceFactory(useBandwidthMeter ? BANDWIDTH_METER : null);
    }

    private void showControls() {
        mDebugRootView.setVisibility(View.VISIBLE);
    }

    private void updateButtonVisibilities() {
        mDebugRootView.removeAllViews();

        mRetryButton.setVisibility(mPlayerNeedsSource ? View.VISIBLE : View.GONE);
        mDebugRootView.addView(mRetryButton);

        if (mSimpleExoPlayer == null) {
            return;
        }

        TrackSelections<MappingTrackSelector.MappedTrackInfo> trackSelections = mMappingTrackSelector.getCurrentSelections();
        if (trackSelections == null) {
            return;
        }

        int rendererCount = trackSelections.length;
        for (int i = 0; i < rendererCount; i++) {
            TrackGroupArray trackGroups = trackSelections.info.getTrackGroups(i);
        }
    }

    // ExoPlayer.EventListener call back
    @Override
    public void onLoadingChanged(boolean isLoading) {

    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        if (playbackState == ExoPlayer.STATE_ENDED) {
            showControls();
        }
        updateButtonVisibilities();

    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {
        mIsTimelineStatic = timeline != null && timeline.getWindowCount() > 0
                && !timeline.getWindow(timeline.getWindowCount() - 1, mWindow).isDynamic;
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        String errorString = null;
        if (error.type == ExoPlaybackException.TYPE_RENDERER) {
            Exception cause = error.getRendererException();
            if (cause instanceof MediaCodecRenderer.DecoderInitializationException) {
                // Special case for decoder initialization failures.
                MediaCodecRenderer.DecoderInitializationException decoderInitializationException =
                        (MediaCodecRenderer.DecoderInitializationException) cause;
                if (decoderInitializationException.decoderName == null) {
                    if (decoderInitializationException.getCause() instanceof MediaCodecUtil.DecoderQueryException) {
                        errorString = getString(R.string.error_querying_decoders);
                    } else if (decoderInitializationException.secureDecoderRequired) {
                        errorString = getString(R.string.error_no_secure_decoder,
                                decoderInitializationException.mimeType);
                    } else {
                        errorString = getString(R.string.error_no_decoder,
                                decoderInitializationException.mimeType);
                    }
                } else {
                    errorString = getString(R.string.error_instantiating_decoder,
                            decoderInitializationException.decoderName);
                }
            }
        }
        if (errorString != null) {
            showToast(errorString);
        }
        mPlayerNeedsSource = true;
        updateButtonVisibilities();
        showControls();

    }

    @Override
    public void onPositionDiscontinuity() {

    }

    @Override
    public void onClick(View view) {

        if (view == mRetryButton) {
            initializePlayer();
        } else if (view.getParent() == mDebugRootView) {
            // trackSelectionHelper.showSelectionDialog(this, ((Button) view).getText(),
            // mMappingTrackSelector.getCurrentSelections().info, (int) view.getTag());
        }
    }

    @Override
    public void onVisibilityChange(int visibility) {
        mDebugRootView.setVisibility(visibility);
    }

    @Override
    public void onTrackSelectionsChanged(TrackSelections<? extends MappingTrackSelector.MappedTrackInfo> trackSelections) {
        updateButtonVisibilities();
        MappingTrackSelector.MappedTrackInfo trackInfo = trackSelections.info;
        if (trackInfo.hasOnlyUnplayableTracks(C.TRACK_TYPE_VIDEO)) {
            showToast(R.string.error_unsupported_video);
        }
        if (trackInfo.hasOnlyUnplayableTracks(C.TRACK_TYPE_AUDIO)) {
            showToast(R.string.error_unsupported_audio);
        }
    }

    private void showToast(int messageId) {
        showToast(getString(messageId));
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }
}
