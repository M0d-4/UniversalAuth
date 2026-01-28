# Face Unlock Algorithm Integration Guide

## Overview

This document provides comprehensive information about the face unlock algorithm implementation in UniversalAuth and how to use the extracted face recognition capabilities.

## Architecture

The face unlock system consists of several key components:

### 1. **FaceUnlockAlgorithmExtractor** (`FaceUnlockAlgorithmExtractor.java`)
Low-level wrapper around the Megvii FacePP SDK. Handles:
- Algorithm initialization with model files
- Face feature extraction from image data
- Face comparison and matching
- Resource management and cleanup

### 2. **FaceUnlockAlgorithmManager** (`FaceUnlockAlgorithmManager.java`)
High-level manager providing a unified interface for the complete workflow:
- Singleton pattern for single instance management
- Easy initialization with callbacks
- Face enrollment and verification
- Feature extraction and comparison
- Version information and status checks

### 3. **AutoDownloadManager** (`AutoDownloadManager.java`)
Handles automatic download of required libraries with:
- Automatic detection of missing libraries
- Retry logic with exponential backoff
- Progress reporting
- Fallback to manual import

### 4. **ChooseLibsViewModel** (Enhanced)
Updated with:
- Progress tracking during downloads
- Stream-based download progress reporting
- Better error messages

## Library Files

The face unlock algorithm requires the following native libraries extracted from Moto Face Unlock APK:

- `libmegface.so` - Core face recognition engine
- `libFaceDetectCA.so` - Face detection component
- `libMegviiUnlock.so` - Face unlock specific implementation
- `libMegviiUnlock-jni-1.2.so` - JNI bindings

**Note:** These libraries are proprietary and must be extracted from Motorola's official Moto Face Unlock app (v01.03.0312).

## Auto-Download Implementation

### Automatic Download Flow

```
1. App starts
2. Check if all libraries exist → LibManager.needsDownload()
3. If missing:
   a. Start auto-download from IPFS/CDN
   b. Extract .so files from APK
   c. Verify SHA-512 hashes
   d. Load libraries into memory
4. Proceed with face recognition
```

### Implementing Auto-Download

#### In MainActivity.kt:

```kotlin
// Initialize auto-download on app start
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Check if download is needed
    val autoDownloadMgr = AutoDownloadManager(this, chooseLibsViewModel, this)
    
    if (autoDownloadMgr.needsDownload()) {
        // Start download with UI feedback
        autoDownloadMgr.autoDownloadWithDialog()
    } else {
        // Libraries ready, proceed
        checkAndAskForPermissions()
    }
}

// Listen for download completion
DownloadLibsDialog(this, chooseLibsViewModel).open()
```

#### In Custom Components:

```kotlin
// Programmatic auto-download
val autoDownload = AutoDownloadManager(context, viewModel, activity)
autoDownload.autoDownload()

// Check status
if (autoDownload.isDownloading()) {
    // Show progress UI
}
```

## Face Unlock Algorithm Usage

### Basic Initialization

```java
// Get algorithm manager
FaceUnlockAlgorithmManager manager = FaceUnlockAlgorithmManager.getInstance(context);

// Initialize with model files
manager.initialize(
    modelPath,           // Directory with model files
    panoramaPath,        // Panorama model file path
    modelFile,           // Main model file path
    new FaceUnlockAlgorithmManager.InitializationCallback() {
        @Override
        public void onSuccess() {
            Log.d(TAG, "Algorithm ready for use");
        }

        @Override
        public void onError(String error) {
            Log.e(TAG, "Initialization failed: " + error);
        }
    }
);
```

### Face Enrollment

```java
// Extract face features during enrollment
byte[] imageData = getCameraFrameAsNV21();
byte[] faceFeatures = manager.enrollFace(
    imageData,
    width,      // Image width
    height,     // Image height
    rotation    // 0, 90, 180, or 270
);

if (faceFeatures != null) {
    // Store features securely
    SharedPreferences prefs = context.getSharedPreferences("face_data", Context.MODE_PRIVATE);
    String encodedFeatures = Base64.getEncoder().encodeToString(faceFeatures);
    prefs.edit().putString("enrolled_face", encodedFeatures).apply();
    Log.d(TAG, "Face enrolled successfully");
} else {
    Log.e(TAG, "Failed to extract face features");
}
```

