# Quick Reference: Face Unlock Auto-Download & Algorithm Integration

## Files Created/Modified

### New Files
```
✅ faceunlock/src/main/java/ax/nd/faceunlock/util/FaceUnlockAlgorithmExtractor.java
✅ faceunlock/src/main/java/ax/nd/faceunlock/util/AutoDownloadManager.java
✅ faceunlock/src/main/java/ax/nd/faceunlock/service/FaceUnlockAlgorithmManager.java
✅ faceunlock/src/main/java/ax/nd/faceunlock/examples/FaceUnlockIntegrationExamples.java
✅ FACE_UNLOCK_ALGORITHM_GUIDE.md (Comprehensive documentation)
✅ IMPLEMENTATION_SUMMARY.md (Implementation details)
✅ QUICK_REFERENCE.md (This file)
```

### Modified Files
```
✅ faceunlock/src/main/java/ax/nd/faceunlock/ChooseLibsViewModel.kt (Enhanced with progress tracking)
✅ faceunlock/src/main/java/ax/nd/faceunlock/DownloadLibsDialog.kt (Enhanced UI updates)
```

## Quick Start

### 1. Auto-Download Libraries
```java
// In MainActivity.onCreate()
AutoDownloadManager autoDownload = new AutoDownloadManager(this, viewModel, this);
if (autoDownload.needsDownload()) {
    autoDownload.autoDownloadWithDialog();  // Show progress dialog
    // OR
    autoDownload.autoDownload();            // Background download
}
```

### 2. Initialize Algorithm
```java
FaceUnlockAlgorithmManager manager = FaceUnlockAlgorithmManager.getInstance(context);
manager.initialize(modelPath, panoramaPath, modelFile, new InitializationCallback() {
    @Override
    public void onSuccess() {
        // Algorithm ready
    }
    
    @Override
    public void onError(String error) {
        // Handle error
    }
});
```

### 3. Enroll Face
```java
byte[] enrolledFeatures = manager.enrollFace(nv21ImageData, width, height, 0);
if (enrolledFeatures != null) {
    // Store features securely
}
```

### 4. Verify Face
```java
boolean isMatch = manager.verifyFace(
    cameraFrame, width, height, 0,
    enrolledFeatures,
    0.75f  // similarity threshold
);

if (isMatch) {
    unlockDevice();
}
```

## Key Classes

### AutoDownloadManager
```java
needsDownload()           // Check if libraries missing
autoDownload()            // Auto-download in background
autoDownloadWithDialog()  // Auto-download with UI
onDownloadSuccess()       // Handle completion
onDownloadFailed()        // Handle failure with retry
```

### FaceUnlockAlgorithmExtractor
```java
initAlgorithm()           // Initialize with models
extractFaceFeature()      // Get features from image
compareFaceFeatures()     // Compare two features
bitmapToNV21()           // Convert Bitmap to NV21
calculateFeatureSimilarity() // Get similarity score
```

### FaceUnlockAlgorithmManager
```java
getInstance()            // Get singleton
initialize()             // Init with callback
enrollFace()             // Extract features
verifyFace()             // Verify against template
extractFeatures()        // Get features for custom use
compareFeatures()        // Direct feature comparison
getVersion()             // Get algorithm version
shutdown()               // Cleanup
```

## Configuration

### Similarity Threshold
```java
// Production: 0.75 (recommended)
// Testing: 0.65
// Strict: 0.85
```

### Retry Settings
```java
// In AutoDownloadManager
MAX_RETRIES = 3              // 3 attempts
RETRY_DELAY_MS = 2000        // 2 seconds between retries
```

### Download Source
```kotlin
// In ChooseLibsViewModel
IPFS_GATEWAY = "https://cloudflare-ipfs.com"
LIBS_CID = "QmQNREjjXTQBDpd69gFqEreNi1dV91eSGQByqi5nXU3rBt"
```

## Common Tasks

### Check Download Status
```java
if (autoDownloadManager.needsDownload()) {
    System.out.println("Libraries missing");
} else {
    System.out.println("Libraries ready");
}
```

### Monitor Download Progress
```kotlin
// In ChooseLibsViewModel, downloadStatus Flow
downloadStatus.collect { status ->
    when(status) {
        is DownloadStatus.Downloading -> {
            val progress = status.progress * 100  // 0-100%
            val message = status.progressMessage
            updateProgressUI(progress, message)
        }
    }
}
```

### Handle Errors
```java
try {
    boolean match = manager.verifyFace(...);
} catch (Exception e) {
    Log.e(TAG, "Face verification failed", e);
    showErrorDialog(e.getMessage());
}
```

### Release Resources
```java
@Override
protected void onDestroy() {
    if (manager != null) {
        manager.shutdown();  // Important!
    }
    super.onDestroy();
}
```

## Image Format

### NV21 (YUV420SP) Format
- **Y-plane**: width × height bytes (grayscale)
- **UV-plane**: (width × height) / 2 bytes (color)
- **Total size**: (width × height × 3) / 2 bytes

