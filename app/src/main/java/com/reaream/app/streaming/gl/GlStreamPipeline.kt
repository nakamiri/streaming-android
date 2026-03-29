package com.reaream.app.streaming.gl

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.reaream.app.data.model.WidgetSettings
import com.reaream.app.streaming.WidgetRenderer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * GPU-accelerated streaming pipeline:
 *   Camera (Preview SurfaceTexture OES) -> GL render -> MediaCodec Surface input
 *                                                -> Display SurfaceView
 *
 * All GL work runs on a dedicated HandlerThread.
 */
class GlStreamPipeline(
    private val widgetRenderer: WidgetRenderer,
    private val widgetSettingsRef: AtomicReference<WidgetSettings>,
) {
    private val glThread = HandlerThread("GlStreamPipeline").also { it.start() }
    private val glHandler = Handler(glThread.looper)

    /** Executor backed by the GL thread — pass to CameraX SurfaceProvider. */
    val glExecutor: Executor = Executor { command ->
        if (!released) {
            glHandler.post(command)
        }
    }

    private val eglCore = EglCore()
    private var offscreenSurface: EGLSurface? = null

    // Camera input (OES texture + SurfaceTexture)
    private var oesTexId: Int = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var cameraSurface: Surface? = null
    @Volatile private var sourceWidth: Int = 0
    @Volatile private var sourceHeight: Int = 0

    // Encoder output EGL surface
    private var encoderEglSurface: EGLSurface? = null
    @Volatile var encW: Int = 0
    @Volatile var encH: Int = 0
    @Volatile private var baseTimestampNs: Long = -1L

    // Display output EGL surface
    private var displayEglSurface: EGLSurface? = null
    @Volatile private var dispW: Int = 0
    @Volatile private var dispH: Int = 0
    @Volatile private var lastDisplayRenderTimestampNs: Long = Long.MIN_VALUE
    @Volatile private var displayFrameIntervalWhileEncodingNs: Long = DEFAULT_DISPLAY_FRAME_INTERVAL_WHILE_ENCODING_NS

    // Camera transform state
    @Volatile var cameraRotation: Int = 0
    @Volatile private var mirrorPreview: Boolean = false
    @Volatile var fallbackRotation: Int = 0

    // Shader programs
    private var cameraProgram: Int = 0
    private var overlayProgram: Int = 0
    private var overlayTexId: Int = 0
    private var uploadedOverlayVersion: Int = -1

    // Camera shader locations
    private var camPosAttr = 0
    private var camTexAttr = 0
    private var camSamplerLoc = 0
    private var camTexMatLoc = 0
    private var camMvpLoc = 0

    // Overlay shader locations
    private var ovlPosAttr = 0
    private var ovlTexAttr = 0
    private var ovlSamplerLoc = 0

    // Reusable buffers
    private val texMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val positionBuf = ByteBuffer
        .allocateDirect(QUAD_POSITIONS.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .also { it.put(QUAD_POSITIONS); it.position(0) }
    private val overlayTexBuf = ByteBuffer
        .allocateDirect(QUAD_TEX_COORDS.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .also { it.put(QUAD_TEX_COORDS); it.position(0) }
    private val cameraTexBuf = ByteBuffer
        .allocateDirect(QUAD_TEX_COORDS.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .also { it.put(QUAD_TEX_COORDS); it.position(0) }

    /** Called once when the first camera frame arrives AND encoder is not yet set up. */
    @Volatile var onFirstFrameReady: ((rotationDegrees: Int) -> Unit)? = null

    val framesRendered = AtomicInteger(0)
    val framesDropped = AtomicInteger(0)

    private var firstFrameCallbackFired = false
    private var pendingFrameSignals = 0
    private var renderPosted = false
    @Volatile private var released = false
    @Volatile private var initFailed = false

    init {
        try {
            runOnGlThreadSync("initGl") { initGlInternal() }
        } catch (e: Exception) {
            initFailed = true
            Log.e(TAG, "GL pipeline initialization failed", e)
        }
    }

    fun setFrontCamera(isFrontCamera: Boolean) {
        mirrorPreview = isFrontCamera
    }

    fun updateFallbackRotation(rotationDegrees: Int) {
        fallbackRotation = rotationDegrees
        if (cameraRotation == 0) {
            cameraRotation = rotationDegrees
        }
    }

    fun setDisplayPreviewFpsCapWhileEncoding(fps: Int) {
        displayFrameIntervalWhileEncodingNs = if (fps <= 0) {
            0L
        } else {
            NANOS_PER_SECOND / fps.toLong()
        }
        lastDisplayRenderTimestampNs = Long.MIN_VALUE
    }

    /**
     * Call from CameraX SurfaceProvider on the GL executor.
     * Sets the camera buffer size and returns the Surface the camera should write to.
     */
    fun prepareCameraSurface(width: Int, height: Int): Surface? {
        if (released || initFailed) return null
        sourceWidth = width
        sourceHeight = height
        surfaceTexture?.setDefaultBufferSize(width, height)
        return cameraSurface
    }

    fun attachEncoderSurfaceSync(surface: Surface, width: Int, height: Int) {
        if (initFailed) return
        runOnGlThreadSync("attachEncoderSurface") {
            val offscreen = offscreenSurface
            if (encoderEglSurface != null && offscreen != null) {
                eglCore.makeCurrent(offscreen)
                eglCore.destroySurface(encoderEglSurface!!)
            }
            encoderEglSurface = eglCore.createWindowSurface(surface)
            encW = width
            encH = height
            baseTimestampNs = -1L
            Log.i(TAG, "Encoder surface attached: ${width}x${height}")
        }
    }

    fun detachEncoderSurfaceSync() {
        if (initFailed) return
        runOnGlThreadSync("detachEncoderSurface") {
            val offscreen = offscreenSurface
            if (encoderEglSurface != null && offscreen != null) {
                eglCore.makeCurrent(offscreen)
                eglCore.destroySurface(encoderEglSurface!!)
                encoderEglSurface = null
            }
            encW = 0
            encH = 0
            baseTimestampNs = -1L
            uploadedOverlayVersion = -1
            onFirstFrameReady = null
            firstFrameCallbackFired = false
            Log.i(TAG, "Encoder surface detached")
        }
    }

    fun attachDisplaySurface(surface: Surface, width: Int, height: Int) {
        if (released || initFailed) return
        glHandler.post {
            val offscreen = offscreenSurface
            if (displayEglSurface != null && offscreen != null) {
                eglCore.makeCurrent(offscreen)
                eglCore.destroySurface(displayEglSurface!!)
            }
            displayEglSurface = eglCore.createWindowSurface(surface)
            dispW = width
            dispH = height
            lastDisplayRenderTimestampNs = Long.MIN_VALUE
            Log.i(TAG, "Display surface attached: ${width}x${height}")
        }
    }

    fun detachDisplaySurface() {
        if (released || initFailed) return
        glHandler.post {
            val offscreen = offscreenSurface
            if (displayEglSurface != null && offscreen != null) {
                eglCore.makeCurrent(offscreen)
                eglCore.destroySurface(displayEglSurface!!)
                displayEglSurface = null
                dispW = 0
                dispH = 0
                lastDisplayRenderTimestampNs = Long.MIN_VALUE
                Log.i(TAG, "Display surface detached")
            }
        }
    }

    fun release() {
        if (released) return
        released = true
        if (initFailed) {
            glThread.quitSafely()
            try {
                glThread.join(1000)
            } catch (_: InterruptedException) {
            }
            return
        }
        runOnGlThreadSync("releaseGl") {
            surfaceTexture?.release()
            surfaceTexture = null
            cameraSurface?.release()
            cameraSurface = null

            val offscreen = offscreenSurface
            if (encoderEglSurface != null && offscreen != null) {
                eglCore.makeCurrent(offscreen)
                eglCore.destroySurface(encoderEglSurface!!)
                encoderEglSurface = null
            }
            if (displayEglSurface != null && offscreen != null) {
                eglCore.makeCurrent(offscreen)
                eglCore.destroySurface(displayEglSurface!!)
                displayEglSurface = null
            }
            if (offscreen != null) {
                eglCore.destroySurface(offscreen)
                offscreenSurface = null
            }

            if (oesTexId != 0 || overlayTexId != 0) {
                GLES20.glDeleteTextures(2, intArrayOf(oesTexId, overlayTexId), 0)
                oesTexId = 0
                overlayTexId = 0
            }
            uploadedOverlayVersion = -1
            if (cameraProgram != 0) {
                GLES20.glDeleteProgram(cameraProgram)
                cameraProgram = 0
            }
            if (overlayProgram != 0) {
                GLES20.glDeleteProgram(overlayProgram)
                overlayProgram = 0
            }

            eglCore.release()
            Log.i(TAG, "GL pipeline released")
        }
        glThread.quitSafely()
        try {
            glThread.join(1000)
        } catch (_: InterruptedException) {
        }
    }

    private fun initGlInternal() {
        eglCore.init()
        offscreenSurface = eglCore.createOffscreenSurface()
        eglCore.makeCurrent(offscreenSurface ?: error("Failed to create offscreen surface"))

        val texIds = IntArray(2)
        GLES20.glGenTextures(2, texIds, 0)
        oesTexId = texIds[0]
        overlayTexId = texIds[1]

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTexId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        cameraProgram = createProgram(CAMERA_VERT, CAMERA_FRAG)
        camPosAttr = GLES20.glGetAttribLocation(cameraProgram, "aPosition")
        camTexAttr = GLES20.glGetAttribLocation(cameraProgram, "aTexCoord")
        camSamplerLoc = GLES20.glGetUniformLocation(cameraProgram, "uTexture")
        camTexMatLoc = GLES20.glGetUniformLocation(cameraProgram, "uTexMatrix")
        camMvpLoc = GLES20.glGetUniformLocation(cameraProgram, "uMVP")

        overlayProgram = createProgram(OVERLAY_VERT, OVERLAY_FRAG)
        ovlPosAttr = GLES20.glGetAttribLocation(overlayProgram, "aPosition")
        ovlTexAttr = GLES20.glGetAttribLocation(overlayProgram, "aTexCoord")
        ovlSamplerLoc = GLES20.glGetUniformLocation(overlayProgram, "uTexture")

        val st = SurfaceTexture(oesTexId)
        st.setOnFrameAvailableListener({ onFrameAvailable() }, glHandler)
        surfaceTexture = st
        cameraSurface = Surface(st)

        Log.i(TAG, "GL pipeline initialized")
    }

    private fun onFrameAvailable() {
        if (released) return
        pendingFrameSignals++
        if (renderPosted) return
        renderPosted = true
        glHandler.post {
            renderPosted = false
            val dropped = (pendingFrameSignals - 1).coerceAtLeast(0)
            if (dropped > 0) {
                framesDropped.addAndGet(dropped)
            }
            pendingFrameSignals = 0
            renderFrame()
        }
    }

    private fun renderFrame() {
        val st = surfaceTexture ?: return
        val hasEncoder = encoderEglSurface != null
        val hasDisplay = displayEglSurface != null

        st.updateTexImage()
        if (!hasEncoder && !hasDisplay) return

        if (!hasEncoder && !firstFrameCallbackFired) {
            val callback = onFirstFrameReady
            if (callback != null) {
                firstFrameCallbackFired = true
                callback(cameraRotation)
            }
        }

        st.getTransformMatrix(texMatrix)

        val overlay: Bitmap? = if (hasEncoder && encW > 0 && encH > 0) {
            widgetRenderer.renderOverlayBitmap(encW, encH, widgetSettingsRef.get())
        } else {
            null
        }
        val overlayVersion = widgetRenderer.overlayVersion
        val frameTimestampNs = st.timestamp

        if (hasEncoder && encW > 0 && encH > 0) {
            if (overlay == null) {
                uploadedOverlayVersion = -1
            }
            val encoderSurface = encoderEglSurface ?: return
            eglCore.makeCurrent(encoderSurface)
            drawScene(texMatrix, overlay, overlayVersion, encW, encH, cameraRotation, mirror = false)
            if (baseTimestampNs < 0) {
                baseTimestampNs = frameTimestampNs
            }
            val eglDisplay = eglCore.eglDisplay ?: return
            EGLExt.eglPresentationTimeANDROID(
                eglDisplay,
                encoderSurface,
                (frameTimestampNs - baseTimestampNs).coerceAtLeast(0L),
            )
            if (eglCore.swapBuffers(encoderSurface)) {
                framesRendered.incrementAndGet()
            } else {
                framesDropped.incrementAndGet()
            }
        }

        val shouldRenderDisplay = hasDisplay && dispW > 0 && dispH > 0 && shouldRenderDisplayFrame(
            frameTimestampNs = frameTimestampNs,
            hasEncoder = hasEncoder,
            lastDisplayRenderTimestampNs = lastDisplayRenderTimestampNs,
            displayFrameIntervalNs = displayFrameIntervalWhileEncodingNs,
        )

        if (shouldRenderDisplay) {
            val displaySurface = displayEglSurface ?: return
            eglCore.makeCurrent(displaySurface)
            drawScene(texMatrix, null, overlayVersion, dispW, dispH, cameraRotation, mirror = mirrorPreview)
            if (!eglCore.swapBuffers(displaySurface)) {
                framesDropped.incrementAndGet()
            } else {
                lastDisplayRenderTimestampNs = frameTimestampNs
            }
        }

        val offscreen = offscreenSurface
        if (offscreen != null) {
            eglCore.makeCurrent(offscreen)
        }
    }

    private fun drawScene(
        texTransform: FloatArray,
        overlay: Bitmap?,
        overlayVersion: Int,
        viewWidth: Int,
        viewHeight: Int,
        rotationDegrees: Int,
        mirror: Boolean,
    ) {
        buildMvpMatrix(mvpMatrix, viewWidth, viewHeight, rotationDegrees, mirror)
        updateCameraTexCoords(cameraTexBuf, rotationDegrees, mirror)

        GLES20.glViewport(0, 0, viewWidth, viewHeight)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(cameraProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexId)
        GLES20.glUniform1i(camSamplerLoc, 0)
        GLES20.glUniformMatrix4fv(camTexMatLoc, 1, false, texTransform, 0)
        GLES20.glUniformMatrix4fv(camMvpLoc, 1, false, mvpMatrix, 0)

        positionBuf.position(0)
        GLES20.glVertexAttribPointer(camPosAttr, 2, GLES20.GL_FLOAT, false, 0, positionBuf)
        GLES20.glEnableVertexAttribArray(camPosAttr)
        cameraTexBuf.position(0)
        GLES20.glVertexAttribPointer(camTexAttr, 2, GLES20.GL_FLOAT, false, 0, cameraTexBuf)
        GLES20.glEnableVertexAttribArray(camTexAttr)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        if (overlay != null) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTexId)
            if (overlayVersion != uploadedOverlayVersion) {
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, overlay, 0)
                uploadedOverlayVersion = overlayVersion
            }

            GLES20.glUseProgram(overlayProgram)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTexId)
            GLES20.glUniform1i(ovlSamplerLoc, 0)

            positionBuf.position(0)
            GLES20.glVertexAttribPointer(ovlPosAttr, 2, GLES20.GL_FLOAT, false, 0, positionBuf)
            GLES20.glEnableVertexAttribArray(ovlPosAttr)
            overlayTexBuf.position(0)
            GLES20.glVertexAttribPointer(ovlTexAttr, 2, GLES20.GL_FLOAT, false, 0, overlayTexBuf)
            GLES20.glEnableVertexAttribArray(ovlTexAttr)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisable(GLES20.GL_BLEND)
        }
    }

    private fun buildMvpMatrix(
        outMatrix: FloatArray,
        viewWidth: Int,
        viewHeight: Int,
        rotationDegrees: Int,
        mirror: Boolean,
    ) {
        val srcW = sourceWidth.coerceAtLeast(1)
        val srcH = sourceHeight.coerceAtLeast(1)
        val rotatedSrcW = if (rotationDegrees % 180 == 0) srcW else srcH
        val rotatedSrcH = if (rotationDegrees % 180 == 0) srcH else srcW
        val srcAspect = rotatedSrcW.toFloat() / rotatedSrcH.coerceAtLeast(1)
        val dstAspect = viewWidth.toFloat() / viewHeight.coerceAtLeast(1)

        var scaleX = 1f
        var scaleY = 1f
        if (srcAspect > dstAspect) {
            scaleX = srcAspect / dstAspect
        } else {
            scaleY = dstAspect / srcAspect
        }

        Matrix.setIdentityM(outMatrix, 0)
        Matrix.scaleM(outMatrix, 0, scaleX, scaleY, 1f)
    }

    private fun updateCameraTexCoords(buffer: FloatBuffer, rotationDegrees: Int, mirror: Boolean) {
        val coords = QUAD_TEX_COORDS

        buffer.position(0)
        if (mirror) {
            val mirrored = FloatArray(coords.size)
            for (index in coords.indices step 2) {
                mirrored[index] = 1f - coords[index]
                mirrored[index + 1] = coords[index + 1]
            }
            buffer.put(mirrored)
        } else {
            buffer.put(coords)
        }
        buffer.position(0)
    }

    private fun runOnGlThreadSync(description: String, block: () -> Unit) {
        if (Thread.currentThread() == glThread) {
            block()
            return
        }
        if (!glThread.isAlive) return

        val latch = CountDownLatch(1)
        var failure: Throwable? = null
        glHandler.post {
            try {
                block()
            } catch (t: Throwable) {
                failure = t
            } finally {
                latch.countDown()
            }
        }

        if (!latch.await(3, TimeUnit.SECONDS)) {
            throw IllegalStateException("Timed out waiting for GL operation: $description")
        }
        failure?.let { throw IllegalStateException("GL operation failed: $description", it) }
    }

    private fun createProgram(vert: String, frag: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vert)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, frag)
        return GLES20.glCreateProgram().also { program ->
            GLES20.glAttachShader(program, vs)
            GLES20.glAttachShader(program, fs)
            GLES20.glLinkProgram(program)
            val status = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
            check(status[0] == GLES20.GL_TRUE) {
                "Program link error: ${GLES20.glGetProgramInfoLog(program)}"
            }
            GLES20.glDeleteShader(vs)
            GLES20.glDeleteShader(fs)
        }
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) {
            "Shader compile error (type=$type): ${GLES20.glGetShaderInfoLog(shader)}"
        }
        return shader
    }

    companion object {
        private const val TAG = "GlStreamPipeline"
        private const val DISPLAY_FPS_WHILE_ENCODING = 30
        private const val NANOS_PER_SECOND = 1_000_000_000L
        internal const val DEFAULT_DISPLAY_FRAME_INTERVAL_WHILE_ENCODING_NS =
            NANOS_PER_SECOND / DISPLAY_FPS_WHILE_ENCODING

        private val QUAD_POSITIONS = floatArrayOf(
            -1f, -1f,
             1f, -1f,
            -1f,  1f,
             1f,  1f,
        )

        private val QUAD_TEX_COORDS = floatArrayOf(
            0f, 0f,
            1f, 0f,
            0f, 1f,
            1f, 1f,
        )

        private const val CAMERA_VERT = """
            uniform mat4 uMVP;
            uniform mat4 uTexMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = uMVP * aPosition;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
        """

        private const val CAMERA_FRAG = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uTexture;
            varying vec2 vTexCoord;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """

        private const val OVERLAY_VERT = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = vec2(aTexCoord.x, 1.0 - aTexCoord.y);
            }
        """

        private const val OVERLAY_FRAG = """
            precision mediump float;
            uniform sampler2D uTexture;
            varying vec2 vTexCoord;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """
    }
}

internal fun shouldRenderDisplayFrame(
    frameTimestampNs: Long,
    hasEncoder: Boolean,
    lastDisplayRenderTimestampNs: Long,
    displayFrameIntervalNs: Long,
): Boolean {
    if (!hasEncoder) return true
    if (displayFrameIntervalNs <= 0L) return true
    if (lastDisplayRenderTimestampNs == Long.MIN_VALUE) return true
    return frameTimestampNs - lastDisplayRenderTimestampNs >=
        displayFrameIntervalNs
}