### Face Verification

```java
// Verify a face during unlock
byte[] enrolledFeatures = retrieveEnrolledFeatures();
byte[] cameraFrame = getCameraFrameAsNV21();

boolean isFaceMatch = manager.verifyFace(
    cameraFrame,
    width,              // Image width
    height,             // Image height
    rotation,           // Image rotation
    enrolledFeatures,
    0.75f              // Similarity threshold (0.0-1.0)
);

if (isFaceMatch) {
    // Face matched, unlock device
    unlockDevice();
} else {
    // Face not matched, continue monitoring
    continueFaceDetection();
}
```

### Direct Feature Extraction

```java
// Extract features from camera frame
byte[] features = manager.extractFeatures(
    nv21ImageData,
    width,
    height,
    rotation
);

if (features != null) {
    // Use features for custom processing
    float similarity = FaceUnlockAlgorithmExtractor.calculateFeatureSimilarity(
        features,
        referenceFeatures
    );
}
```

### Direct Feature Comparison

```java
// Compare two feature sets
byte[] feature1 = ...;
byte[] feature2 = ...;

boolean matches = manager.compareFeatures(
    feature1,
    feature2,
    0.70f  // Similarity threshold
);
```

## Image Format Requirements

### NV21 Format

The face recognition algorithm expects image data in **NV21 format** (also known as YUV420SP):

- **Y-plane**: Luminance data (grayscale)
  - Size: width × height bytes
  - Each byte represents brightness

- **UV-plane**: Chrominance data (color)
  - Size: (width × height) / 2 bytes
  - Interleaved U and V components

#### Converting Bitmap to NV21:

```java
Bitmap bitmap = getBitmapFromCamera();
byte[] nv21Data = FaceUnlockAlgorithmExtractor.bitmapToNV21(bitmap);

// Use nv21Data with face recognition
manager.extractFeatures(nv21Data, bitmap.getWidth(), bitmap.getHeight(), 0);
```

#### Converting Camera YUV to NV21:

```java
Camera.PreviewCallback callback = new Camera.PreviewCallback() {
    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        // Camera provides YUV420SP (NV21) by default
        manager.extractFeatures(data, width, height, getImageRotation());
    }
};
```

## Algorithm Configuration

### Similarity Thresholds

The algorithm uses similarity scores from 0.0 to 1.0:

| Threshold | Security | False Positive Rate | Notes |
|-----------|----------|-------------------|-------|
| 0.50      | Low      | High               | Very permissive, not recommended |
| 0.65      | Medium   | Moderate           | Good for testing |
| 0.75      | High     | Low                | Recommended for production |
| 0.85      | Very High| Very Low           | Strict, may reject similar faces |
| 0.95      | Extreme  | Extremely Low      | Use only for high-security apps |

**Recommended default**: 0.75

### Power Modes

The algorithm supports different power modes for optimization:

```java
FaceUnlockAlgorithmExtractor extractor = manager.getFaceEngine();
if (extractor != null) {
    Lite.LiteConfig config = new Lite.LiteConfig();
    config.bigCpuCore = Lite.LiteConfig.MG_UNLOCK_BIG_CPU_CORE_HIGH; // High performance
    // OR
    // config.bigCpuCore = Lite.LiteConfig.MG_UNLOCK_BIG_CPU_CORE_LOW;  // Low power
    
    extractor.setConfig(config);
}
```

## Performance Optimization

### 1. Pre-load Algorithm on App Start

```java
// In Application.onCreate()
FaceUnlockAlgorithmManager.getInstance(context).initialize(
    modelPath, panoramaPath, modelFile,
    callback
);
```

### 2. Use Background Threads

```java
// Don't block UI thread
new Thread(() -> {
    byte[] features = manager.extractFeatures(imageData, w, h, rot);
    // Update UI on main thread
    runOnUiThread(() -> updateUI(features));
}).start();
```

### 3. Cache Features

```java
// Store enrolled features efficiently
SharedPreferences prefs = context.getSharedPreferences("face_data", Context.MODE_PRIVATE);
String encoded = Base64.getEncoder().encodeToString(enrolledFeatures);
prefs.edit().putString("face_template", encoded).apply();

// Retrieve when needed
String encoded = prefs.getString("face_template", null);
byte[] features = Base64.getDecoder().decode(encoded);
```

