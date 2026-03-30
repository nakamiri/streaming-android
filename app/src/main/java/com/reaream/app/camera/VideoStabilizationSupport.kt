package com.reaream.app.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Build
import com.reaream.app.data.model.VideoStabilizationMode

data class VideoStabilizationSupport(
    val electronicMode: Int?,
    val hasOpticalStabilization: Boolean,
)

data class VideoStabilizationRequest(
    val electronicMode: Int? = null,
    val opticalMode: Int? = null,
)

fun getVideoStabilizationSupport(
    cameraManager: CameraManager?,
    useFrontCamera: Boolean,
): VideoStabilizationSupport {
    if (cameraManager == null) {
        return VideoStabilizationSupport(electronicMode = null, hasOpticalStabilization = false)
    }

    return try {
        val desiredFacing = if (useFrontCamera) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }
        val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == desiredFacing
        } ?: return VideoStabilizationSupport(electronicMode = null, hasOpticalStabilization = false)

        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val modes = characteristics
            .get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
            ?.toSet()
            .orEmpty()
        val hasOpticalStabilization = characteristics
            .get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
            ?.contains(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON) == true

        val electronicMode = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                modes.contains(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION) -> {
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION
            }
            modes.contains(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON) -> {
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
            }
            else -> null
        }

        VideoStabilizationSupport(
            electronicMode = electronicMode,
            hasOpticalStabilization = hasOpticalStabilization,
        )
    } catch (_: Exception) {
        VideoStabilizationSupport(electronicMode = null, hasOpticalStabilization = false)
    }
}

fun resolveVideoStabilizationRequest(
    preference: VideoStabilizationMode,
    support: VideoStabilizationSupport,
): VideoStabilizationRequest = when (preference) {
    VideoStabilizationMode.AUTO -> when {
        support.hasOpticalStabilization -> VideoStabilizationRequest(
            electronicMode = support.electronicMode?.let { CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF },
            opticalMode = CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON,
        )
        support.electronicMode != null -> VideoStabilizationRequest(
            electronicMode = support.electronicMode,
        )
        else -> VideoStabilizationRequest()
    }
    VideoStabilizationMode.OPTICAL -> {
        if (!support.hasOpticalStabilization) {
            VideoStabilizationRequest()
        } else {
            VideoStabilizationRequest(
                electronicMode = support.electronicMode?.let { CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF },
                opticalMode = CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON,
            )
        }
    }
    VideoStabilizationMode.ELECTRONIC -> {
        if (support.electronicMode == null) {
            VideoStabilizationRequest()
        } else {
            VideoStabilizationRequest(
                electronicMode = support.electronicMode,
                opticalMode = if (support.hasOpticalStabilization) {
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF
                } else {
                    null
                },
            )
        }
    }
}
