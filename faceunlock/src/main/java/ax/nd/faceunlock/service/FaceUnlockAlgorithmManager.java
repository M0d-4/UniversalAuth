package ax.nd.faceunlock.service;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ax.nd.faceunlock.util.FaceUnlockAlgorithmExtractor;
import ax.nd.faceunlock.util.Util;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * FaceUnlockAlgorithmManager provides a unified interface for the complete face unlock workflow.
 * It handles initialization, feature extraction, face comparison, and cleanup.
 */
public class FaceUnlockAlgorithmManager {
    private static final String TAG = "FaceUnlockAlgorithmManager";
    private static FaceUnlockAlgorithmManager sInstance;
    
    private final Context mContext;
    private final FaceUnlockAlgorithmExtractor mExtractor;
    private final AtomicBoolean mInitializing = new AtomicBoolean(false);
    private final AtomicBoolean mInitialized = new AtomicBoolean(false);
    @Nullable
    private String mModelPath;
    @Nullable
    private String mPanoramaPath;
    @Nullable
    private String mModelFile;

    private FaceUnlockAlgorithmManager(@NonNull Context context) {
        this.mContext = context.getApplicationContext();
        this.mExtractor = new FaceUnlockAlgorithmExtractor();
    }

    /**
     * Get or create the singleton instance
     */
    public static synchronized FaceUnlockAlgorithmManager getInstance(@NonNull Context context) {
        if (sInstance == null) {
            sInstance = new FaceUnlockAlgorithmManager(context);
        }
        return sInstance;
    }

