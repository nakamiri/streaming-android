package com.reaream.app.streaming.gl

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.reaream.app.data.model.WidgetSettings
import com.reaream.app.streaming.WidgetRenderer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * GPU-accelerated streaming pipeline:
 *   Camera (Preview SurfaceTexture OES) → GL render → MediaCodec Surface input
 *                                                    → Display SurfaceView
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
    val glExecutor: Executor = Executor { cmd -> glHandler.post(cmd) }

    private val eglCore = EglCore()
    private var offscreenSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    // Camera input (OES texture + SurfaceTexture)
    private var oesTexId: Int = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var cameraSurface: Surface? = null

    // Encoder output EGL surface
    private var encoderEglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    @Volatile var encW: Int = 0; @Volatile var encH: Int = 0
    @Volatile private var baseTimestampNs: Long = -1L

    // Display output EGL surface
    private var displayEglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    @Volatile private var dispW: Int = 0; @Volatile private var dispH: Int = 0

    // Camera transform from CameraX TransformationInfo
    @Volatile var cameraRotation: Int = 0
    @Volatile var cameraMirroring: Boolean = false

    // Shader programs
    private var cameraProgram: Int = 0
    private var overlayProgram: Int = 0
    private var overlayTexId: Int = 0

    // Camera shader locations
    private var camPosAttr = 0; private var camTexAttr = 0
    private var camSamplerLoc = 0; private var camTexMatLoc = 0; private var camMvpLoc = 0

    // Overlay shader locations
    private var ovlPosAttr = 0; private var ovlTexAttr = 0; private var ovlSamplerLoc = 0

    // Reusable buffers
    private val texMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val quadBuf: java.nio.FloatBuffer = ByteBuffer
        .allocateDirect(QUAD_VERTS.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .also { it.put(QUAD_VERTS); it.position(0) }

    /** Called once when the first camera frame arrives AND encoder is not yet set up. */
    @Volatile var onFirstFrameReady: ((rotationDegrees: Int) -> Unit)? = null

    val framesRendered = AtomicInteger(0)

    init {
        glHandler.post { initGl() }
    }

    private fun initGl() {
        try {
            eglCore.init()
            offscreenSurface = eglCore.createOffscreenSurface()
            eglCore.makeCurrent(offscreenSurface)

            // Create OES texture for camera input
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
            st.setOnFrameAvailableListener({ glHandler.post { renderFrame() } })
            surfaceTexture = st
            cameraSurface = Surface(st)

            Log.i(TAG, "GL pipeline initialized")
        } catch (e: Exception) {
            Log.e(TAG, "GL init failed", e)
        }
    }

    /**
     * Call from CameraX SurfaceProvider on the GL executor.
     * Sets the camera buffer size and returns the Surface the camera should write to.
     */
    fun prepareCameraSurface(width: Int, height: Int): Surface? {
        // Already on GL thread (called via glExecutor)
        surfaceTexture?.setDefaultBufferSize(width, height)
        return cameraSurface
    }

    fun attachEncoderSurface(surface: Surface, w: Int, h: Int) {
        glHandler.post {
            if (encoderEglSurface != EGL14.EGL_NO_SURFACE) {
                eglCore.makeCurrent(offscreenSurface)
                eglCore.destroySurface(encoderEglSurface)
            }
            encoderEglSurface = eglCore.createWindowSurface(surface)
            encW = w; encH = h
            baseTimestampNs = -1L
            Log.i(TAG, "Encoder surface attached: ${w}x${h}")
        }
    }

    /**
     * Detach the encoder EGL surface synchronously on the GL thread.
     * Callers (releaseEncoders) must not stop/release the MediaCodec until this returns,
     * to prevent the GL thread being mid-swapBuffers on the encoder surface.
     */
    fun detachEncoderSurfaceSync() {
        val latch = java.util.concurrent.CountDownLatch(1)
        glHandler.post {
            if (encoderEglSurface != EGL14.EGL_NO_SURFACE) {
                eglCore.makeCurrent(offscreenSurface)
                eglCore.destroySurface(encoderEglSurface)
                encoderEglSurface = EGL14.EGL_NO_SURFACE
                encW = 0; encH = 0
                Log.i(TAG, "Encoder surface detached (sync)")
            }
            // Always reset so the next startStreaming() can fire onFirstFrameReady again
            onFirstFrameReady = null
            firstFrameCallbackFired = false
            latch.countDown()
        }
        latch.await(1, java.util.concurrent.TimeUnit.SECONDS)
    }

    fun detachEncoderSurface() {
        glHandler.post {
            if (encoderEglSurface != EGL14.EGL_NO_SURFACE) {
                eglCore.makeCurrent(offscreenSurface)
                eglCore.destroySurface(encoderEglSurface)
                encoderEglSurface = EGL14.EGL_NO_SURFACE
                encW = 0; encH = 0
                Log.i(TAG, "Encoder surface detached")
            }
            onFirstFrameReady = null
            firstFrameCallbackFired = false
        }
    }

    fun attachDisplaySurface(surface: Surface, w: Int, h: Int) {
        glHandler.post {
            if (displayEglSurface != EGL14.EGL_NO_SURFACE) {
                eglCore.makeCurrent(offscreenSurface)
                eglCore.destroySurface(displayEglSurface)
            }
            displayEglSurface = eglCore.createWindowSurface(surface)
            dispW = w; dispH = h
            Log.i(TAG, "Display surface attached: ${w}x${h}")
        }
    }

    fun detachDisplaySurface() {
        glHandler.post {
            if (displayEglSurface != EGL14.EGL_NO_SURFACE) {
                eglCore.makeCurrent(offscreenSurface)
                eglCore.destroySurface(displayEglSurface)
                displayEglSurface = EGL14.EGL_NO_SURFACE
                Log.i(TAG, "Display surface detached")
            }
        }
    }

    private var firstFrameCallbackFired = false

    private fun renderFrame() {
        val st = surfaceTexture ?: return
        val hasEncoder = encoderEglSurface != EGL14.EGL_NO_SURFACE
        val hasDisplay = displayEglSurface != EGL14.EGL_NO_SURFACE

        // Consume the frame regardless
        st.updateTexImage()

        if (!hasEncoder && !hasDisplay) return

        // First frame: notify StreamingEngine to set up encoder (if streaming)
        if (!hasEncoder && !firstFrameCallbackFired) {
            val callback = onFirstFrameReady
            if (callback != null) {
                firstFrameCallbackFired = true
                callback(cameraRotation)
            }
        }

        st.getTransformMatrix(texMatrix)

        // Build MVP from CameraX TransformationInfo: rotation (CW degrees) + front-camera mirror.
        // rotationDegrees: how much to rotate CW to display correctly → rotate vertices -rotationDegrees CCW.
        // isMirroring: front camera needs horizontal flip → scale X by -1 before rotation.
        Matrix.setIdentityM(mvpMatrix, 0)
        if (cameraRotation != 0) {
            Matrix.rotateM(mvpMatrix, 0, -cameraRotation.toFloat(), 0f, 0f, 1f)
        }
        if (cameraMirroring) {
            Matrix.scaleM(mvpMatrix, 0, -1f, 1f, 1f)
        }

        // Render widget overlay bitmap (only needed for encoder)
        val overlay: Bitmap? = if (hasEncoder && encW > 0 && encH > 0) {
            widgetRenderer.renderOverlayBitmap(encW, encH, widgetSettingsRef.get())
        } else null

        // Render to encoder surface
        if (hasEncoder) {
            eglCore.makeCurrent(encoderEglSurface)
            drawScene(texMatrix, mvpMatrix, overlay, encW, encH)
            val ts = st.timestamp
            if (baseTimestampNs < 0) baseTimestampNs = ts
            EGLExt.eglPresentationTimeANDROID(eglCore.eglDisplay, encoderEglSurface, ts - baseTimestampNs)
            eglCore.swapBuffers(encoderEglSurface)
            framesRendered.incrementAndGet()
        }

        // Render to display surface (no overlay — Compose UI handles the widget overlay display)
        if (hasDisplay) {
            eglCore.makeCurrent(displayEglSurface)
            drawScene(texMatrix, mvpMatrix, null, dispW, dispH)
            eglCore.swapBuffers(displayEglSurface)
        }
    }

    private fun drawScene(texMatrix: FloatArray, mvpMatrix: FloatArray, overlay: Bitmap?, viewW: Int, viewH: Int) {
        GLES20.glViewport(0, 0, viewW, viewH)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // --- Camera OES texture ---
        GLES20.glUseProgram(cameraProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexId)
        GLES20.glUniform1i(camSamplerLoc, 0)
        GLES20.glUniformMatrix4fv(camTexMatLoc, 1, false, texMatrix, 0)
        GLES20.glUniformMatrix4fv(camMvpLoc, 1, false, mvpMatrix, 0)

        quadBuf.position(0)
        GLES20.glVertexAttribPointer(camPosAttr, 2, GLES20.GL_FLOAT, false, 16, quadBuf)
        GLES20.glEnableVertexAttribArray(camPosAttr)
        quadBuf.position(2)
        GLES20.glVertexAttribPointer(camTexAttr, 2, GLES20.GL_FLOAT, false, 16, quadBuf)
        GLES20.glEnableVertexAttribArray(camTexAttr)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // --- Widget overlay (encoder only) ---
        if (overlay != null) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTexId)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, overlay, 0)

            GLES20.glUseProgram(overlayProgram)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTexId)
            GLES20.glUniform1i(ovlSamplerLoc, 0)

            quadBuf.position(0)
            GLES20.glVertexAttribPointer(ovlPosAttr, 2, GLES20.GL_FLOAT, false, 16, quadBuf)
            GLES20.glEnableVertexAttribArray(ovlPosAttr)
            quadBuf.position(2)
            GLES20.glVertexAttribPointer(ovlTexAttr, 2, GLES20.GL_FLOAT, false, 16, quadBuf)
            GLES20.glEnableVertexAttribArray(ovlTexAttr)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisable(GLES20.GL_BLEND)
        }
    }

    fun release() {
        glHandler.post {
            surfaceTexture?.release()
            cameraSurface?.release()
            cameraSurface = null

            if (encoderEglSurface != EGL14.EGL_NO_SURFACE) {
                eglCore.makeCurrent(offscreenSurface)
                eglCore.destroySurface(encoderEglSurface)
                encoderEglSurface = EGL14.EGL_NO_SURFACE
            }
            if (displayEglSurface != EGL14.EGL_NO_SURFACE) {
                eglCore.makeCurrent(offscreenSurface)
                eglCore.destroySurface(displayEglSurface)
                displayEglSurface = EGL14.EGL_NO_SURFACE
            }
            if (offscreenSurface != EGL14.EGL_NO_SURFACE) {
                eglCore.destroySurface(offscreenSurface)
                offscreenSurface = EGL14.EGL_NO_SURFACE
            }

            if (oesTexId != 0) {
                GLES20.glDeleteTextures(2, intArrayOf(oesTexId, overlayTexId), 0)
                oesTexId = 0
            }
            if (cameraProgram != 0) { GLES20.glDeleteProgram(cameraProgram); cameraProgram = 0 }
            if (overlayProgram != 0) { GLES20.glDeleteProgram(overlayProgram); overlayProgram = 0 }

            eglCore.release()
            glThread.quit()
            Log.i(TAG, "GL pipeline released")
        }
    }

    private fun createProgram(vert: String, frag: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vert)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, frag)
        return GLES20.glCreateProgram().also { prog ->
            GLES20.glAttachShader(prog, vs)
            GLES20.glAttachShader(prog, fs)
            GLES20.glLinkProgram(prog)
            val status = IntArray(1)
            GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, status, 0)
            if (status[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "Program link error: ${GLES20.glGetProgramInfoLog(prog)}")
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
        if (status[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Shader compile error (type=$type): ${GLES20.glGetShaderInfoLog(shader)}")
        }
        return shader
    }

    companion object {
        private const val TAG = "GlStreamPipeline"

        // Full-screen quad: (x, y, s, t) × 4 vertices, triangle strip (BL, BR, TL, TR)
        private val QUAD_VERTS = floatArrayOf(
            -1f, -1f, 0f, 0f,
             1f, -1f, 1f, 0f,
            -1f,  1f, 0f, 1f,
             1f,  1f, 1f, 1f,
        )

        // Camera: MVP matrix rotates vertex positions; texMatrix from SurfaceTexture handles
        // Y-flip and crop. The OES extension is required for camera textures.
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

        // Overlay: identity position (no rotation — overlay is already in output orientation).
        // Flip Y so Android Bitmap (row 0 = top) maps correctly to GL texture (t=0 = bottom).
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
