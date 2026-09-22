package com.lmg.vk.artwork

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import com.lmg.vk.debug.DebugLog
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class MotionArtworkRenderer(
    private val onSurface: (Surface) -> Unit,
    private val onFailure: (Exception) -> Unit,
) {
    private data class Output(val window: EGLSurface, val surface: Surface,
        var width: Int, var height: Int, val mode: Int, var pending: Boolean = false)
    private val thread = HandlerThread("MotionArtwork").apply { start() }
    private val handler = Handler(thread.looper)
    private val main = Handler(android.os.Looper.getMainLooper())
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var anchor: EGLSurface = EGL14.EGL_NO_SURFACE
    private var config: EGLConfig? = null
    private val outputs = linkedMapOf<SurfaceTexture, Output>()
    private var input: SurfaceTexture? = null
    private var decoderSurface: Surface? = null
    private var texture = 0
    private var program = 0
    private var blurProgram = 0
    private val blurTextures = IntArray(2)
    private val blurBuffers = IntArray(2)
    private var hasFrame = false
    private var videoAspect = 1f
    private val matrix = FloatArray(16)
    private val vertices = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f,
            -1f, 1f, 0f, 1f, 1f, 1f, 1f, 1f)); position(0)
    }
    @Volatile private var closed = false

    init { submit { initialize() } }

    private fun submit(block: () -> Unit) {
        handler.post {
            if (!closed) try { block() } catch (error: Exception) {
                main.post { if (!closed) onFailure(error) }
            }
        }
    }

    fun attach(surface: SurfaceTexture, width: Int, height: Int, mode: Int) = submit {
        val windowSurface = Surface(surface)
        val window = EGL14.eglCreateWindowSurface(display, config, windowSurface, intArrayOf(EGL14.EGL_NONE), 0)
        if (window == EGL14.EGL_NO_SURFACE) {
            windowSurface.release()
            error("Motion window unavailable")
        }
        outputs[surface] = Output(window, windowSurface, width, height, mode)
        draw()
    }

    fun frameConsumed(surface: SurfaceTexture) = submit {
        outputs[surface]?.pending = false
    }

    fun resize(surface: SurfaceTexture, width: Int, height: Int) = submit {
        outputs[surface]?.let { it.width = width; it.height = height }
        draw()
    }

    fun detach(surface: SurfaceTexture, releaseTexture: Boolean) {
        if (!handler.post {
            outputs.remove(surface)?.let {
                EGL14.eglMakeCurrent(display, anchor, anchor, context)
                EGL14.eglDestroySurface(display, it.window)
                it.surface.release()
            }
            if (releaseTexture) surface.release()
        } && releaseTexture) surface.release()
    }

    fun videoSize(width: Int, height: Int) = submit {
        if (width > 0 && height > 0) videoAspect = width.toFloat() / height
    }

    private fun initialize() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 0))
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(display, intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_NONE), 0, configs, 0, 1, count, 0))
        config = requireNotNull(configs[0])
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        check(context != EGL14.EGL_NO_CONTEXT)
        anchor = EGL14.eglCreatePbufferSurface(display, config,
            intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        check(EGL14.eglMakeCurrent(display, anchor, anchor, context))
        val names = IntArray(1)
        GLES20.glGenTextures(1, names, 0)
        texture = names[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        program = createProgram(VIDEO_FRAGMENT)
        blurProgram = createProgram(BLUR_FRAGMENT)
        GLES20.glGenTextures(2, blurTextures, 0)
        GLES20.glGenFramebuffers(2, blurBuffers, 0)
        for (index in 0..1) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTextures[index])
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 32, 32, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, blurBuffers[index])
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, blurTextures[index], 0)
            check(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE)
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        input = SurfaceTexture(texture).apply {
            setOnFrameAvailableListener({
                if (!closed) try {
                    check(EGL14.eglMakeCurrent(display, anchor, anchor, context))
                    updateTexImage()
                    getTransformMatrix(matrix)
                    hasFrame = true
                    draw()
                } catch (error: Exception) { main.post { if (!closed) onFailure(error) } }
            }, handler)
        }
        decoderSurface = Surface(input).also { surface -> main.post { if (!closed) onSurface(surface) } }
    }

    private fun quad(shader: Int) {
        val position = GLES20.glGetAttribLocation(shader, "position")
        val uv = GLES20.glGetAttribLocation(shader, "uv")
        vertices.position(0)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 16, vertices)
        GLES20.glEnableVertexAttribArray(position)
        vertices.position(2)
        GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 16, vertices)
        GLES20.glEnableVertexAttribArray(uv)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun useVideo(cropX: Float, cropY: Float, mode: Int, aspect: Float = 1f) {
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "video"), 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, if (mode != 0) blurTextures[0] else 0)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "softened"), 1)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "transform"), 1, false, matrix, 0)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "crop"), cropX, cropY)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "mode"), mode.toFloat())
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "artFraction"), minOf(aspect / videoAspect, 0.66f))

    }

    private fun soften() {
        check(EGL14.eglMakeCurrent(display, anchor, anchor, context))
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, blurBuffers[0])
        GLES20.glViewport(0, 0, 32, 32)
        useVideo(1f, 1f, 0)
        quad(program)
        GLES20.glUseProgram(blurProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(blurProgram, "image"), 0)
        for (pass in 0 until 6) {
            val source = pass % 2
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, blurBuffers[1 - source])
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTextures[source])
            GLES20.glUniform2f(GLES20.glGetUniformLocation(blurProgram, "stepSize"),
                if (source == 0) 1f / 32 else 0f, if (source == 1) 1f / 32 else 0f)
            quad(blurProgram)
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    private fun draw() {
        if (!hasFrame) return
        if (outputs.values.any { it.mode != 0 && !it.pending }) soften()
        val iterator = outputs.values.iterator()
        while (iterator.hasNext()) {
            val output = iterator.next()
            if (output.width <= 0 || output.height <= 0 || output.pending) continue
            try {
                check(EGL14.eglMakeCurrent(display, output.window, output.window, context))
                EGL14.eglSwapInterval(display, 0)
                GLES20.glViewport(0, 0, output.width, output.height)
                val aspect = output.width.toFloat() / output.height
                useVideo(minOf(1f, aspect / videoAspect), minOf(1f, videoAspect / aspect), output.mode, aspect)
                quad(program)
                output.pending = true
                check(EGL14.eglSwapBuffers(display, output.window))
            } catch (error: Exception) {
                DebugLog.add("MOTION output lost ${error.javaClass.simpleName}")
                EGL14.eglMakeCurrent(display, anchor, anchor, context)
                EGL14.eglDestroySurface(display, output.window)
                output.surface.release()
                iterator.remove()
            }
        }
        EGL14.eglMakeCurrent(display, anchor, anchor, context)
    }

    private fun createProgram(fragmentSource: String): Int {
        fun compile(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            check(status[0] != 0) { GLES20.glGetShaderInfoLog(shader) }
            return shader
        }
        val vertex = compile(GLES20.GL_VERTEX_SHADER, """
            attribute vec2 position;
            attribute vec2 uv;
            varying vec2 coord;
            void main() { gl_Position = vec4(position, 0.0, 1.0); coord = uv; }
        """.trimIndent())
        val fragment = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val result = GLES20.glCreateProgram()
        GLES20.glAttachShader(result, vertex)
        GLES20.glAttachShader(result, fragment)
        GLES20.glLinkProgram(result)
        val status = IntArray(1)
        GLES20.glGetProgramiv(result, GLES20.GL_LINK_STATUS, status, 0)
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        check(status[0] != 0) { GLES20.glGetProgramInfoLog(result) }
        return result
    }

    fun close() {
        closed = true
        handler.post {
            runCatching {
                EGL14.eglMakeCurrent(display, anchor, anchor, context)
                input?.setOnFrameAvailableListener(null)
                decoderSurface?.release()
                input?.release()
                outputs.values.forEach { EGL14.eglDestroySurface(display, it.window); it.surface.release() }
                outputs.clear()
                if (program != 0) GLES20.glDeleteProgram(program)
                if (blurProgram != 0) GLES20.glDeleteProgram(blurProgram)
                GLES20.glDeleteFramebuffers(2, blurBuffers, 0)
                GLES20.glDeleteTextures(2, blurTextures, 0)
                if (texture != 0) GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(display, anchor)
                EGL14.eglDestroyContext(display, context)
                EGL14.eglTerminate(display)
            }
            thread.quitSafely()
        }
    }

    companion object {
        private val VIDEO_FRAGMENT = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES video;
            uniform sampler2D softened;
            uniform mat4 transform;
            uniform vec2 crop;
            uniform float mode;
            uniform float artFraction;
            varying vec2 coord;
            vec3 backdrop(vec2 p) {
                vec3 color = texture2D(softened, p).rgb;
                float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
                return (clamp(mix(vec3(luma), color, 2.5), 0.0, 1.0) * 0.63 + 0.1) * 0.6;
            }
            void main() {
                vec2 p = (coord - 0.5) * crop + 0.5;
                vec2 b = (coord - 0.5) * crop / 1.3 + 0.5;
                vec3 color;
                if (mode > 1.5) {
                    vec3 base = backdrop(b);
                    float y = 1.0 - (1.0 - coord.y) / artFraction;
                    vec2 artwork = vec2(coord.x, clamp(y, 0.0, 1.0));
                    vec3 frame = texture2D(video, (transform * vec4(artwork, 0.0, 1.0)).xy).rgb;
                    color = mix(base, frame, smoothstep(0.0, 0.2, y));
                } else if (mode > 0.5) {
                    color = backdrop(b);
                } else {
                    color = texture2D(video, (transform * vec4(p, 0.0, 1.0)).xy).rgb;
                }
                gl_FragColor = vec4(color, 1.0);
            }
        """.trimIndent()
        private val BLUR_FRAGMENT = """
            precision mediump float;
            uniform sampler2D image;
            uniform vec2 stepSize;
            varying vec2 coord;
            void main() {
                vec3 color = texture2D(image, coord).rgb * 0.227027;
                color += texture2D(image, coord + stepSize * 1.384615).rgb * 0.316216;
                color += texture2D(image, coord - stepSize * 1.384615).rgb * 0.316216;
                color += texture2D(image, coord + stepSize * 3.230769).rgb * 0.070270;
                color += texture2D(image, coord - stepSize * 3.230769).rgb * 0.070270;
                gl_FragColor = vec4(color, 1.0);
            }
        """.trimIndent()
    }
}