    /**
     * Initialize the face unlock algorithm with model files
     * @param modelPath Directory containing model files
     * @param panoramaPath Path to panorama model file
     * @param modelFile Path to main model file
     * @param callback Callback for initialization completion
     */
    public void initialize(
            @NonNull String modelPath,
            @NonNull String panoramaPath,
            @NonNull String modelFile,
            @Nullable InitializationCallback callback) {

        if (mInitialized.get()) {
            Log.d(TAG, "Algorithm already initialized");
            if (callback != null) {
                callback.onSuccess();
            }
            return;
        }

        if (mInitializing.getAndSet(true)) {
            Log.w(TAG, "Initialization already in progress");
            return;
        }

        this.mModelPath = modelPath;
        this.mPanoramaPath = panoramaPath;
        this.mModelFile = modelFile;

        // Run initialization on background thread
        new Thread(() -> {
            try {
                boolean success = mExtractor.initAlgorithm(modelPath, panoramaPath, modelFile);
                if (success) {
                    mInitialized.set(true);
                    Log.d(TAG, "Face unlock algorithm initialized successfully");
                    if (callback != null) {
                        callback.onSuccess();
                    }
                } else {
                    mInitializing.set(false);
                    Log.e(TAG, "Failed to initialize face unlock algorithm");
                    if (callback != null) {
                        callback.onError("Failed to initialize algorithm");
                    }
                }
            } catch (Exception e) {
                mInitializing.set(false);
                Log.e(TAG, "Error during algorithm initialization", e);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        }).start();
    }

    /**
     * Enroll a face by extracting and storing face features
     * @param imageData Image data in NV21 format
     * @param width Image width
     * @param height Image height
     * @param rotation Image rotation
     * @return Extracted face features or null if extraction failed
     */
    @Nullable
    public byte[] enrollFace(@NonNull byte[] imageData, int width, int height, int rotation) {
        if (!mInitialized.get()) {
            Log.e(TAG, "Algorithm not initialized. Call initialize first.");
            return null;
        }

        byte[] feature = new byte[com.megvii.facepp.sdk.Lite.FEATURE_SIZE];
        boolean success = mExtractor.extractFaceFeature(imageData, width, height, rotation, feature);
        
        return success ? feature : null;
    }

    /**
     * Verify a face against stored enrollment features
     * @param imageData Image data in NV21 format
     * @param width Image width
     * @param height Image height
     * @param rotation Image rotation
     * @param enrolledFeature Previously enrolled face feature
     * @param similarityThreshold Threshold for face matching (0.0 to 1.0, typically 0.6-0.8)
     * @return true if face matches enrolled feature above threshold
     */
    public boolean verifyFace(
            @NonNull byte[] imageData,
            int width,
            int height,
            int rotation,
            @NonNull byte[] enrolledFeature,
            float similarityThreshold) {

        if (!mInitialized.get()) {
            Log.e(TAG, "Algorithm not initialized. Call initialize first.");
            return false;
        }

        try {
            mExtractor.prepareForComparison();
            
            byte[] currentFeature = new byte[com.megvii.facepp.sdk.Lite.FEATURE_SIZE];
            boolean extractSuccess = mExtractor.extractFaceFeature(imageData, width, height, rotation, currentFeature);
            
            if (!extractSuccess) {
                Log.w(TAG, "Failed to extract face feature for verification");
                return false;
            }

            boolean match = mExtractor.compareFaceFeatures(enrolledFeature, currentFeature, similarityThreshold);
            
            mExtractor.reset();
            
            return match;
        } catch (Exception e) {
            Log.e(TAG, "Error during face verification", e);
            return false;
        }
    }

    /**
     * Extract face features from image data
     * @param imageData Image data in NV21 format
     * @param width Image width
     * @param height Image height
     * @param rotation Image rotation
     * @return Extracted face features or null if extraction failed
     */
    @Nullable
    public byte[] extractFeatures(@NonNull byte[] imageData, int width, int height, int rotation) {
        if (!mInitialized.get()) {
            Log.e(TAG, "Algorithm not initialized. Call initialize first.");
            return null;
        }

        byte[] feature = new byte[com.megvii.facepp.sdk.Lite.FEATURE_SIZE];
        boolean success = mExtractor.extractFaceFeature(imageData, width, height, rotation, feature);
        
        return success ? feature : null;
    }

    /**
     * Compare two face features directly
     * @param feature1 First face feature
     * @param feature2 Second face feature
     * @param threshold Similarity threshold
     * @return true if features match above threshold
     */
    public boolean compareFeatures(@NonNull byte[] feature1, @NonNull byte[] feature2, float threshold) {
        if (!mInitialized.get()) {
            Log.e(TAG, "Algorithm not initialized. Call initialize first.");
            return false;
        }

        try {
            mExtractor.prepareForComparison();
            boolean match = mExtractor.compareFaceFeatures(feature1, feature2, threshold);
            mExtractor.reset();
            return match;
        } catch (Exception e) {
            Log.e(TAG, "Error comparing features", e);
            return false;
        }
    }

    /**
     * Get algorithm version information
     */
    @Nullable
    public String getVersion() {
        if (mInitialized.get()) {
            return mExtractor.getAlgorithmVersion();
        }
        return null;
    }

    /**
     * Check if algorithm is initialized
     */
    public boolean isInitialized() {
        return mInitialized.get();
    }

    /**
     * Release all resources and cleanup
     */
    public void shutdown() {
        if (mInitialized.get()) {
            mExtractor.release();
            mInitialized.set(false);
            Log.d(TAG, "Face unlock algorithm shutdown");
        }
    }

    /**
     * Callback interface for initialization events
     */
    public interface InitializationCallback {
        /**
         * Called when initialization completes successfully
         */
        void onSuccess();

        /**
         * Called when initialization fails
         * @param error Error message
         */
        void onError(@Nullable String error);
    }

    /**
     * Callback interface for face recognition events
     */
    public interface FaceRecognitionCallback {
        /**
         * Called when face is recognized/matched
         */
        void onFaceMatched(float confidence);

        /**
         * Called when face is not recognized/matched
         */
        void onFaceNotMatched();

        /**
         * Called when an error occurs during face recognition
         * @param error Error message
         */
        void onError(@Nullable String error);
    }
}
