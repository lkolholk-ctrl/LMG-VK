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
import android.os.SystemClock
import android.view.Surface
import com.lmg.vk.debug.DebugLog
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

internal class MotionArtworkRenderer(
    private val density: Float,
    private val onSurface: (Surface) -> Unit,
    private val onFailure: (Exception) -> Unit,
) {
    private data class Output(val window: EGLSurface, val surface: Surface,
        var width: Int, var height: Int, val mode: Int, var presentation: Float,
        val frames: MotionArtworkFrameDelivery)
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
    private var stripProgram = 0
    private val blurTextures = IntArray(5)
    private val blurBuffers = IntArray(5)
    private var linearStorage = false
    private var stripWidth = 0
    private var stripHeight = 0
    private var hasFrame = false
    private var frameVersion = 0L
    private var ambientVersion = -1L
    private var portraitVersion = -1L
    private var portraitLayout: MotionBackdropLayout? = null
    private var videoAspect = 1f
    private val matrix = FloatArray(16)
    private val vertices = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f,
            -1f, 1f, 0f, 1f, 1f, 1f, 1f, 1f)); position(0)
    }
    @Volatile private var closed = false
    @Volatile private var decodedFrames = 0L
    @Volatile private var submittedFrames = 0L
    @Volatile private var lastDecodedAt = 0L
    @Volatile private var lastSwapMs = 0L
    @Volatile private var stage = "initializing"
    @Volatile private var outputCount = 0
    private var inputUpdatePosted = false

    fun diagnostic(): String = "decoded=$decodedFrames submitted=$submittedFrames outputs=$outputCount " +
        "inputAgeMs=${if (lastDecodedAt == 0L) -1 else SystemClock.uptimeMillis() - lastDecodedAt} " +
        "stage=$stage swapMs=$lastSwapMs"

    init { submit { initialize() } }

    private fun submit(block: () -> Unit) {
        handler.post {
            if (!closed) try { block() } catch (error: Exception) {
                main.post { if (!closed) onFailure(error) }
            }
        }
    }

    fun attach(surface: SurfaceTexture, width: Int, height: Int, mode: Int, presentation: Float,
        onFrameAvailable: () -> Unit) = submit {
        outputs[surface]?.let {
            it.width = width
            it.height = height
            it.presentation = presentation
            it.frames.invalidate()
            draw()
            return@submit
        }
        val windowSurface = Surface(surface)
        val window = EGL14.eglCreateWindowSurface(display, config, windowSurface, intArrayOf(EGL14.EGL_NONE), 0)
        if (window == EGL14.EGL_NO_SURFACE) {
            windowSurface.release()
            error("Motion window unavailable")
        }
        val notificationPending = AtomicBoolean(false)
        val frames = MotionArtworkFrameDelivery {
            if (notificationPending.compareAndSet(false, true)) main.post {
                notificationPending.set(false)
                if (!closed) onFrameAvailable()
            }
        }
        outputs[surface] = Output(window, windowSurface, width, height, mode, presentation, frames)
        outputCount = outputs.size
        DebugLog.add("MOTION output attach mode=$mode size=${width}x$height outputs=${outputs.size}")
        draw()
    }

    fun presentation(surface: SurfaceTexture, fraction: Float) = submit {
        outputs[surface]?.let {
            if (it.presentation != fraction) {
                it.presentation = fraction
                it.frames.invalidate()
                draw()
            }
        }
    }

    fun resize(surface: SurfaceTexture, width: Int, height: Int) = submit {
        outputs[surface]?.let { it.width = width; it.height = height; it.frames.invalidate() }
        draw()
    }

    fun detach(surface: SurfaceTexture, releaseTexture: Boolean) {
        if (!handler.post {
            outputs.remove(surface)?.let {
                EGL14.eglMakeCurrent(display, anchor, anchor, context)
                EGL14.eglDestroySurface(display, it.window)
                it.surface.release()
            }
            outputCount = outputs.size
            if (releaseTexture) surface.release()
        } && releaseTexture) surface.release()
    }

    fun videoSize(width: Int, height: Int) = submit {
        if (width > 0 && height > 0) {
            videoAspect = width.toFloat() / height
            portraitVersion = -1L
        }
    }

    private fun initialize() {
        display = MotionEglDisplay.acquire()
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(display, intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_NONE), 0, configs, 0, 1, count, 0))
        config = requireNotNull(configs[0])
        val interval = IntArray(1)
        check(EGL14.eglGetConfigAttrib(display, config, EGL14.EGL_MIN_SWAP_INTERVAL, interval, 0))
        DebugLog.add("MOTION output minimumSwapInterval=${interval[0]}")
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
        stripProgram = createProgram(STRIP_BLUR_FRAGMENT)
        val extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS).orEmpty().split(' ').toSet()
        linearStorage = "GL_OES_texture_half_float" in extensions &&
            "GL_OES_texture_half_float_linear" in extensions &&
            "GL_EXT_color_buffer_half_float" in extensions
        GLES20.glGenTextures(blurTextures.size, blurTextures, 0)
        GLES20.glGenFramebuffers(blurBuffers.size, blurBuffers, 0)
        for (index in blurTextures.indices) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTextures[index])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }
        allocate(0, 32, 32)
        allocate(1, 32, 32)
        for (index in 2..4) allocate(index, 1, 1)
        DebugLog.add("MOTION renderer mirroredBottomStrip linearStorage=$linearStorage")
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        input = SurfaceTexture(texture).apply {
            setOnFrameAvailableListener({
                if (!closed && !inputUpdatePosted) {
                    inputUpdatePosted = true
                    submit {
                        inputUpdatePosted = false
                        stage = "acquire-input"
                        check(EGL14.eglMakeCurrent(display, anchor, anchor, context))
                        updateTexImage()
                        getTransformMatrix(matrix)
                        if (!hasFrame) DebugLog.add("MOTION decoder first_frame")
                        hasFrame = true
                        frameVersion++
                        decodedFrames++
                        lastDecodedAt = SystemClock.uptimeMillis()
                        outputs.values.forEach { it.frames.invalidate() }
                        draw()
                    }
                }
            }, handler)
        }
        stage = "await-input"
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

    private fun allocate(index: Int, width: Int, height: Int) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTextures[index])
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
            GLES20.GL_RGBA, if (linearStorage && index < 2) 0x8D61 else GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, blurBuffers[index])
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, blurTextures[index], 0)
        if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE && linearStorage) {
            linearStorage = false
            ambientVersion = -1L
            portraitVersion = -1L
            for (slot in blurTextures.indices) {
                if (slot < 2) allocate(slot, 32, 32)
                else if (stripHeight > 0) allocate(slot, stripWidth, stripHeight)
                else allocate(slot, 1, 1)
            }
        }
        check(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE)
    }

    private fun useVideo(cropX: Float, cropY: Float, mode: Int, aspect: Float = 1f, presentation: Float = 0f,
        layout: MotionBackdropLayout? = null) {
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "video"), 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, if (mode == 1 || mode == 2) blurTextures[0] else 0)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "ambient"), 1)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, if (mode == 2) blurTextures[4] else 0)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "strip"), 2)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "transform"), 1, false, matrix, 0)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "crop"), cropX, cropY)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "mode"), mode.toFloat())
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "artFraction"), layout?.videoFraction ?: 1f)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "artCrop"),
            minOf(1f, 0.75f / videoAspect), minOf(1f, videoAspect / 0.75f))
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "stripFraction"), layout?.stripFraction ?: 0.125f)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "overlap"), layout?.overlapFraction ?: 0.1f)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "screenAspect"), aspect)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "presentation"), presentation)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "linearStorage"), if (linearStorage) 1f else 0f)
    }

    private fun soften() {
        if (ambientVersion == frameVersion) return
        check(EGL14.eglMakeCurrent(display, anchor, anchor, context))
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, blurBuffers[0])
        GLES20.glViewport(0, 0, 32, 32)
        useVideo(1f, 1f, 3)
        quad(program)
        GLES20.glUseProgram(blurProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(blurProgram, "image"), 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(blurProgram, "linearStorage"), if (linearStorage) 1f else 0f)
        for (pass in 0 until 6) {
            val source = pass % 2
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, blurBuffers[1 - source])
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTextures[source])
            GLES20.glUniform2f(GLES20.glGetUniformLocation(blurProgram, "stepSize"),
                if (source == 0) 1f / 32 else 0f, if (source == 1) 1f / 32 else 0f)
            quad(blurProgram)
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        ambientVersion = frameVersion
    }

    private fun softenPortrait(layout: MotionBackdropLayout) {
        if (portraitVersion == frameVersion && portraitLayout == layout) return
        check(EGL14.eglMakeCurrent(display, anchor, anchor, context))
        if (stripWidth != layout.stripWidth || stripHeight != layout.stripHeight) {
            stripWidth = layout.stripWidth
            stripHeight = layout.stripHeight
            for (index in 2..4) allocate(index, stripWidth, stripHeight)
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, blurBuffers[2])
        GLES20.glViewport(0, 0, stripWidth, stripHeight)
        useVideo(1f, 1f, 4, layout = layout)
        quad(program)
        GLES20.glUseProgram(stripProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(stripProgram, "image"), 0)
        for (pass in 0..1) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, blurBuffers[3 + pass])
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTextures[2 + pass])
            GLES20.glUniform2f(GLES20.glGetUniformLocation(stripProgram, "stepSize"),
                if (pass == 0) 1f / stripWidth else 0f, if (pass == 1) 1f / stripHeight else 0f)
            quad(stripProgram)
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        portraitVersion = frameVersion
        portraitLayout = layout
    }

    private fun draw() {
        if (!hasFrame) return
        stage = "render"
        if (outputs.values.any { it.mode != 0 && it.frames.needsFrame }) soften()
        val iterator = outputs.values.iterator()
        while (iterator.hasNext()) {
            val output = iterator.next()
            if (output.width <= 0 || output.height <= 0) continue
            if (!output.frames.needsFrame) continue
            try {
                val aspect = output.width.toFloat() / output.height
                val layout = if (output.mode == 2) MotionBackdropLayout.create(output.width, output.height, density) else null
                if (layout != null) {
                    softenPortrait(layout)
                    soften()
                }
                check(EGL14.eglMakeCurrent(display, output.window, output.window, context))
                check(EGL14.eglSwapInterval(display, 0))
                GLES20.glViewport(0, 0, output.width, output.height)
                useVideo(minOf(1f, aspect / videoAspect), minOf(1f, videoAspect / aspect), output.mode, aspect, output.presentation, layout)
                quad(program)
                stage = if (output.mode == 0) "swap-thumbnail" else "swap-background"
                val beforeSwap = SystemClock.uptimeMillis()
                check(EGL14.eglSwapBuffers(display, output.window))
                lastSwapMs = SystemClock.uptimeMillis() - beforeSwap
                submittedFrames++
                output.frames.submitted()
            } catch (error: Exception) {
                DebugLog.add("MOTION output lost mode=${output.mode} egl=0x${EGL14.eglGetError().toString(16)} " +
                    "${error.javaClass.simpleName}: ${error.message}")
                EGL14.eglMakeCurrent(display, anchor, anchor, context)
                EGL14.eglDestroySurface(display, output.window)
                output.surface.release()
                iterator.remove()
                outputCount = outputs.size
            }
        }
        EGL14.eglMakeCurrent(display, anchor, anchor, context)
        stage = "await-input"
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
        val fragment = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSource.replace("COLOR_FUNCTIONS", COLOR_FUNCTIONS))
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
                if (stripProgram != 0) GLES20.glDeleteProgram(stripProgram)
                GLES20.glDeleteFramebuffers(blurBuffers.size, blurBuffers, 0)
                GLES20.glDeleteTextures(blurTextures.size, blurTextures, 0)
                if (texture != 0) GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(display, anchor)
                EGL14.eglDestroyContext(display, context)
            }
            if (display != EGL14.EGL_NO_DISPLAY) MotionEglDisplay.release()
            thread.quitSafely()
        }
    }

    companion object {
        private val COLOR_FUNCTIONS = """
            vec3 toLinear(vec3 color) {
                return mix(color / 12.92, pow((color + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), color));
            }
            vec3 toDisplay(vec3 color) {
                color = max(color, vec3(0.0));
                return mix(color * 12.92, 1.055 * pow(color, vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), color));
            }
            vec3 unpackColor(vec3 color) {
                return linearStorage > 0.5 ? color : toLinear(color);
            }
            vec3 readColor(sampler2D source, vec2 uv, vec2 pixel) {
                if (linearStorage > 0.5) return texture2D(source, uv).rgb;
                vec2 position = uv / pixel - 0.5;
                vec2 origin = (floor(position) + 0.5) * pixel;
                vec2 fraction = fract(position);
                vec3 a = toLinear(texture2D(source, origin).rgb);
                vec3 b = toLinear(texture2D(source, origin + vec2(pixel.x, 0.0)).rgb);
                vec3 c = toLinear(texture2D(source, origin + vec2(0.0, pixel.y)).rgb);
                vec3 d = toLinear(texture2D(source, origin + pixel).rgb);
                return mix(mix(a, b, fraction.x), mix(c, d, fraction.x), fraction.y);
            }
            vec3 packColor(vec3 color) {
                return linearStorage > 0.5 ? color : toDisplay(color);
            }
            float feather(float start, float end, float value) {
                float t = clamp((value - start) / (end - start), 0.0, 1.0);
                return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
            }
        """.trimIndent()
        private val VIDEO_FRAGMENT = """
            #extension GL_OES_EGL_image_external : require
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            uniform samplerExternalOES video;
            uniform sampler2D ambient;
            uniform sampler2D strip;
            uniform mat4 transform;
            uniform vec2 crop;
            uniform vec2 artCrop;
            uniform float stripFraction;
            uniform float overlap;
            uniform float screenAspect;
            uniform float mode;
            uniform float artFraction;
            uniform float presentation;
            uniform float linearStorage;
            varying vec2 coord;
            COLOR_FUNCTIONS
            vec3 videoColor(vec2 uv) {
                return texture2D(video, (transform * vec4(uv, 0.0, 1.0)).xy).rgb;
            }
            vec2 artworkCoord() {
                vec2 uv = vec2(coord.x, 1.0 - (1.0 - coord.y) / artFraction);
                return clamp((uv - 0.5) * artCrop + 0.5, 0.0, 1.0);
            }
            vec3 stripColor(vec2 uv) {
                vec3 color = texture2D(strip, uv).rgb;
                return color * (1.0 - 89.0 / 255.0) * (1.0 - 10.0 / 255.0) + 10.0 / 255.0;
            }
            float gradientAlpha(float t) {
                if (t < 0.7) return (204.0 / 255.0) * clamp(t / 0.7, 0.0, 1.0);
                if (t < 0.9) return mix(204.0 / 255.0, 242.0 / 255.0, (t - 0.7) / 0.2);
                return mix(242.0 / 255.0, 1.0, clamp((t - 0.9) / 0.1, 0.0, 1.0));
            }
            vec3 portraitColor(float depth) {
                vec3 sharp = depth <= artFraction ? videoColor(artworkCoord()) : vec3(0.0);
                float legibility = depth < 0.2 ? 0.2 * (1.0 - depth / 0.2) : (depth - 0.2) / 0.8;
                vec3 color = mix(sharp, vec3(23.0 / 255.0), legibility * (179.0 / 255.0));
                float localY = depth - (artFraction - overlap);
                if (localY >= 0.0) {
                    float radius = screenAspect * screenAspect / (4.0 * overlap) + overlap * 0.25;
                    float distance = length(vec2((coord.x - 0.5) * screenAspect, localY - overlap * 0.5 + radius));
                    float mask = clamp((distance - radius) / (overlap * 0.5), 0.0, 1.0);
                    float lowerHeight = max(1.0 - artFraction + overlap, 0.001);
                    float y = 1.0 + (artFraction - depth) / lowerHeight;
                    float mirrored = 1.0 - abs(mod(y, 2.0) - 1.0);
                    vec2 uv = vec2((coord.x + 0.125) / 1.25, 1.0 - mirrored);
                    color = mix(color, stripColor(uv), mask);
                }
                if (depth > artFraction) {
                    vec3 dominant = (stripColor(vec2(0.25, 0.25)) + stripColor(vec2(0.75, 0.25))
                        + stripColor(vec2(0.25, 0.75)) + stripColor(vec2(0.75, 0.75))) * 0.25;
                    float t = (depth - artFraction) / max((1.0 - artFraction) * 0.4, 0.001);
                    color = mix(color, dominant, gradientAlpha(t));
                }
                return color;
            }
            void main() {
                vec3 color;
                if (mode > 3.5) {
                    vec2 uv = (vec2(coord.x, coord.y * stripFraction) - 0.5) * artCrop + 0.5;
                    vec3 sampleColor = videoColor(uv);
                    float luma = dot(sampleColor, vec3(0.213, 0.715, 0.072));
                    color = clamp(mix(vec3(luma), sampleColor, 1.4), 0.0, 1.0);
                } else if (mode > 2.5) {
                    color = packColor(toLinear(videoColor(coord)));
                } else if (mode > 1.5) {
                    float depth = 1.0 - coord.y;
                    vec3 base = readColor(ambient, coord, vec2(1.0 / 32.0)) * 0.46;
                    float luma = dot(base, vec3(0.2126, 0.7152, 0.0722));
                    base += vec3(max(0.0, 0.012 - luma));
                    color = toDisplay(mix(base, toLinear(portraitColor(depth)), presentation));
                    if (depth > artFraction - overlap) {
                        float noise = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
                        color += vec3((noise - 0.5) / 255.0);
                    }
                } else if (mode > 0.5) {
                    vec2 b = (coord - 0.5) * crop / 1.3 + 0.5;
                    vec3 blurred = toDisplay(readColor(ambient, b, vec2(1.0 / 32.0)));
                    float luma = dot(blurred, vec3(0.2126, 0.7152, 0.0722));
                    color = (clamp(mix(vec3(luma), blurred, 2.5), 0.0, 1.0) * 0.63 + 0.1) * 0.6;
                } else {
                    color = videoColor((coord - 0.5) * crop + 0.5);
                }
                gl_FragColor = vec4(color, 1.0);
            }
        """.trimIndent()
        private val BLUR_FRAGMENT = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            uniform sampler2D image;
            uniform vec2 stepSize;
            uniform float linearStorage;
            varying vec2 coord;
            COLOR_FUNCTIONS
            void main() {
                vec3 color = readColor(image, coord, vec2(1.0 / 32.0)) * 0.227027;
                color += readColor(image, coord + stepSize * 1.384615, vec2(1.0 / 32.0)) * 0.316216;
                color += readColor(image, coord - stepSize * 1.384615, vec2(1.0 / 32.0)) * 0.316216;
                color += readColor(image, coord + stepSize * 3.230769, vec2(1.0 / 32.0)) * 0.070270;
                color += readColor(image, coord - stepSize * 3.230769, vec2(1.0 / 32.0)) * 0.070270;
                gl_FragColor = vec4(packColor(color), 1.0);
            }
        """.trimIndent()
        private val STRIP_BLUR_FRAGMENT = """
            precision highp float;
            uniform sampler2D image;
            uniform vec2 stepSize;
            varying vec2 coord;
            void main() {
                vec3 color = vec3(0.0);
                float total = 0.0;
                for (int tap = -25; tap <= 25; tap++) {
                    float distance = float(tap) / 10.6;
                    float weight = exp(-0.5 * distance * distance);
                    color += texture2D(image, coord + stepSize * float(tap)).rgb * weight;
                    total += weight;
                }
                gl_FragColor = vec4(color / total, 1.0);
            }
        """.trimIndent()
    }

}

private object MotionEglDisplay {
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var users = 0

    @Synchronized
    fun acquire(): EGLDisplay {
        if (users == 0) {
            val next = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(EGL14.eglInitialize(next, IntArray(2), 0, IntArray(2), 0))
            display = next
        }
        users++
        return display
    }

    @Synchronized
    fun release() {
        check(users > 0)
        users--
        if (users == 0) {
            EGL14.eglTerminate(display)
            display = EGL14.EGL_NO_DISPLAY
        }
    }
}
