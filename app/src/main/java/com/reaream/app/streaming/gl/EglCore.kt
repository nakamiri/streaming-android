package com.reaream.app.streaming.gl

import android.opengl.*
import android.util.Log
import android.view.Surface

/**
 * Manages an EGL display, context, and surfaces.
 * All operations must be called on the GL thread after [init].
 */
class EglCore {
    var eglDisplay: EGLDisplay? = null
        private set
    var eglContext: EGLContext? = null
        private set
    private var eglConfig: EGLConfig? = null

    fun init() {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != null && display != EGL14.EGL_NO_DISPLAY) { "eglGetDisplay failed" }
        eglDisplay = display

        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize failed" }

        // EGL_RECORDABLE_ANDROID ensures compatibility with MediaCodec encoder surfaces
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        check(EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, numConfigs, 0) && numConfigs[0] > 0) {
            "eglChooseConfig failed: error=0x${EGL14.eglGetError().toString(16)}"
        }
        eglConfig = configs[0]

        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        val context = EGL14.eglCreateContext(display, eglConfig, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
        check(context != null && context != EGL14.EGL_NO_CONTEXT) {
            "eglCreateContext failed: error=0x${EGL14.eglGetError().toString(16)}"
        }
        eglContext = context
        Log.d(TAG, "EGL initialized: EGL ${version[0]}.${version[1]}")
    }

    /** Create an EGL window surface from an Android [Surface]. */
    fun createWindowSurface(surface: Surface): EGLSurface {
        val display = eglDisplay ?: error("EGL display is not initialized")
        val config = eglConfig ?: error("EGL config is not initialized")
        val attribs = intArrayOf(EGL14.EGL_NONE)
        return EGL14.eglCreateWindowSurface(display, config, surface, attribs, 0)
            .also { check(it != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface failed: 0x${EGL14.eglGetError().toString(16)}" } }
    }

    /** Create a 1×1 off-screen PBuffer surface (used to make context current before any window surface exists). */
    fun createOffscreenSurface(): EGLSurface {
        val display = eglDisplay ?: error("EGL display is not initialized")
        val config = eglConfig ?: error("EGL config is not initialized")
        val attribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
        return EGL14.eglCreatePbufferSurface(display, config, attribs, 0)
            .also { check(it != EGL14.EGL_NO_SURFACE) { "eglCreatePbufferSurface failed" } }
    }

    fun makeCurrent(surface: EGLSurface) {
        val display = eglDisplay ?: error("EGL display is not initialized")
        val context = eglContext ?: error("EGL context is not initialized")
        check(EGL14.eglMakeCurrent(display, surface, surface, context)) {
            "eglMakeCurrent failed: 0x${EGL14.eglGetError().toString(16)}"
        }
    }

    fun makeNoCurrent() {
        val display = eglDisplay ?: return
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
    }

    fun swapBuffers(surface: EGLSurface): Boolean {
        val display = eglDisplay ?: return false
        val swapped = EGL14.eglSwapBuffers(display, surface)
        if (!swapped) {
            Log.w(TAG, "eglSwapBuffers failed: 0x${EGL14.eglGetError().toString(16)}")
        }
        return swapped
    }

    fun destroySurface(surface: EGLSurface) {
        // Caller must have already made a different surface current (e.g., offscreenSurface).
        // Do NOT call makeNoCurrent() here — that would clear the context and break updateTexImage().
        val display = eglDisplay ?: return
        EGL14.eglDestroySurface(display, surface)
    }

    fun release() {
        makeNoCurrent()
        val display = eglDisplay
        val context = eglContext
        if (display != null && context != null && context != EGL14.EGL_NO_CONTEXT) {
            EGL14.eglDestroyContext(display, context)
            eglContext = null
        }
        if (display != null && display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglTerminate(display)
            eglDisplay = null
        }
    }

    companion object {
        private const val TAG = "EglCore"
        private const val EGL_RECORDABLE_ANDROID = 0x3142
    }
}
