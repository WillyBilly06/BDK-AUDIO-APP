package com.example.myspeaker

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Premium LED effect preview matching ESP32 16x16 matrix aesthetics.
 * Uses downscale + blur + upscale for frosted glass effect.
 */
class LedEffectPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentEffectId: Int = 0
    private var animationPhase: Float = 0f
    private var frameCount: Int = 0
    private var animator: ValueAnimator? = null
    
    // Downscale blur (render small, blur, scale up)
    private val BLUR_SCALE = 8
    private var effectBitmap: Bitmap? = null
    private var effectCanvas: Canvas? = null
    private var blurredBitmap: Bitmap? = null
    private var scaledWidth: Int = 0
    private var scaledHeight: Int = 0
    private var scaleMatrix: Matrix = Matrix()
    
    // RenderScript
    private var renderScript: RenderScript? = null
    private var blurScript: ScriptIntrinsicBlur? = null
    
    // Paints
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    
    // Simulated audio levels (animated)
    private var bassLevel = 0.3f
    private var midLevel = 0.3f
    private var highLevel = 0.2f
    private var beatPhase = 0f
    
    // Effect state
    private val barHeights = FloatArray(16) { Random.nextFloat() * 0.3f + 0.1f }
    private val barPeaks = FloatArray(16) { 0f }
    private val barTargets = FloatArray(16) { Random.nextFloat() * 0.5f + 0.2f }
    
    // Ripple state
    private data class Ripple(var radius: Float, var hue: Float, var active: Boolean)
    private val ripples = Array(5) { Ripple(0f, 0f, false) }
    
    // Fire heat map
    private val heatMap = FloatArray(16 * 16) { 0f }
    
    // Matrix drops
    private data class Drop(var y: Float, var speed: Float, var length: Int, var active: Boolean)
    private val drops = Array(16) { Drop(-5f, 0.3f, 5, false) }
    
    // Stars
    private data class Star(var x: Int, var y: Int, var brightness: Float, var delta: Float)
    private val stars = Array(40) { Star(Random.nextInt(16), Random.nextInt(16), Random.nextFloat() * 150 + 50, (Random.nextFloat() - 0.5f) * 10f) }
    
    // Particles
    private data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var hue: Float, var life: Float, var active: Boolean)
    private val particles = Array(60) { Particle(0f, 0f, 0f, 0f, 0f, 0f, false) }
    
    // Blobs for lava lamp
    private data class Blob(var x: Float, var y: Float, var radius: Float, var vx: Float, var vy: Float, var hue: Float)
    private val blobs = Array(4) { Blob(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 0.08f + 0.05f, (Random.nextFloat() - 0.5f) * 0.003f, (Random.nextFloat() - 0.5f) * 0.002f, Random.nextFloat() * 40f + 10f) }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        try {
            renderScript = RenderScript.create(context)
            blurScript = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript))
            blurScript?.setRadius(25f)
        } catch (e: Exception) { }
    }
    
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Request layout after a brief delay to ensure parent has measured
        post { requestLayout() }
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Match the parent's size exactly
        val parent = parent as? android.view.ViewGroup
        if (parent != null && parent.measuredHeight > 0) {
            setMeasuredDimension(
                MeasureSpec.getSize(widthMeasureSpec),
                parent.measuredHeight
            )
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            effectBitmap?.recycle()
            blurredBitmap?.recycle()
            
            scaledWidth = (w / BLUR_SCALE).coerceAtLeast(1)
            scaledHeight = (h / BLUR_SCALE).coerceAtLeast(1)
            
            effectBitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
            effectCanvas = Canvas(effectBitmap!!)
            blurredBitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
            
            scaleMatrix.reset()
            scaleMatrix.setScale(BLUR_SCALE.toFloat(), BLUR_SCALE.toFloat())
        }
    }
    
    fun setEffect(effectId: Int) {
        if (currentEffectId == effectId) return
        currentEffectId = effectId
        frameCount = 0
        initEffect()
        startAnimation()
    }
    
    private fun initEffect() {
        // Reset effect-specific state
        ripples.forEach { it.active = false }
        particles.forEach { it.active = false }
        heatMap.fill(0f)
        drops.forEach { it.active = false; it.y = -5f }
    }
    
    private fun startAnimation() {
        animator?.cancel()
        if (currentEffectId == 255) {
            invalidate()
            return
        }
        
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                animationPhase = it.animatedValue as Float
                frameCount++
                updateSimulatedAudio()
                updateEffectState()
                invalidate()
            }
            start()
        }
    }
    
    private fun updateSimulatedAudio() {
        // Simulate realistic audio levels
        val t = frameCount * 0.05f
        bassLevel = 0.3f + 0.35f * sin(t * 0.7f).toFloat()
        midLevel = 0.25f + 0.25f * sin(t * 1.1f + 1f).toFloat()
        highLevel = 0.15f + 0.2f * sin(t * 1.5f + 2f).toFloat()
        beatPhase = (beatPhase + 0.033f) % 1f
    }
    
    private fun updateEffectState() {
        // Update bar targets periodically
        for (i in barHeights.indices) {
            barHeights[i] += (barTargets[i] - barHeights[i]) * 0.15f
            if (Random.nextFloat() < 0.08f) {
                barTargets[i] = 0.1f + (bassLevel * 0.5f + midLevel * 0.3f + highLevel * 0.2f) * Random.nextFloat() * 1.2f
            }
            // Decay peaks
            if (barHeights[i] > barPeaks[i]) {
                barPeaks[i] = barHeights[i]
            } else {
                barPeaks[i] *= 0.97f
            }
        }
        
        // Stars twinkle
        stars.forEach { star ->
            star.brightness += star.delta
            if (star.brightness > 250 || star.brightness < 30) star.delta = -star.delta
        }
        
        // Blobs move
        blobs.forEach { b ->
            b.x += b.vx
            b.y += b.vy
            if (b.x < 0.1f || b.x > 0.9f) b.vx *= -1
            if (b.y < 0.1f || b.y > 0.9f) b.vy *= -1
            b.x = b.x.coerceIn(0.05f, 0.95f)
            b.y = b.y.coerceIn(0.05f, 0.95f)
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        
        val eBitmap = effectBitmap ?: return
        val eCanvas = effectCanvas ?: return
        val bBitmap = blurredBitmap ?: return
        
        eBitmap.eraseColor(Color.TRANSPARENT)
        
        val sw = scaledWidth.toFloat()
        val sh = scaledHeight.toFloat()
        
        // Draw effect
        when (currentEffectId) {
            0 -> drawSpectrumBars(eCanvas, sw, sh)
            1 -> drawBeatPulse(eCanvas, sw, sh)
            2 -> drawRipple(eCanvas, sw, sh)
            3 -> drawFire(eCanvas, sw, sh)
            4 -> drawPlasma(eCanvas, sw, sh)
            5 -> drawMatrixRain(eCanvas, sw, sh)
            6 -> drawVuMeter(eCanvas, sw, sh)
            7 -> drawStarfield(eCanvas, sw, sh)
            8 -> drawWave(eCanvas, sw, sh)
            9 -> drawFireworks(eCanvas, sw, sh)
            10 -> drawRainbowWave(eCanvas, sw, sh)
            11 -> drawParticleBurst(eCanvas, sw, sh)
            12 -> drawKaleidoscope(eCanvas, sw, sh)
            13 -> drawFrequencySpiral(eCanvas, sw, sh)
            14 -> drawBassReactor(eCanvas, sw, sh)
            15 -> drawMeteorShower(eCanvas, sw, sh)
            16 -> drawBreathing(eCanvas, sw, sh)
            17 -> drawDnaHelix(eCanvas, sw, sh)
            18 -> drawAudioScope(eCanvas, sw, sh)
            19 -> drawBouncingBalls(eCanvas, sw, sh)
            20 -> drawLavaLamp(eCanvas, sw, sh)
            21 -> drawAmbient(eCanvas, sw, sh)
            255 -> drawOff(eCanvas, sw, sh)
            else -> drawGeneric(eCanvas, sw, sh)
        }
        
        // Apply blur and scale up
        if (renderScript != null && blurScript != null) {
            try {
                val input = Allocation.createFromBitmap(renderScript, eBitmap)
                val output = Allocation.createFromBitmap(renderScript, bBitmap)
                blurScript?.setInput(input)
                blurScript?.forEach(output)
                output.copyTo(bBitmap)
                input.destroy()
                output.destroy()
                canvas.drawBitmap(bBitmap, scaleMatrix, bitmapPaint)
            } catch (e: Exception) {
                canvas.drawBitmap(eBitmap, scaleMatrix, bitmapPaint)
            }
        } else {
            canvas.drawBitmap(eBitmap, scaleMatrix, bitmapPaint)
        }
    }
    
    // ========== ESP32-MATCHING EFFECTS ==========
    
    // Effect 0: Spectrum Bars - 16 band analyzer with peaks
    private fun drawSpectrumBars(c: Canvas, w: Float, h: Float) {
        val barW = w / 16f
        for (i in 0 until 16) {
            val level = if (i < barHeights.size) barHeights[i] else 0.3f
            val barH = h * level.coerceIn(0f, 1f)
            val x = i * barW
            
            // Color gradient: green -> yellow -> red based on height
            val hue = (120f - level * 120f).coerceIn(0f, 120f)
            paint.color = Color.HSVToColor(255, floatArrayOf(hue, 1f, 1f))
            c.drawRect(x, h - barH, x + barW - 1, h, paint)
            
            // Peak
            val peakY = h - h * barPeaks[i].coerceIn(0f, 1f)
            paint.color = Color.WHITE
            c.drawRect(x, peakY - 2, x + barW - 1, peakY, paint)
        }
    }
    
    // Effect 1: Beat Pulse - Full matrix pulse
    private fun drawBeatPulse(c: Canvas, w: Float, h: Float) {
        val beat = (frameCount % 30) < 5
        val pulseLevel = if (beat) 1f else (1f - (frameCount % 30) / 30f) * 0.3f
        val hue = (frameCount * 3f) % 360f
        
        // Background
        paint.color = Color.HSVToColor((30 * bassLevel).toInt(), floatArrayOf((hue + 128) % 360, 1f, 0.15f))
        c.drawRect(0f, 0f, w, h, paint)
        
        // Pulse overlay
        paint.color = Color.HSVToColor((pulseLevel * 255).toInt(), floatArrayOf(hue, 1f, 1f))
        c.drawRect(0f, 0f, w, h, paint)
    }
    
    // Effect 2: Ripple - Concentric rings from center
    private fun drawRipple(c: Canvas, w: Float, h: Float) {
        val cx = w / 2
        val cy = h / 2
        val maxR = maxOf(w, h) * 0.7f
        
        // Spawn ripple periodically
        if (frameCount % 25 == 0) {
            for (r in ripples) {
                if (!r.active) {
                    r.active = true
                    r.radius = 0f
                    r.hue = Random.nextFloat() * 360f
                    break
                }
            }
        }
        
        // Fade background
        paint.color = Color.argb(20, 0, 0, 0)
        c.drawRect(0f, 0f, w, h, paint)
        
        // Draw ripples
        paint.style = Paint.Style.STROKE
        for (r in ripples) {
            if (!r.active) continue
            r.radius += 0.5f + bassLevel * 0.5f
            if (r.radius > maxR) {
                r.active = false
                continue
            }
            
            val alpha = ((1f - r.radius / maxR) * 255).toInt()
            paint.color = Color.HSVToColor(alpha, floatArrayOf(r.hue, 1f, 1f))
            paint.strokeWidth = 3f * (1f - r.radius / maxR)
            c.drawCircle(cx, cy, r.radius, paint)
        }
        paint.style = Paint.Style.FILL
    }
    
    // Effect 3: Fire - Heat simulation
    private fun drawFire(c: Canvas, w: Float, h: Float) {
        val gridW = 16
        val gridH = 16
        val cellW = w / gridW
        val cellH = h / gridH
        
        // Cooling
        val cooling = (55 - bassLevel * 30).toInt()
        for (i in heatMap.indices) {
            val cooldown = Random.nextInt(0, ((cooling * 10) / 16) + 2)
            heatMap[i] = (heatMap[i] - cooldown).coerceAtLeast(0f)
        }
        
        // Heat rises
        for (y in 0 until gridH - 1) {
            for (x in 0 until gridW) {
                val idx = y * gridW + x
                val belowIdx = (y + 1) * gridW + x
                heatMap[idx] = (heatMap[belowIdx] + heatMap[belowIdx] + heatMap[(y + 1) * gridW + ((x + 1) % gridW)]) / 3f
            }
        }
        
        // Sparking at bottom
        val sparking = (80 + bassLevel * 120).toInt()
        for (x in 0 until gridW) {
            if (Random.nextInt(255) < sparking) {
                val idx = (gridH - 1) * gridW + x
                heatMap[idx] = (heatMap[idx] + Random.nextInt(160, 255)).coerceAtMost(255f)
            }
        }
        
        // Draw
        for (y in 0 until gridH) {
            for (x in 0 until gridW) {
                val heat = heatMap[y * gridW + x].toInt()
                val color = when {
                    heat < 85 -> Color.rgb((heat * 3).coerceAtMost(255), 0, 0)
                    heat < 170 -> Color.rgb(255, ((heat - 85) * 3).coerceAtMost(255), 0)
                    else -> Color.rgb(255, 255, ((heat - 170) * 3).coerceAtMost(255))
                }
                paint.color = color
                c.drawRect(x * cellW, y * cellH, (x + 1) * cellW, (y + 1) * cellH, paint)
            }
        }
    }
    
    // Effect 4: Plasma - Animated plasma patterns
    private fun drawPlasma(c: Canvas, w: Float, h: Float) {
        val gridSize = 8
        val cellW = w / gridSize
        val cellH = h / gridSize
        val time = frameCount * 0.1f
        
        for (y in 0 until gridSize) {
            for (x in 0 until gridSize) {
                val v1 = sin(x * 0.5f + time)
                val v2 = sin(y * 0.4f + time * 0.7f)
                val v3 = sin((x + y) * 0.3f + time * 0.5f)
                val v4 = sin(sqrt((x * x + y * y).toFloat()) * 0.4f - time)
                val value = ((v1 + v2 + v3 + v4) / 4f + 1f) / 2f
                
                val hue = ((value * 360 + frameCount * 2) % 360)
                val brightness = (128 + value * 127 * (1f + bassLevel)).coerceAtMost(255f)
                paint.color = Color.HSVToColor(255, floatArrayOf(hue, 1f, brightness / 255f))
                c.drawRect(x * cellW, y * cellH, (x + 1) * cellW, (y + 1) * cellH, paint)
            }
        }
    }
    
    // Effect 5: Matrix Rain - Digital rain
    private fun drawMatrixRain(c: Canvas, w: Float, h: Float) {
        val cols = 16
        val colW = w / cols
        
        // Fade
        paint.color = Color.argb(50, 0, 0, 0)
        c.drawRect(0f, 0f, w, h, paint)
        
        val speedMod = 0.3f + bassLevel * 0.5f + midLevel * 0.3f
        
        for (x in 0 until cols) {
            val drop = drops[x]
            if (!drop.active) {
                if (Random.nextFloat() < 0.05f + highLevel * 0.1f) {
                    drop.active = true
                    drop.y = -Random.nextFloat() * 8
                    drop.speed = 0.2f + Random.nextFloat() * 0.3f
                    drop.length = 3 + Random.nextInt(8)
                }
            }
            
            if (drop.active) {
                drop.y += drop.speed * speedMod * 1.5f
                
                // Draw trail
                for (i in 0 until drop.length) {
                    val y = drop.y - i
                    if (y >= 0 && y < 16) {
                        val brightness = 255 - (i * 255 / drop.length)
                        val color = if (i == 0) Color.rgb(200, 255, 200) else Color.rgb(0, brightness, 0)
                        paint.color = color
                        val py = y / 16f * h
                        c.drawRect(x * colW, py, (x + 1) * colW - 1, py + h / 16, paint)
                    }
                }
                
                if (drop.y - drop.length > 16) drop.active = false
            }
        }
    }
    
    // Effect 6: VU Meter - Stereo bars
    private fun drawVuMeter(c: Canvas, w: Float, h: Float) {
        val level = (bassLevel * 0.6f + midLevel * 0.3f + highLevel * 0.1f) * 2f
        val barH = h * level.coerceIn(0f, 1f)
        
        // Left bar (0-40%)
        val leftW = w * 0.4f
        for (y in 0 until 16) {
            val yPos = h - (y + 1) * h / 16
            if (y < (level * 16).toInt()) {
                val hue = (120f - y * 8f).coerceIn(0f, 120f)
                paint.color = Color.HSVToColor(255, floatArrayOf(hue, 1f, 1f))
                c.drawRect(0f, yPos, leftW, yPos + h / 16, paint)
            }
        }
        
        // Right bar (60-100%)
        val rightX = w * 0.6f
        val rightLevel = level * (0.9f + 0.2f * sin(frameCount * 0.1f).toFloat())
        for (y in 0 until 16) {
            val yPos = h - (y + 1) * h / 16
            if (y < (rightLevel * 16).toInt()) {
                val hue = (120f - y * 8f).coerceIn(0f, 120f)
                paint.color = Color.HSVToColor(255, floatArrayOf(hue, 1f, 1f))
                c.drawRect(rightX, yPos, w, yPos + h / 16, paint)
            }
        }
        
        // Center decoration
        val hue = frameCount % 360f
        paint.color = Color.HSVToColor(150, floatArrayOf(hue, 1f, 0.5f))
        c.drawRect(leftW, 0f, rightX, h, paint)
    }
    
    // Effect 7: Starfield - Twinkling stars
    private fun drawStarfield(c: Canvas, w: Float, h: Float) {
        // Dark blue background
        val bgLevel = (bassLevel * 20).toInt()
        paint.color = Color.HSVToColor(bgLevel, floatArrayOf(220f, 1f, 0.1f))
        c.drawRect(0f, 0f, w, h, paint)
        
        // Beat flash
        val beatFlash = if (frameCount % 40 < 3) 150f else 0f
        
        for (star in stars) {
            val brightness = (star.brightness + beatFlash).coerceIn(0f, 255f).toInt()
            paint.color = Color.rgb(brightness, brightness, brightness)
            val x = star.x / 16f * w
            val y = star.y / 16f * h
            c.drawCircle(x, y, 2f, paint)
        }
    }
    
    // Effect 8: Wave - Multiple sine waves
    private fun drawWave(c: Canvas, w: Float, h: Float) {
        paint.color = Color.argb(40, 0, 0, 0)
        c.drawRect(0f, 0f, w, h, paint)
        
        val amp1 = 3f + bassLevel * 4f
        val amp2 = 2f + midLevel * 3f
        val amp3 = 1f + highLevel * 2f
        val t = frameCount * 0.1f
        
        // Draw 3 waves
        for (x in 0 until w.toInt() step 2) {
            val xf = x.toFloat()
            val xNorm = xf / w
            
            val y1 = h / 2 + amp1 * h / 16 * sin(xNorm * 4 * Math.PI + t).toFloat()
            val y2 = h / 2 + amp2 * h / 16 * sin(xNorm * 4 * Math.PI * 1.5 + t + 1).toFloat()
            val y3 = h / 2 + amp3 * h / 16 * sin(xNorm * 4 * Math.PI * 2 + t + 2).toFloat()
            
            paint.color = Color.HSVToColor(255, floatArrayOf(0f, 1f, 1f))  // Red - bass
            c.drawCircle(xf, y1, 2f, paint)
            
            paint.color = Color.HSVToColor(255, floatArrayOf(120f, 1f, 1f))  // Green - mid
            c.drawCircle(xf, y2, 2f, paint)
            
            paint.color = Color.HSVToColor(255, floatArrayOf(240f, 1f, 1f))  // Blue - high
            c.drawCircle(xf, y3, 2f, paint)
        }
    }
    
    // Effect 9: Fireworks - Exploding particles
    private fun drawFireworks(c: Canvas, w: Float, h: Float) {
        paint.color = Color.argb(30, 0, 0, 0)
        c.drawRect(0f, 0f, w, h, paint)
        
        // Spawn explosion periodically
        if (frameCount % 45 == 0) {
            val cx = Random.nextFloat() * w
            val cy = Random.nextFloat() * h * 0.5f
            val hue = Random.nextFloat() * 360f
            for (p in particles) {
                if (!p.active) {
                    val angle = Random.nextFloat() * Math.PI.toFloat() * 2
                    val speed = 0.5f + Random.nextFloat() * 1f
                    p.x = cx
                    p.y = cy
                    p.vx = cos(angle) * speed
                    p.vy = sin(angle) * speed
                    p.hue = hue + Random.nextFloat() * 30
                    p.life = 40f + Random.nextFloat() * 30f
                    p.active = true
                }
            }
        }
        
        // Update and draw particles
        for (p in particles) {
            if (!p.active) continue
            p.x += p.vx
            p.y += p.vy
            p.vy += 0.03f  // Gravity
            p.life -= 1f
            
            if (p.life <= 0 || p.y > h) {
                p.active = false
                continue
            }
            
            val alpha = (p.life / 70f * 255).toInt().coerceIn(0, 255)
            paint.color = Color.HSVToColor(alpha, floatArrayOf(p.hue % 360f, 1f, 1f))
            c.drawCircle(p.x, p.y, 2f, paint)
        }
    }
    
    // Effect 10: Rainbow Wave
    private fun drawRainbowWave(c: Canvas, w: Float, h: Float) {
        val bands = 7
        val bandH = h / bands
        val baseHue = frameCount % 360f
        
        for (i in 0 until bands) {
            val hue = (baseHue + i * 51f) % 360f
            val wave = sin(frameCount * 0.1f + i * 0.5f).toFloat() * 10f
            val brightness = (128 + bassLevel * 127).toInt()
            paint.color = Color.HSVToColor(brightness, floatArrayOf(hue, 1f, 1f))
            c.drawRect(0f, i * bandH + wave, w, (i + 1) * bandH + wave, paint)
        }
    }
    
    // Effect 11: Particle Burst
    private fun drawParticleBurst(c: Canvas, w: Float, h: Float) {
        val cx = w / 2
        val cy = h / 2
        val phase = animationPhase
        val numParticles = 24
        
        for (i in 0 until numParticles) {
            val angle = (i.toFloat() / numParticles) * Math.PI.toFloat() * 2 + phase * Math.PI.toFloat() * 2
            val dist = (0.2f + phase * 0.6f) * minOf(w, h) * 0.45f
            val x = cx + dist * cos(angle)
            val y = cy + dist * sin(angle)
            
            val hue = (i * 15f + frameCount * 3f) % 360f
            val alpha = ((1f - phase * 0.6f) * 255).toInt()
            paint.color = Color.HSVToColor(alpha, floatArrayOf(hue, 1f, 1f))
            c.drawCircle(x, y, 3f, paint)
        }
        
        paint.color = Color.WHITE
        c.drawCircle(cx, cy, 5f * (1f - phase * 0.3f), paint)
    }
    
    // Effect 12: Kaleidoscope
    private fun drawKaleidoscope(c: Canvas, w: Float, h: Float) {
        val cx = w / 2
        val cy = h / 2
        val segments = 8
        val radius = minOf(w, h) * 0.4f
        
        for (seg in 0 until segments) {
            val baseAngle = (seg.toFloat() / segments) * 360f + frameCount * 2f
            val hue = (seg * 45f + frameCount * 3f) % 360f
            
            for (layer in 0..2) {
                val r = radius * (0.4f + layer * 0.25f)
                val angle = Math.toRadians((baseAngle + layer * 10f).toDouble())
                val x = cx + r * cos(angle).toFloat()
                val y = cy + r * sin(angle).toFloat()
                
                val alpha = 220 - layer * 40
                paint.color = Color.HSVToColor(alpha, floatArrayOf((hue + layer * 20f) % 360f, 1f, 1f))
                c.drawCircle(x, y, 4f - layer, paint)
            }
        }
    }
    
    // Effect 13: Frequency Spiral
    private fun drawFrequencySpiral(c: Canvas, w: Float, h: Float) {
        val cx = w / 2
        val cy = h / 2
        val points = 60
        
        for (i in 0 until points) {
            val t = i.toFloat() / points
            val angle = t * Math.PI * 6 + frameCount * 0.1
            val r = t * minOf(w, h) * 0.4f
            val x = cx + r * cos(angle).toFloat()
            val y = cy + r * sin(angle).toFloat()
            
            val hue = (t * 360f + frameCount * 3f) % 360f
            val alpha = (200 + t * 55f).toInt()
            paint.color = Color.HSVToColor(alpha, floatArrayOf(hue, 1f, 1f))
            c.drawCircle(x, y, 1.5f + t * 2f, paint)
        }
    }
    
    // Effect 14: Bass Reactor
    private fun drawBassReactor(c: Canvas, w: Float, h: Float) {
        val cx = w / 2
        val cy = h / 2
        val maxR = minOf(w, h) * 0.4f
        
        for (i in 4 downTo 0) {
            val r = maxR * (0.5f + bassLevel * 0.5f) * (1f + i * 0.15f)
            val alpha = (200 - i * 35).coerceAtLeast(60)
            val hue = 260f + bassLevel * 60f
            paint.color = Color.HSVToColor(alpha, floatArrayOf(hue, 0.9f, 1f))
            c.drawCircle(cx, cy, r, paint)
        }
        
        paint.color = Color.HSVToColor(255, floatArrayOf(280f, 0.5f, 1f))
        c.drawCircle(cx, cy, maxR * 0.2f * (0.8f + bassLevel * 0.4f), paint)
    }
    
    // Effect 15: Meteor Shower
    private fun drawMeteorShower(c: Canvas, w: Float, h: Float) {
        // Stars background
        paint.color = Color.argb(20, 0, 0, 0)
        c.drawRect(0f, 0f, w, h, paint)
        
        for (i in 0..15) {
            val x = ((i * 67 + frameCount * 0.2f) % w.toInt()).toFloat()
            val y = ((i * 43) % h.toInt()).toFloat()
            paint.color = Color.argb(100, 255, 255, 255)
            c.drawCircle(x, y, 1f, paint)
        }
        
        // Meteors
        val meteors = 3
        for (m in 0 until meteors) {
            val phase = ((frameCount * 0.02f + m * 0.33f) % 1f)
            val x = (0.1f + m * 0.3f + phase * 0.3f) * w
            val y = phase * h * 1.2f
            val len = h * 0.15f
            
            // Trail
            paint.shader = LinearGradient(x - len * 0.3f, y - len, x, y,
                Color.TRANSPARENT, Color.HSVToColor(255, floatArrayOf(30f + m * 20f, 1f, 1f)),
                Shader.TileMode.CLAMP)
            paint.strokeWidth = 3f
            paint.style = Paint.Style.STROKE
            c.drawLine(x - len * 0.3f, y - len, x, y, paint)
            paint.shader = null
            paint.style = Paint.Style.FILL
            
            // Head
            paint.color = Color.WHITE
            c.drawCircle(x, y, 2f, paint)
        }
    }
    
    // Effect 16: Breathing
    private fun drawBreathing(c: Canvas, w: Float, h: Float) {
        val breath = (sin(frameCount * 0.05) + 1.0).toFloat() / 2f
        val hue = (frameCount * 0.5f) % 360f
        val cx = w / 2
        val cy = h / 2
        
        for (i in 4 downTo 0) {
            val r = minOf(w, h) * (0.2f + breath * 0.25f) * (1f + i * 0.2f)
            val alpha = (180 - i * 30).coerceAtLeast(50)
            paint.color = Color.HSVToColor(alpha, floatArrayOf((hue + i * 10f) % 360f, 0.8f, 0.9f + breath * 0.1f))
            c.drawCircle(cx, cy, r, paint)
        }
    }
    
    // Effect 17: DNA Helix
    private fun drawDnaHelix(c: Canvas, w: Float, h: Float) {
        val cx = w / 2
        val points = 20
        val amplitude = w * 0.3f
        
        for (i in 0 until points) {
            val t = i.toFloat() / points
            val y = t * h
            val phase = t * Math.PI * 3 + frameCount * 0.1
            
            val x1 = cx + amplitude * sin(phase).toFloat()
            val x2 = cx - amplitude * sin(phase).toFloat()
            
            val hue1 = (frameCount * 3f + t * 60f) % 360f
            val hue2 = (hue1 + 180f) % 360f
            
            paint.color = Color.HSVToColor(255, floatArrayOf(hue1, 1f, 1f))
            c.drawCircle(x1, y, 3f, paint)
            
            paint.color = Color.HSVToColor(255, floatArrayOf(hue2, 1f, 1f))
            c.drawCircle(x2, y, 3f, paint)
            
            if (i % 3 == 0) {
                paint.color = Color.argb(150, 255, 255, 255)
                paint.strokeWidth = 1f
                c.drawLine(x1, y, x2, y, paint)
            }
        }
    }
    
    // Effect 18: Audio Scope
    private fun drawAudioScope(c: Canvas, w: Float, h: Float) {
        val cy = h / 2
        val hue = (frameCount * 2f) % 360f
        
        // Center line
        paint.color = Color.argb(50, 255, 255, 255)
        c.drawLine(0f, cy, w, cy, paint)
        
        // Waveform
        paint.color = Color.HSVToColor(255, floatArrayOf(hue, 1f, 1f))
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        
        val path = Path()
        path.moveTo(0f, cy)
        for (x in 0 until w.toInt() step 3) {
            val xf = x.toFloat()
            val barIdx = ((x / w) * 16).toInt().coerceIn(0, 15)
            val amp = barHeights[barIdx] * h * 0.4f
            val noise = sin(xf * 0.1 + frameCount * 0.3).toFloat()
            val y = cy + noise * amp
            path.lineTo(xf, y)
        }
        c.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }
    
    // Effect 19: Bouncing Balls
    private fun drawBouncingBalls(c: Canvas, w: Float, h: Float) {
        val balls = 5
        for (i in 0 until balls) {
            val phase = ((frameCount * 0.03f + i * 0.2f) % 1f)
            val bounceY = 1f - 4f * (phase - 0.5f) * (phase - 0.5f)
            
            val x = w * (0.15f + i * 0.175f)
            val y = h * (0.9f - bounceY * 0.6f)
            val hue = (i * 72f) % 360f
            
            // Shadow
            paint.color = Color.argb(40, 0, 0, 0)
            c.drawOval(x - 6f, h * 0.88f, x + 6f, h * 0.92f, paint)
            
            // Ball
            paint.color = Color.HSVToColor(255, floatArrayOf(hue, 1f, 1f))
            c.drawCircle(x, y, 6f, paint)
            
            // Highlight
            paint.color = Color.argb(180, 255, 255, 255)
            c.drawCircle(x - 2f, y - 2f, 2f, paint)
        }
    }
    
    // Effect 20: Lava Lamp
    private fun drawLavaLamp(c: Canvas, w: Float, h: Float) {
        // Warm background
        paint.color = Color.argb(60, 255, 100, 50)
        c.drawRect(0f, 0f, w, h, paint)
        
        for (blob in blobs) {
            val x = blob.x * w
            val y = blob.y * h
            val r = blob.radius * minOf(w, h)
            val hue = blob.hue + frameCount * 0.3f
            
            for (layer in 2 downTo 0) {
                val lr = r * (1f + layer * 0.3f)
                val alpha = (200 - layer * 50).coerceAtLeast(80)
                paint.color = Color.HSVToColor(alpha, floatArrayOf((hue + layer * 10f) % 360f, 1f, 0.9f))
                c.drawCircle(x, y, lr, paint)
            }
        }
    }
    
    // Effect 21: Ambient
    private fun drawAmbient(c: Canvas, w: Float, h: Float) {
        val hue = (frameCount * 0.5f) % 360f
        val pulse = (sin(frameCount * 0.05) + 1.0).toFloat() / 2f
        val cx = w / 2
        val cy = h / 2
        
        // Radial gradient
        val gradient = RadialGradient(cx, cy, maxOf(w, h) * 0.6f,
            Color.HSVToColor((180 + pulse * 75).toInt(), floatArrayOf(hue, 0.6f, 0.9f)),
            Color.HSVToColor(60, floatArrayOf((hue + 30f) % 360f, 0.8f, 0.5f)),
            Shader.TileMode.CLAMP)
        paint.shader = gradient
        c.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        
        // Floating particles
        for (i in 0..8) {
            val px = w * (0.1f + i * 0.1f) + sin(frameCount * 0.05 + i).toFloat() * 10f
            val py = h * (0.2f + (i % 3) * 0.25f) + cos(frameCount * 0.05 + i * 0.5).toFloat() * 8f
            paint.color = Color.argb((100 + pulse * 50).toInt(), 255, 255, 255)
            c.drawCircle(px, py, 1.5f + pulse, paint)
        }
    }
    
    // Effect 255: Off - visible frosted dark background
    private fun drawOff(c: Canvas, w: Float, h: Float) {
        // Dark gradient background - more visible
        val gradient = LinearGradient(0f, 0f, w, h,
            Color.argb(255, 40, 45, 55),
            Color.argb(255, 25, 28, 35),
            Shader.TileMode.CLAMP)
        paint.shader = gradient
        c.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        
        // Visible glow in center (indicates LED is off but present)
        val cx = w / 2
        val cy = h / 2
        val radialGlow = RadialGradient(cx, cy, maxOf(w, h) * 0.6f,
            Color.argb(80, 100, 120, 150),
            Color.argb(0, 50, 60, 70),
            Shader.TileMode.CLAMP)
        paint.shader = radialGlow
        c.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        
        // Add subtle noise/grain dots for texture
        paint.color = Color.argb(25, 150, 160, 180)
        for (i in 0 until 20) {
            val px = Random.nextFloat() * w
            val py = Random.nextFloat() * h
            c.drawCircle(px, py, 0.5f, paint)
        }
    }
    
    // Fallback
    private fun drawGeneric(c: Canvas, w: Float, h: Float) {
        val cx = w / 2
        val cy = h / 2
        val r = minOf(w, h) * 0.3f * (0.8f + 0.2f * sin(frameCount * 0.1).toFloat())
        paint.color = Color.HSVToColor(200, floatArrayOf((frameCount * 3f) % 360f, 0.8f, 1f))
        c.drawCircle(cx, cy, r, paint)
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        effectBitmap?.recycle()
        blurredBitmap?.recycle()
        blurScript?.destroy()
        renderScript?.destroy()
    }
}
