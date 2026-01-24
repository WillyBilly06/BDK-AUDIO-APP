package com.example.myspeaker

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Converts audio files (MP3, etc) to WAV format optimized for ESP32 playback.
 * Output format: 16-bit PCM, mono, dynamically adjusted sample rate to fit 200KB limit.
 */
object AudioConverter {
    private const val TAG = "AudioConverter"
    
    // Target WAV parameters - will be adjusted if file too large
    private const val PREFERRED_SAMPLE_RATE = 22050  // Good quality default
    private const val MIN_SAMPLE_RATE = 8000         // Minimum acceptable quality
    private const val TARGET_CHANNELS = 1
    private const val TARGET_BITS_PER_SAMPLE = 16
    
    // Max file size for upload (200KB)
    private const val MAX_OUTPUT_SIZE = 200 * 1024
    // Max audio data (excluding 44-byte header)
    private const val MAX_AUDIO_DATA = MAX_OUTPUT_SIZE - 44
    
    data class ConversionResult(
        val success: Boolean,
        val wavData: ByteArray? = null,
        val errorMessage: String? = null,
        val originalFormat: String? = null
    )
    
    /**
     * Convert any supported audio file to WAV format.
     * Returns null if conversion fails.
     */
    fun convertToWav(context: Context, uri: Uri): ConversionResult {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        
        try {
            // Set up extractor
            extractor.setDataSource(context, uri, null)
            
            // Find audio track
            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    inputFormat = format
                    break
                }
            }
            
            if (audioTrackIndex < 0 || inputFormat == null) {
                return ConversionResult(false, errorMessage = "No audio track found")
            }
            