## Security Considerations

### 1. Feature Storage

- **Store encrypted**: Use Android Keystore to encrypt feature data
- **Avoid plain text**: Never store features in plain SharedPreferences
- **Consider FIPS**: Use FIPS-compliant encryption if required

### 2. Image Data

- **Don't store images**: Delete camera frames after processing
- **Process in memory**: Don't write images to disk
- **Clear buffers**: Overwrite sensitive data after use

### 3. Template Matching

- **Use appropriate threshold**: 0.75 is standard, adjust based on use case
- **Multi-factor auth**: Combine with PIN/password for high-security apps
- **Liveness detection**: Detect replay attacks and spoofing attempts

### 4. Rate Limiting

```java
// Implement rate limiting to prevent brute force
private long lastAttemptTime = 0;
private int failedAttempts = 0;
private static final int MAX_ATTEMPTS = 5;
private static final long ATTEMPT_TIMEOUT = 60000; // 1 minute

public boolean canAttemptMatch() {
    long now = System.currentTimeMillis();
    if (now - lastAttemptTime > ATTEMPT_TIMEOUT) {
        failedAttempts = 0;
    }
    
    if (failedAttempts >= MAX_ATTEMPTS) {
        return false;
    }
    
    lastAttemptTime = now;
    return true;
}
```

## Error Handling

### Common Errors

| Error | Cause | Solution |
|-------|-------|----------|
| Algorithm not initialized | `initialize()` not called | Call `initialize()` with valid model paths |
| Library not found | .so files not extracted | Run auto-download or manual import |
| Feature extraction failed | Poor image quality or face not visible | Ensure good lighting and face is clearly visible |
| Hash mismatch | Downloaded file corrupted | Retry download or use manual import |
| Out of memory | Insufficient RAM | Close other apps or reduce image resolution |

### Robust Error Handling:

```java
try {
    manager.initialize(modelPath, panoramaPath, modelFile, new InitializationCallback() {
        @Override
        public void onSuccess() {
            // Proceed with face recognition
        }

        @Override
        public void onError(String error) {
            Log.e(TAG, "Initialization failed: " + error);
            // Show user-friendly error message
            showErrorDialog("Unable to initialize face recognition. Please try again.");
        }
    });
} catch (Exception e) {
    Log.e(TAG, "Unexpected error", e);
    // Handle unexpected errors gracefully
}
```

## Testing

### Unit Testing

```java
@Test
public void testAlgorithmInitialization() {
    FaceUnlockAlgorithmManager manager = FaceUnlockAlgorithmManager.getInstance(context);
    
    CountDownLatch latch = new CountDownLatch(1);
    manager.initialize(modelPath, panoramaPath, modelFile, new InitializationCallback() {
        @Override
        public void onSuccess() {
            assertTrue(manager.isInitialized());
            latch.countDown();
        }

        @Override
        public void onError(String error) {
            fail("Initialization should not fail");
        }
    });
    
    assertTrue(latch.await(10, TimeUnit.SECONDS));
}
```

### Integration Testing

```java
@Test
public void testFaceEnrollmentAndVerification() {
    // Generate test image
    byte[] testImage = generateTestNV21Image(320, 240);
    
    // Enroll
    byte[] features = manager.enrollFace(testImage, 320, 240, 0);
    assertNotNull(features);
    
    // Verify same image
    boolean matches = manager.verifyFace(testImage, 320, 240, 0, features, 0.75f);
    assertTrue(matches);
}
```

## Cleanup and Resource Management

```java
@Override
protected void onDestroy() {
    // Clean up algorithm when activity is destroyed
    FaceUnlockAlgorithmManager manager = FaceUnlockAlgorithmManager.getInstance(context);
    manager.shutdown();
    
    super.onDestroy();
}
```

## License

The face unlock algorithm is based on proprietary Megvii FacePP technology. Libraries must be extracted from Motorola's official Moto Face Unlock app. Ensure compliance with all applicable licenses and terms of service.

## References

- Megvii FacePP SDK Documentation
- Motorola Moto Face Unlock App
- Android Camera Framework Documentation
- YUV420 Image Format Specification
