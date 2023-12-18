package com.visioncamerafacedetector;


import static java.lang.Math.ceil;

import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.media.Image;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.mrousavy.camera.frameprocessor.Frame;
import com.mrousavy.camera.frameprocessor.FrameProcessorPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class VisionCameraFaceDetectorPlugin extends FrameProcessorPlugin {

  private final FaceDetector faceDetector;

  public VisionCameraFaceDetectorPlugin(Map<String, Object> options) {
    super(options);

    FaceDetectorOptions.Builder faceOptionsBuilder = new FaceDetectorOptions.Builder();
    if ("PERFORMANCE_MODE_ACCURATE".equals(options.get("performanceMode"))) {
      faceOptionsBuilder.setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE);
    } else {
      faceOptionsBuilder.setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST);
    }

    if ("LANDMARK_MODE_ALL".equals(options.get("landmarkMode"))) {
      faceOptionsBuilder.setPerformanceMode(FaceDetectorOptions.LANDMARK_MODE_ALL);
    } else {
      faceOptionsBuilder.setPerformanceMode(FaceDetectorOptions.LANDMARK_MODE_NONE);
    }

    if ("CONTOUR_MODE_ALL".equals(options.get("contourMode"))) {
      faceOptionsBuilder.setPerformanceMode(FaceDetectorOptions.CONTOUR_MODE_ALL);
    } else {
      faceOptionsBuilder.setPerformanceMode(FaceDetectorOptions.CONTOUR_MODE_NONE);
    }

    if ("CLASSIFICATION_MODE_ALL".equals(options.get("classificationMode"))) {
      faceOptionsBuilder.setPerformanceMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL);
    } else {
      faceOptionsBuilder.setPerformanceMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE);
    }

    Object minFaceSize = options.get("minFaceSize");
    if (minFaceSize != null) {
      faceOptionsBuilder.setMinFaceSize(((Number) minFaceSize).floatValue());
    } else {
      faceOptionsBuilder.setMinFaceSize(0.1f);
    }

    faceDetector = FaceDetection.getClient(faceOptionsBuilder.build());
  }

  private Map<String,Object> processBoundingBox(Rect boundingBox) {
    Map<String,Object> bounds = new HashMap<>();

    // Calculate offset (we need to center the overlay on the target)
    double offsetX =  (boundingBox.exactCenterX() - ceil(boundingBox.width())) / 2.0f;
    double offsetY =  (boundingBox.exactCenterY() - ceil(boundingBox.height())) / 2.0f;

    double x = boundingBox.right + offsetX;
    double y = boundingBox.top + offsetY;


    bounds.put("x", boundingBox.centerX() + (boundingBox.centerX() - x));
    bounds.put("y", boundingBox.centerY() + (y - boundingBox.centerY()));
    bounds.put("width", boundingBox.width());
    bounds.put("height", boundingBox.height());


    bounds.put("boundingCenterX", (double)boundingBox.centerX());
    bounds.put("boundingCenterY", (double)boundingBox.centerY());
    bounds.put("boundingExactCenterX", (double)boundingBox.exactCenterX());
    bounds.put("boundingExactCenterY", (double)boundingBox.exactCenterY());

    return bounds;
  }


  @SuppressLint("NewApi")
  @Override
  public Object callback(Frame frame, Map<String, Object> params) {
    @SuppressLint("UnsafeOptInUsageError")
    Image mediaImage = frame.getImage();

    if (mediaImage == null) {
      return null;
    }

    String rotationStr = frame.getOrientation();
    int rotation = 90;
    if ("PORTRAIT".equalsIgnoreCase(rotationStr)) {
      rotation = 0;
    } else if ("LANDSCAPE_RIGHT".equalsIgnoreCase(rotationStr)) {
      rotation = 90;
    } else if ("PORTRAIT_UPSIDE_DOWN".equalsIgnoreCase(rotationStr)) {
      rotation = 180;
    } else if ("LANDSCAPE_LEFT".equalsIgnoreCase(rotationStr)) {
      rotation = 270;
    }

    InputImage image = InputImage.fromMediaImage(mediaImage, rotation);
    Task<List<Face>> task = faceDetector.process(image).addOnSuccessListener(faces ->{
      Log.i("scanFaces", "OnSuccessListener->" + faces.size());
    }).addOnFailureListener(e -> {
      Log.i("scanFaces", "addOnFailureListener", e);
    });

    List<Map<String,Object>> result = new ArrayList<>();
    try {
      Log.d("scanFaces","scanFaces start check");
      List<Face> faces = Tasks.await(task);
      Log.d("scanFaces", "scanFaces result->" + faces.size());
      for (Face face : faces) {
        Map<String,Object> map = new HashMap<>();

        map.put("rollAngle", (double)face.getHeadEulerAngleZ()); // Head is rotated to the left rotZ degrees
        map.put("pitchAngle", (double)face.getHeadEulerAngleX()); // Head is rotated to the right rotX degrees
        map.put("yawAngle", (double)face.getHeadEulerAngleY());  // Head is tilted sideways rotY degrees

        Map<String,Object> bounds = processBoundingBox(face.getBoundingBox());

        map.put("bounds", bounds);

        result.add(map);
      }

      return result;
    } catch (Exception e) {
      Log.d("scanFaces","scanFaces error",e);
      return null;
    }

  }

}
