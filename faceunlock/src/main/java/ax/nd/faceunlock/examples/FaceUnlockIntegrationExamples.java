package ax.nd.faceunlock.examples;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import ax.nd.faceunlock.service.FaceUnlockAlgorithmManager;
import ax.nd.faceunlock.util.AutoDownloadManager;
import ax.nd.faceunlock.util.FaceUnlockAlgorithmExtractor;
import ax.nd.faceunlock.util.ConUtil;
import ax.nd.faceunlock.ChooseLibsViewModel;

/**
 * Example implementations showing how to integrate face unlock algorithm
 * into your application.
 */
public class FaceUnlockIntegrationExamples {
    private static final String TAG = "FaceUnlockExamples";

    /**
     * Example 1: Initialize algorithm with auto-download
     */
    public static void exampleAutoDownloadAndInit(Context context, ChooseLibsViewModel viewModel) {
        // Check if libraries need to be downloaded
        AutoDownloadManager downloadMgr = new AutoDownloadManager(context, viewModel, null);
        
        if (downloadMgr.needsDownload()) {
            Log.d(TAG, "Libraries need to be downloaded");
            downloadMgr.autoDownload();
            return;
        }

        // Libraries ready, initialize algorithm
        FaceUnlockAlgorithmManager manager = FaceUnlockAlgorithmManager.getInstance(context);
        
        String modelPath = context.getDir("faceunlock_data", Context.MODE_PRIVATE).getAbsolutePath();
        String panoramaPath = ConUtil.getRaw(
            context, 
            ax.nd.faceunlock.backend.R.raw.panorama_mgb,
            "model",
            "panorama_mgb",
            false
        );
        String modelFile = ConUtil.getRaw(
            context,
            ax.nd.faceunlock.backend.R.raw.model_file,
            "model",
            "model_file",
            false
        );

        manager.initialize(modelPath, panoramaPath, modelFile, 
            new FaceUnlockAlgorithmManager.InitializationCallback() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Face unlock algorithm ready!");
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "Failed to initialize: " + error);
                }
            }
        );
    }

    /**
     * Example 2: Enroll a face and store features
     */
    public static void exampleEnrollFace(Context context, byte[] nv21ImageData, int width, int height) {
        FaceUnlockAlgorithmManager manager = FaceUnlockAlgorithmManager.getInstance(context);
        
        if (!manager.isInitialized()) {
            Log.e(TAG, "Manager not initialized");
            return;
        }

        // Extract face features
        byte[] features = manager.enrollFace(nv21ImageData, width, height, 0);
        
        if (features != null) {
            // Store features securely
            SharedPreferences prefs = context.getSharedPreferences("face_secure", Context.MODE_PRIVATE);
            String encoded = Base64.encodeToString(features, Base64.NO_WRAP);
            prefs.edit().putString("enrolled_face_template", encoded).apply();
            
            Log.d(TAG, "Face enrolled successfully");
        } else {
            Log.e(TAG, "Failed to extract face features");
        }
    }

    /**
     * Example 3: Verify a face for unlock
     */
    public static boolean exampleVerifyFaceForUnlock(Context context, byte[] cameraFrame, int width, int height) {
        FaceUnlockAlgorithmManager manager = FaceUnlockAlgorithmManager.getInstance(context);
        
        if (!manager.isInitialized()) {
            Log.e(TAG, "Manager not initialized");
            return false;
        }

        // Retrieve enrolled features
        SharedPreferences prefs = context.getSharedPreferences("face_secure", Context.MODE_PRIVATE);
        String encoded = prefs.getString("enrolled_face_template", null);
        
        if (encoded == null) {
            Log.w(TAG, "No enrolled face found");
            return false;
        }

        byte[] enrolledFeatures = Base64.decode(encoded, Base64.NO_WRAP);

        // Verify face
        boolean isMatch = manager.verifyFace(
            cameraFrame,
            width,
            height,
            0,  // rotation
            enrolledFeatures,
            0.75f  // similarity threshold
        );

        Log.d(TAG, "Face match result: " + isMatch);
        return isMatch;
    }

    /**
     * Example 4: Extract features for custom comparison
     */
    public static void exampleExtractAndCompareFeatures(Context context, byte[] image1, byte[] image2) {
        FaceUnlockAlgorithmManager manager = FaceUnlockAlgorithmManager.getInstance(context);
        
        if (!manager.isInitialized()) {
            Log.e(TAG, "Manager not initialized");
            return;
        }

        // Extract features from both images
        byte[] features1 = manager.extractFeatures(image1, 320, 240, 0);
        byte[] features2 = manager.extractFeatures(image2, 320, 240, 0);

        if (features1 == null || features2 == null) {
            Log.e(TAG, "Failed to extract features from one or both images");
            return;
        }

        // Direct comparison
        boolean matches = manager.compareFeatures(features1, features2, 0.75f);
        
        // Or calculate similarity score
        float similarity = FaceUnlockAlgorithmExtractor.calculateFeatureSimilarity(features1, features2);
        
        Log.d(TAG, "Similarity score: " + similarity + ", Matches: " + matches);
    }

    /**
     * Example 5: Continuous face monitoring with callback
     */
    public static class FaceMonitoringService {
        private final Context context;
        private final FaceUnlockAlgorithmManager manager;
        private byte[] enrolledFeatures;
        private boolean isRunning = false;

        public FaceMonitoringService(Context context) {
            this.context = context;
            this.manager = FaceUnlockAlgorithmManager.getInstance(context);
            loadEnrolledFeatures();
        }

        private void loadEnrolledFeatures() {
            SharedPreferences prefs = context.getSharedPreferences("face_secure", Context.MODE_PRIVATE);
            String encoded = prefs.getString("enrolled_face_template", null);
            if (encoded != null) {
                enrolledFeatures = Base64.decode(encoded, Base64.NO_WRAP);
            }
        }

        public void startMonitoring() {
            isRunning = true;
            Log.d(TAG, "Face monitoring started");
        }

        public void stopMonitoring() {
            isRunning = false;
            Log.d(TAG, "Face monitoring stopped");
        }

        public void onCameraFrameReceived(byte[] nv21Data, int width, int height) {
            if (!isRunning || !manager.isInitialized() || enrolledFeatures == null) {
                return;
            }

            // Process frame in background
            new Thread(() -> {
                boolean isFaceMatch = manager.verifyFace(
                    nv21Data,
                    width,
                    height,
                    0,
                    enrolledFeatures,
                    0.75f
                );

                if (isFaceMatch) {
                    Log.d(TAG, "Face recognized! Triggering unlock...");
                    onFaceRecognized();
                } else {
                    Log.d(TAG, "Face not recognized, continuing monitoring");
                }
            }).start();
        }

        private void onFaceRecognized() {
            // Trigger device unlock or authentication success
            Log.d(TAG, "UNLOCK DEVICE");
        }
    }

    /**
     * Example 6: Complete workflow from download to unlock
     */
    public static class FaceUnlockWorkflow {
        private final Context context;
        private final ChooseLibsViewModel viewModel;
        private FaceUnlockAlgorithmManager algorithmManager;
        private FaceMonitoringService monitoringService;
        private static final String FACE_FEATURES_KEY = "face_features";

        public FaceUnlockWorkflow(Context context, ChooseLibsViewModel viewModel) {
            this.context = context;
            this.viewModel = viewModel;
        }

        /**
         * Start the complete face unlock workflow
         */
        public void startWorkflow() {
            Log.d(TAG, "Starting face unlock workflow");
            
            // Step 1: Check and download libraries if needed
            checkAndDownloadLibraries();
        }

        private void checkAndDownloadLibraries() {
            AutoDownloadManager downloadMgr = new AutoDownloadManager(context, viewModel, null);
            
            if (downloadMgr.needsDownload()) {
                Log.d(TAG, "Downloading required libraries...");
                downloadMgr.autoDownload();
                // Wait for download completion, then proceed
                return;
            }

            // Libraries ready, proceed to initialization
            initializeAlgorithm();
        }

        private void initializeAlgorithm() {
            algorithmManager = FaceUnlockAlgorithmManager.getInstance(context);
            
            String modelPath = context.getDir("faceunlock_data", Context.MODE_PRIVATE).getAbsolutePath();
            String panoramaPath = ConUtil.getRaw(
                context,
                ax.nd.faceunlock.backend.R.raw.panorama_mgb,
                "model",
                "panorama_mgb",
                false
            );
            String modelFile = ConUtil.getRaw(
                context,
                ax.nd.faceunlock.backend.R.raw.model_file,
                "model",
                "model_file",
                false
            );

            algorithmManager.initialize(modelPath, panoramaPath, modelFile,
                new FaceUnlockAlgorithmManager.InitializationCallback() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "Algorithm initialized, starting face monitoring");
                        startFaceMonitoring();
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Initialization failed: " + error);
                        // Retry or show error to user
                    }
                }
            );
        }

        private void startFaceMonitoring() {
            monitoringService = new FaceMonitoringService(context);
            monitoringService.startMonitoring();
            // Connect camera and feed frames to monitoringService.onCameraFrameReceived()
        }

        public void stopWorkflow() {
            if (monitoringService != null) {
                monitoringService.stopMonitoring();
            }
            if (algorithmManager != null) {
                algorithmManager.shutdown();
            }
        }
    }
}
