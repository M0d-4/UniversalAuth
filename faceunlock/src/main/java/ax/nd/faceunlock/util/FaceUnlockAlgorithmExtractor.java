package ax.nd.faceunlock.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ax.nd.faceunlock.backend.FaceUnlockVendorImpl;
import com.megvii.facepp.sdk.Lite;

import java.util.Arrays;
import java.util.Objects;

/**
 * FaceUnlockAlgorithmExtractor provides utilities to extract and use the face unlock algorithm.
 * This class encapsulates the FacePP Megvii face recognition engine operations.
 */
public class FaceUnlockAlgorithmExtractor {
    private static final String TAG = "FaceUnlockAlgorithmExtractor";
    
    private Lite mFaceEngine;
    private boolean mInitialized = false;
    private boolean mCompareStarted = false;

    /**
     * Initialize the face recognition algorithm
     * @param modelPath Path to the model files directory
     * @param panoramaPath Path to the panorama model file
     * @param modelFile Path to the model file
     * @return true if initialization was successful
     */
    public boolean initAlgorithm(@NonNull String modelPath, @NonNull String panoramaPath, @NonNull String modelFile) {
        try {
            mFaceEngine = FaceUnlockVendorImpl.getInstance();
            mFaceEngine.initHandle(modelPath);
            
            int result = mFaceEngine.initAllWithPath(panoramaPath, "", modelFile);
            if (result == 0) {
                mInitialized = true;
                Log.d(TAG, "Face unlock algorithm initialized successfully");
                return true;
            } else {
                Log.e(TAG, "Failed to initialize face unlock algorithm: " + result);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing face unlock algorithm", e);
            return false;
        }
    }

    /**
     * Prepare the algorithm for face comparison
     */
    public void prepareForComparison() {
        if (!mInitialized) {
            throw new IllegalStateException("Algorithm not initialized. Call initAlgorithm first.");
        }
        try {
            mFaceEngine.prepare();
            mCompareStarted = true;
            Log.d(TAG, "Algorithm prepared for comparison");
        } catch (Exception e) {
            Log.e(TAG, "Error preparing algorithm for comparison", e);
            mCompareStarted = false;
        }
    }

    /**
     * Extract face features from image data
     * @param imageData Image data in NV21 format
     * @param width Image width
     * @param height Image height
     * @param rotation Image rotation (0, 90, 180, 270)
     * @param outFeature Output array for face features (size: Lite.FEATURE_SIZE)
     * @return true if feature extraction was successful
     */
    public boolean extractFaceFeature(@NonNull byte[] imageData, int width, int height, int rotation, @NonNull byte[] outFeature) {
        if (!mInitialized) {
            throw new IllegalStateException("Algorithm not initialized. Call initAlgorithm first.");
        }
        if (outFeature.length < Lite.FEATURE_SIZE) {
            throw new IllegalArgumentException("Output feature array too small. Expected at least " + Lite.FEATURE_SIZE + ", got " + outFeature.length);
        }
        
        try {
            int result = mFaceEngine.getFeature(imageData, width, height, rotation, outFeature);
            if (result == 0) {
                Log.d(TAG, "Face feature extracted successfully");
                return true;
            } else {
                Log.w(TAG, "Failed to extract face feature. Result: " + result);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting face feature", e);
            return false;
        }
    }

    /**
     * Compare two face features
     * @param feature1 First face feature array
     * @param feature2 Second face feature array
     * @param threshold Similarity threshold (typically 0.6 to 0.9)
     * @return true if faces are similar above threshold
     */
    public boolean compareFaceFeatures(@NonNull byte[] feature1, @NonNull byte[] feature2, float threshold) {
        if (!mInitialized) {
            throw new IllegalStateException("Algorithm not initialized. Call initAlgorithm first.");
        }
        if (!mCompareStarted) {
            throw new IllegalStateException("Algorithm not prepared for comparison. Call prepareForComparison first.");
        }
        
        try {
            // Create output array for similarity score
            int[] similarity = new int[1];
            
            // Compare features (width, height, rotation are dummy values for feature comparison)
            int result = mFaceEngine.compare(feature1, feature1.length, feature2.length, 0, false, true, similarity);
            
            if (result == 0 && similarity[0] > 0) {
                float score = similarity[0] / 10000.0f;
                boolean match = score >= threshold;
                Log.d(TAG, "Face comparison result: similarity=" + score + ", threshold=" + threshold + ", match=" + match);
                return match;
            } else {
                Log.w(TAG, "Failed to compare face features. Result: " + result);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error comparing face features", e);
            return false;
        }
    }

    /**
     * Reset the algorithm state
     */
    public void reset() {
        if (mInitialized && mCompareStarted) {
            try {
                mFaceEngine.reset();
                mCompareStarted = false;
                Log.d(TAG, "Algorithm reset");
            } catch (Exception e) {
                Log.e(TAG, "Error resetting algorithm", e);
            }
        }
    }

    /**
     * Release all resources allocated by the algorithm
     */
    public void release() {
        if (mInitialized) {
            try {
                if (mCompareStarted) {
                    reset();
                }
                mFaceEngine.release();
                mInitialized = false;
                Log.d(TAG, "Algorithm released");
            } catch (Exception e) {
                Log.e(TAG, "Error releasing algorithm", e);
            }
        }
    }

    /**
     * Check if algorithm is initialized
     */
    public boolean isInitialized() {
        return mInitialized;
    }

    /**
     * Get the face engine instance (for advanced operations)
     */
    @Nullable
    public Lite getFaceEngine() {
        return mInitialized ? mFaceEngine : null;
    }

    /**
     * Convert Android Bitmap to NV21 format (for face detection)
     * @param bitmap Bitmap to convert
     * @return NV21 byte array
     */
    public static byte[] bitmapToNV21(@NonNull Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        byte[] nv21 = new byte[width * height * 3 / 2];
        int ySize = width * height;
        int uvSize = width * height / 4;

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int index = i * width + j;
                int pixel = pixels[index];
                
                int r = (pixel >> 16) & 0xff;
                int g = (pixel >> 8) & 0xff;
                int b = pixel & 0xff;
                
                // Standard YUV conversion formula
                int y = (66 * r + 129 * g + 25 * b + 128) >> 8;
                int u = (-38 * r - 74 * g + 112 * b + 128) >> 8;
                int v = (112 * r - 94 * g - 18 * b + 128) >> 8;
                
                nv21[index] = (byte) Math.max(0, Math.min(255, y + 16));
            }
        }

        // Fill UV planes
        for (int i = 0; i < height / 2; i++) {
            for (int j = 0; j < width / 2; j++) {
                int pixelIndex = (i * 2) * width + (j * 2);
                int pixel = pixels[pixelIndex];
                
                int r = (pixel >> 16) & 0xff;
                int g = (pixel >> 8) & 0xff;
                int b = pixel & 0xff;
                
                int u = (-38 * r - 74 * g + 112 * b + 128) >> 8;
                int v = (112 * r - 94 * g - 18 * b + 128) >> 8;
                
                int uvIndex = ySize + i * width / 2 + j;
                nv21[uvIndex] = (byte) Math.max(0, Math.min(255, v + 128));
                nv21[uvIndex + uvSize] = (byte) Math.max(0, Math.min(255, u + 128));
            }
        }

        return nv21;
    }

    /**
     * Calculate similarity score between two features (0.0 to 1.0)
     * @param feature1 First face feature array
     * @param feature2 Second face feature array
     * @return Similarity score
     */
    public static float calculateFeatureSimilarity(@NonNull byte[] feature1, @NonNull byte[] feature2) {
        if (feature1.length != feature2.length) {
            throw new IllegalArgumentException("Features must have the same length");
        }

        long sumSquares = 0;
        for (int i = 0; i < feature1.length; i++) {
            int diff = (feature1[i] & 0xFF) - (feature2[i] & 0xFF);
            sumSquares += (long) diff * diff;
        }

        double euclideanDistance = Math.sqrt(sumSquares);
        // Normalize distance to similarity (0.0 to 1.0)
        // Using sigmoid-like function
        return (float) (1.0 / (1.0 + euclideanDistance / 1000.0));
    }

    /**
     * Get algorithm version information
     */
    @Nullable
    public String getAlgorithmVersion() {
        if (mInitialized && mFaceEngine != null) {
            try {
                return mFaceEngine.getVersion();
            } catch (Exception e) {
                Log.e(TAG, "Error getting algorithm version", e);
            }
        }
        return null;
    }
}
