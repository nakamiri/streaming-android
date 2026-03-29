package com.reaream.app.streaming.gl

import android.opengl.*
import android.util.Log
import android.view.Surface

/**
 * Manages an EGL display, context, and surfaces.
 * All operations must be called on the GL thread after [init].
 */
class EglCore {
    var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private set
    var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private set
    private var eglConfig: EGLConfig? = null

    fun init() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "eglGetDisplay failed" }

        val version = IntArray(2)
        check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) { "eglInitialize failed" }

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
        check(EGL14.eglChooseConfig(eglDisplay, attribs, 0, configs, 0, 1, numConfigs, 0) && numConfigs[0] > 0) {
            "eglChooseConfig failed: error=0x${EGL14.eglGetError().toString(16)}"
        }
        eglConfig = configs[0]

        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
        check(eglContext != EGL14.EGL_NO_CONTEXT) {
            "eglCreateContext failed: error=0x${EGL14.eglGetError().toString(16)}"
        }
        Log.d(TAG, "EGL initialized: EGL ${version[0]}.${version[1]}")
    }

    /** Create an EGL window surface from an Android [Surface]. */
    fun createWindowSurface(surface: Surface): EGLSurface {
        val attribs = intArrayOf(EGL14.EGL_NONE)
        return EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, attribs, 0)
            .also { check(it != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface failed: 0x${EGL14.eglGetError().toString(16)}" } }
    }

    /** Create a 1×1 off-screen PBuffer surface (used to make context current before any window surface exists). */
    fun createOffscreenSurface(): EGLSurface {
        val attribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
        return EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, attribs, 0)
            .also { check(it != EGL14.EGL_NO_SURFACE) { "eglCreatePbufferSurface failed" } }
    }

    fun makeCurrent(surface: EGLSurface) {
        check(EGL14.eglMakeCurrent(eglDisplay, surface, surface, eglContext)) {
            "eglMakeCurrent failed: 0x${EGL14.eglGetError().toString(16)}"
        }
    }

    fun makeNoCurrent() {
        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
    }

    fun swapBuffers(surface: EGLSurface) {
        EGL14.eglSwapBuffers(eglDisplay, surface)
    }

    fun destroySurface(surface: EGLSurface) {
        // Caller must have already made a different surface current (e.g., offscreenSurface).
        // Do NOT call makeNoCurrent() here — that would clear the context and break updateTexImage().
        EGL14.eglDestroySurface(eglDisplay, surface)
    }

    fun release() {
        makeNoCurrent()
        if (eglContext != EGL14.EGL_NO_CONTEXT) {
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            eglContext = EGL14.EGL_NO_CONTEXT
        }
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglTerminate(eglDisplay)
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }
    }

    companion object {
        private const val TAG = "EglCore"
        private const val EGL_RECORDABLE_ANDROID = 0x3142
    }
}