            extractor.selectTrack(audioTrackIndex)
            
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: "unknown"
            val inputSampleRate = inputFormat.getIntOrDefault(MediaFormat.KEY_SAMPLE_RATE, 44100)
            val inputChannels = inputFormat.getIntOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 2)
            
            Log.d(TAG, "Input: mime=$mime, sampleRate=$inputSampleRate, channels=$inputChannels")
            
            // If already WAV (PCM), just read and convert
            if (mime == "audio/raw" || mime == "audio/x-wav") {
                return convertRawPcm(context, uri, inputSampleRate, inputChannels)
            }
            
            // Set up decoder
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()
            
            val bufferInfo = MediaCodec.BufferInfo()
            val pcmData = ByteArrayOutputStream()
            var inputDone = false
            var outputDone = false
            
            while (!outputDone) {
                // Feed input
                if (!inputDone) {
                    val inputBufferIndex = decoder.dequeueInputBuffer(10000)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputBufferIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                
                // Get output
                val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                when {
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        Log.d(TAG, "Output format changed: ${decoder.outputFormat}")
                    }
                    outputBufferIndex >= 0 -> {
                        val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)!!
                        val chunk = ByteArray(bufferInfo.size)
                        outputBuffer.get(chunk)
                        outputBuffer.clear()
                        pcmData.write(chunk)
                        decoder.releaseOutputBuffer(outputBufferIndex, false)
                        
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }
            
            decoder.stop()
            decoder.release()
            decoder = null
            extractor.release()
            
            // Get decoded PCM data (usually 16-bit stereo at source sample rate)
            val rawPcm = pcmData.toByteArray()
            Log.d(TAG, "Decoded ${rawPcm.size} bytes of PCM data")
            
            // Convert to target format with dynamic sample rate adjustment
            val (converted, usedSampleRate) = resampleToFit(rawPcm, inputSampleRate, inputChannels)
            
            // Create WAV file
            val wavData = createWavFile(converted, usedSampleRate)
            
            Log.d(TAG, "Conversion complete: ${wavData.size} bytes WAV at ${usedSampleRate}Hz")
            return ConversionResult(true, wavData, originalFormat = mime)
            
        } catch (e: Exception) {
            Log.e(TAG, "Conversion failed", e)
            return ConversionResult(false, errorMessage = e.message ?: "Conversion failed")
        } finally {
            try { decoder?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }
    
    /**
     * Convert raw PCM data (for WAV input files)
     */
    private fun convertRawPcm(context: Context, uri: Uri, sampleRate: Int, channels: Int): ConversionResult {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val rawData = inputStream?.readBytes() ?: return ConversionResult(false, errorMessage = "Cannot read file")
            inputStream.close()
            
            // Skip WAV header if present (44 bytes)
            val pcmData = if (rawData.size > 44 && 
                rawData[0].toInt().toChar() == 'R' && 
                rawData[1].toInt().toChar() == 'I' &&
                rawData[2].toInt().toChar() == 'F' &&
                rawData[3].toInt().toChar() == 'F') {
                rawData.copyOfRange(44, rawData.size)
            } else {
                rawData
            }
            
            val (converted, usedSampleRate) = resampleToFit(pcmData, sampleRate, channels)
            val wavData = createWavFile(converted, usedSampleRate)
            
            Log.d(TAG, "WAV conversion complete: ${wavData.size} bytes at ${usedSampleRate}Hz")
            ConversionResult(true, wavData, originalFormat = "audio/wav")
        } catch (e: Exception) {
            ConversionResult(false, errorMessage = e.message)
        }
    }
    
    /**
     * Resample and convert to mono with dynamic sample rate to fit within MAX_OUTPUT_SIZE.
     * Returns the resampled data and the sample rate used.
     */
    private fun resampleToFit(pcmData: ByteArray, inputSampleRate: Int, inputChannels: Int): Pair<ShortArray, Int> {
        // Convert bytes to shorts (16-bit samples)
        val inputSamples = ShortArray(pcmData.size / 2)
        ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(inputSamples)
        
        // Convert to mono if stereo
        val monoSamples = if (inputChannels >= 2) {
            ShortArray(inputSamples.size / inputChannels) { i ->
                var sum = 0
                for (ch in 0 until inputChannels) {
                    sum += inputSamples[i * inputChannels + ch].toInt()
                }
                (sum / inputChannels).toShort()
            }
        } else {
            inputSamples
        }
        
        // Calculate duration in seconds
        val durationSecs = monoSamples.size.toDouble() / inputSampleRate
        Log.d(TAG, "Audio duration: %.2f seconds, mono samples: ${monoSamples.size}".format(durationSecs))
        
        // Calculate max samples that fit in MAX_AUDIO_DATA (2 bytes per sample)
        val maxSamples = MAX_AUDIO_DATA / 2
        
        // Try sample rates from high to low until it fits
        val sampleRatesToTry = listOf(22050, 16000, 11025, 8000)
        
        for (targetRate in sampleRatesToTry) {
            val resampleRatio = inputSampleRate.toDouble() / targetRate
            val outputLength = (monoSamples.size / resampleRatio).toInt()
            
            // Check if this fits
            if (outputLength <= maxSamples) {
                Log.d(TAG, "Using sample rate $targetRate Hz (${outputLength * 2} bytes)")
                val resampled = resampleTo(monoSamples, inputSampleRate, targetRate)
                return Pair(resampled, targetRate)
            }
        }
        
        // Even at min sample rate it's too long - truncate
        Log.w(TAG, "Audio too long, truncating to fit at ${MIN_SAMPLE_RATE}Hz")
        val resampleRatio = inputSampleRate.toDouble() / MIN_SAMPLE_RATE
        val fullOutputLength = (monoSamples.size / resampleRatio).toInt()
        
        // Truncate input to fit
        val maxInputSamples = (maxSamples * resampleRatio).toInt()
        val truncatedMono = if (monoSamples.size > maxInputSamples) {
            monoSamples.copyOfRange(0, maxInputSamples)
        } else {
            monoSamples
        }
        
        val resampled = resampleTo(truncatedMono, inputSampleRate, MIN_SAMPLE_RATE)
        val finalSamples = if (resampled.size > maxSamples) {
            resampled.copyOfRange(0, maxSamples)
        } else {
            resampled
        }
        
        val truncatedDuration = finalSamples.size.toDouble() / MIN_SAMPLE_RATE
        Log.d(TAG, "Truncated to %.2f seconds at ${MIN_SAMPLE_RATE}Hz".format(truncatedDuration))
        
        return Pair(finalSamples, MIN_SAMPLE_RATE)
    }
    
    /**
     * Resample to target rate using linear interpolation
     */
    private fun resampleTo(samples: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        if (fromRate == toRate) {
            return samples
        }
        
        val resampleRatio = fromRate.toDouble() / toRate
        val outputLength = (samples.size / resampleRatio).toInt()
        val resampled = ShortArray(outputLength)
        
        for (i in 0 until outputLength) {
            val srcPos = i * resampleRatio
            val srcIdx = srcPos.toInt()
            val frac = srcPos - srcIdx
            
            val s0 = samples.getOrElse(srcIdx) { 0 }.toInt()
            val s1 = samples.getOrElse(srcIdx + 1) { s0.toShort() }.toInt()
            
            resampled[i] = (s0 + (s1 - s0) * frac).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        
        return resampled
    }
    
    /**
     * Create WAV file from PCM samples
     */
    private fun createWavFile(samples: ShortArray, sampleRate: Int): ByteArray {
        val dataSize = samples.size * 2  // 16-bit = 2 bytes per sample
        val fileSize = 44 + dataSize - 8  // RIFF chunk doesn't include "RIFF" and size fields
        
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        
        // RIFF header
        buffer.put("RIFF".toByteArray())
        buffer.putInt(fileSize)
        buffer.put("WAVE".toByteArray())
        
        // fmt chunk
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)  // fmt chunk size
        buffer.putShort(1)  // audio format (PCM)
        buffer.putShort(TARGET_CHANNELS.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * TARGET_CHANNELS * TARGET_BITS_PER_SAMPLE / 8)  // byte rate
        buffer.putShort((TARGET_CHANNELS * TARGET_BITS_PER_SAMPLE / 8).toShort())  // block align
        buffer.putShort(TARGET_BITS_PER_SAMPLE.toShort())
        
        // data chunk
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)
        
        // PCM data
        for (sample in samples) {
            buffer.putShort(sample)
        }
        
        return buffer.array()
    }
    
    private fun MediaFormat.getIntOrDefault(key: String, default: Int): Int {
        return try {
            getInteger(key)
        } catch (e: Exception) {
            default
        }
    }
}