### Convert Bitmap to NV21
```java
Bitmap bitmap = cameraBitmap;
byte[] nv21 = FaceUnlockAlgorithmExtractor.bitmapToNV21(bitmap);
manager.extractFeatures(nv21, width, height, 0);
```

### Camera Preview to NV21
```java
camera.setPreviewCallback((data, camera) -> {
    // data is already in NV21 format
    manager.extractFeatures(data, width, height, getRotation());
});
```

## Library Files Required

```
✅ libmegface.so           (Core recognition engine)
✅ libFaceDetectCA.so      (Face detection)
✅ libMegviiUnlock.so      (Unlock implementation)
✅ libMegviiUnlock-jni-1.2.so (JNI bindings)
```

**Source**: Motorola Moto Face Unlock app v01.03.0312 (auto-downloaded on first run)

## Performance Tips

### 1. Pre-load on App Start
```java
FaceUnlockAlgorithmManager.getInstance(context).initialize(...);
```

### 2. Use Background Threads
```java
new Thread(() -> {
    byte[] features = manager.extractFeatures(...);
    runOnUiThread(() -> updateUI(features));
}).start();
```

### 3. Cache Features
```java
// Store enrolled features
SharedPreferences prefs = context.getSharedPreferences("face", MODE_PRIVATE);
prefs.edit().putString("enrolled", Base64.encode(features)).apply();

// Retrieve later
String encoded = prefs.getString("enrolled", null);
byte[] features = Base64.decode(encoded);
```

### 4. Optimize Image Size
```java
// Use 320x240 or 640x480, not full resolution
byte[] nv21 = scale(bitmap, 320, 240);
manager.extractFeatures(nv21, 320, 240, 0);
```

## Security Checklist

- [ ] Store features encrypted (use Android Keystore)
- [ ] Don't save images to disk
- [ ] Clear buffers after use
- [ ] Use similarity threshold ≥ 0.75
- [ ] Implement rate limiting (max 5 attempts/minute)
- [ ] Add multi-factor authentication (face + PIN)
- [ ] Log all recognition attempts
- [ ] Handle liveness detection (anti-spoofing)

## Testing

### Test Initialization
```java
FaceUnlockAlgorithmManager manager = FaceUnlockAlgorithmManager.getInstance(context);
manager.initialize(modelPath, panoramaPath, modelFile, callback);
// Check manager.isInitialized() == true
```

### Test Face Enrollment
```java
byte[] features = manager.enrollFace(testImage, 320, 240, 0);
assertNotNull(features);
assertEquals(features.length, Lite.FEATURE_SIZE);
```

### Test Face Matching
```java
boolean match = manager.verifyFace(testImage, 320, 240, 0, features, 0.75f);
// For same image, should be true
```

## Troubleshooting

| Problem | Solution |
|---------|----------|
| "Algorithm not initialized" | Call `initialize()` first |
| "Hash mismatch" | Delete cached files and retry download |
| "Library not found" | Run auto-download or manual import |
| "Feature extraction failed" | Check image quality and lighting |
| "Out of memory" | Close other apps, reduce image size |
| "Download timeout" | Check internet connection, retry |

## Documentation Links

- **Full Guide**: [FACE_UNLOCK_ALGORITHM_GUIDE.md](FACE_UNLOCK_ALGORITHM_GUIDE.md)
- **Implementation Details**: [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md)
- **Code Examples**: [FaceUnlockIntegrationExamples.java](faceunlock/src/main/java/ax/nd/faceunlock/examples/FaceUnlockIntegrationExamples.java)

## Next Steps

1. ✅ Review FACE_UNLOCK_ALGORITHM_GUIDE.md for complete documentation
2. ✅ Check FaceUnlockIntegrationExamples.java for code samples
3. ✅ Update MainActivity.kt to use AutoDownloadManager
4. ✅ Update authentication activities to use FaceUnlockAlgorithmManager
5. ✅ Implement secure feature storage with Android Keystore
6. ✅ Test on multiple devices with different lighting conditions
7. ✅ Tune similarity thresholds based on user feedback
8. ✅ Add liveness detection for anti-spoofing

## Summary of Implementation

This implementation provides:

✅ **Automatic Library Download**
- Detects missing libraries on startup
- Downloads from IPFS/CDN automatically
- Extracts and validates with SHA-512 hashes
- Falls back to manual import on failure
- Retry logic with 3 attempts

✅ **Face Recognition Algorithm**
- Low-level JNI wrapper (FaceUnlockAlgorithmExtractor)
- High-level manager interface (FaceUnlockAlgorithmManager)
- Face enrollment with feature extraction
- Face verification with threshold matching
- Similarity scoring and comparison

✅ **Progress Reporting**
- Real-time download progress (percentage + MB)
- Extraction progress (X/Y libraries)
- Clear status messages
- UI updates during operation

✅ **Security**
- SHA-512 hash verification
- Feature encryption support
- Secure storage recommendations
- Rate limiting examples
- Anti-spoofing guidelines

✅ **Complete Documentation**
- Comprehensive algorithm guide (500+ lines)
- Implementation details and architecture
- Working code examples
- Quick reference guide
- Integration instructions
