package com.sama.directmetaldetector;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;

public class DirectMetalDetector extends AppCompatActivity {
    
    private AudioRecord audioRecord;
    private boolean isDetecting = false;
    private TextView statusText, sensitivityText, detectionText;
    private LineChart signalChart;
    private Handler handler = new Handler();
    
    // متغیرهای پردازش سیگنال
    private float baselineLevel = 0.0f;
    private float currentSignal = 0.0f;
    private int metalDetectionCount = 0;
    private List<Float> signalHistory = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detector);
        
        initializeViews();
        setupAudioProcessing();
    }

    private void initializeViews() {
        statusText = findViewById(R.id.statusTextView);
        sensitivityText = findViewById(R.id.sensitivityTextView);
        detectionText = findViewById(R.id.detectionTextView);
        signalChart = findViewById(R.id.signalChart);
        
        statusText.setText("🔧 در حال راه‌اندازی فلزیاب...");
    }

    private void setupAudioProcessing() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.RECORD_AUDIO}, 101);
        } else {
            startMetalDetection();
        }
    }

    private void startMetalDetection() {
        int sampleRate = 44100;
        int bufferSize = AudioRecord.getMinBufferSize(sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
            sampleRate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        
        startSignalAnalysis();
    }

    private void startSignalAnalysis() {
        isDetecting = true;
        
        new Thread(() -> {
            short[] audioBuffer = new short[1024];
            audioRecord.startRecording();
            
            // کالیبراسیون اولیه (بدون فلز)
            calibrateDetector();
            
            while (isDetecting) {
                int samplesRead = audioRecord.read(audioBuffer, 0, audioBuffer.length);
                if (samplesRead > 0) {
                    analyzeMetalSignal(audioBuffer, samplesRead);
                }
            }
        }).start();
    }

    private void calibrateDetector() {
        short[] buffer = new short[1024];
        float sum = 0;
        int calibrationSamples = 50;
        
        runOnUiThread(() -> statusText.setText("🎯 در حال کالیبراسیون..."));
        
        for (int i = 0; i < calibrationSamples; i++) {
            audioRecord.read(buffer, 0, buffer.length);
            sum += calculateSignalStrength(buffer);
        }
        
        baselineLevel = sum / calibrationSamples;
        
        runOnUiThread(() -> statusText.setText("✅ فلزیاب آماده است"));
    }

    private float calculateSignalStrength(short[] buffer) {
        // محاسبه RMS (Root Mean Square) سیگنال
        long sumOfSquares = 0;
        for (short sample : buffer) {
            sumOfSquares += sample * sample;
        }
        return (float) Math.sqrt(sumOfSquares / (double) buffer.length);
    }

    private void analyzeMetalSignal(short[] buffer, int samplesRead) {
        float instantSignal = calculateSignalStrength(buffer);
        
        // فیلتر دیجیتال برای هموارسازی
        currentSignal = 0.8f * currentSignal + 0.2f * instantSignal;
        
        // تشخیص فلز
        boolean metalDetected = detectMetalPresence(currentSignal);
        float sensitivity = calculateSensitivity(currentSignal);
        
        // آپدیت رابط کاربری
        updateDetectionUI(metalDetected, sensitivity, currentSignal);
    }

    private boolean detectMetalPresence(float signal) {
        float threshold = baselineLevel * 1.5f; // 50% افزایش از baseline
        return signal > threshold;
    }

    private float calculateSensitivity(float signal) {
        float change = Math.abs(signal - baselineLevel);
        return (change / baselineLevel) * 100f; // درصد تغییر
    }

    private void updateDetectionUI(boolean metalDetected, float sensitivity, float signal) {
        handler.post(() -> {
            // نمایش حساسیت
            sensitivityText.setText(String.format("حساسیت: %.1f%%", sensitivity));
            
            // نمایش وضعیت تشخیص
            if (metalDetected) {
                detectionText.setText("🔴 فلز تشخیص داده شد!");
                metalDetectionCount++;
                statusText.setText("✅ تعداد تشخیص: " + metalDetectionCount);
            } else {
                detectionText.setText("🟢 محیط عاری از فلز");
            }
            
            // آپدیت نمودار
            updateSignalChart(signal);
        });
    }

    private void updateSignalChart(float signalValue) {
        signalHistory.add(signalValue);
        if (signalHistory.size() > 50) {
            signalHistory.remove(0);
        }
        
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < signalHistory.size(); i++) {
            entries.add(new Entry(i, signalHistory.get(i)));
        }
        
        LineDataSet dataSet = new LineDataSet(entries, "سیگنال فلزیاب");
        dataSet.setColor(0xFF2196F3);
        dataSet.setLineWidth(2f);
        
        LineData lineData = new LineData(dataSet);
        signalChart.setData(lineData);
        signalChart.invalidate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isDetecting = false;
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
        }
    }
}
