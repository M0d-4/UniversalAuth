package ax.nd.faceunlock.util;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import ax.nd.faceunlock.ChooseLibsViewModel;
import ax.nd.faceunlock.DownloadLibsDialog;
import ax.nd.faceunlock.LibManager;
import ax.nd.faceunlock.MainActivity;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AutoDownloadManager handles automatic download of face unlock libraries with retry logic.
 */
public class AutoDownloadManager {
    private static final String TAG = "AutoDownloadManager";
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000; // 2 seconds between retries

    private final Context mContext;
    private final ChooseLibsViewModel mViewModel;
    @Nullable
    private final MainActivity mActivity;
    private final AtomicBoolean mIsDownloading = new AtomicBoolean(false);
    private int mRetryCount = 0;

    public AutoDownloadManager(@NonNull Context context, @NonNull ChooseLibsViewModel viewModel, @Nullable MainActivity activity) {
        this.mContext = context;
        this.mViewModel = viewModel;
        this.mActivity = activity;
    }

    /**
     * Check if libraries need to be downloaded
     * @return true if any required library is missing
     */
    public boolean needsDownload() {
        return LibManager.librariesData.getValue().stream()
                .anyMatch(state -> !state.getValid());
    }

    /**
     * Start automatic download of libraries
     */
    public void autoDownload() {
        if (mIsDownloading.getAndSet(true)) {
            Log.w(TAG, "Download already in progress");
            return;
        }

        if (!needsDownload()) {
            Log.d(TAG, "All libraries already present, skipping download");
            mIsDownloading.set(false);
            return;
        }

        Log.d(TAG, "Starting automatic library download");
        mRetryCount = 0;
        performDownload();
    }

    /**
     * Start automatic download with user interaction (shows dialog)
     */
    public void autoDownloadWithDialog() {
        if (mActivity != null) {
            if (!needsDownload()) {
                Log.d(TAG, "All libraries already present");
                mActivity.checkAndAskForPermissions();
                return;
            }

            Log.d(TAG, "Starting automatic library download with dialog");
            new DownloadLibsDialog(mActivity, mViewModel).open();
        }
    }

    /**
     * Perform the actual download with retry logic
     */
    private void performDownload() {
        if (mRetryCount >= MAX_RETRIES) {
            Log.e(TAG, "Max retries reached, giving up download");
            mIsDownloading.set(false);
            onDownloadFailed("Maximum retries exceeded");
            return;
        }

        Log.d(TAG, "Attempting download (attempt " + (mRetryCount + 1) + "/" + MAX_RETRIES + ")");
        
        // Attempt to download from IPFS/CDN (no manual import)
        mViewModel.downloadLibs(mContext, null);
    }

    /**
     * Handle download completion
     */
    public void onDownloadSuccess() {
        Log.d(TAG, "Download completed successfully");
        mIsDownloading.set(false);
        if (mActivity != null) {
            mActivity.checkAndAskForPermissions();
        }
    }

    /**
     * Handle download failure with automatic retry
     */
    public void onDownloadFailed(@Nullable String errorMessage) {
        Log.w(TAG, "Download failed: " + (errorMessage != null ? errorMessage : "Unknown error"));
        
        if (mRetryCount < MAX_RETRIES) {
            mRetryCount++;
            Log.d(TAG, "Retrying in " + RETRY_DELAY_MS + "ms...");
            
            // Retry after delay
            new Thread(() -> {
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                    performDownload();
                } catch (InterruptedException e) {
                    Log.e(TAG, "Retry thread interrupted", e);
                    mIsDownloading.set(false);
                }
            }).start();
        } else {
            Log.e(TAG, "Download failed after " + MAX_RETRIES + " attempts");
            mIsDownloading.set(false);
            if (mActivity != null) {
                // Fall back to manual import
                mViewModel.setAskImport();
            }
        }
    }

    /**
     * Cancel ongoing download
     */
    public void cancelDownload() {
        mIsDownloading.set(false);
        Log.d(TAG, "Download cancelled");
    }

    /**
     * Check if download is in progress
     */
    public boolean isDownloading() {
        return mIsDownloading.get();
    }
}
