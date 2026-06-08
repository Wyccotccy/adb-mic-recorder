package com.adbmic;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RecordingService extends Service {

    private static final String TAG = "AdbMic";
    private static final String CHANNEL_ID = "adb_mic_channel";
    private static final int NOTIFICATION_ID = 1;

    private AudioRecord audioRecord;
    private volatile boolean isRecording = false;
    private Thread recordingThread;
    private String currentFilePath;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if ("com.adbmic.START_RECORD".equals(action)) {
            if (!isRecording) {
                startRecording();
            }
        } else if ("com.adbmic.STOP_RECORD".equals(action)) {
            stopRecording();
        }

        return START_STICKY;
    }

    private void startRecording() {
        int sampleRate = 44100;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);

        try {
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
            );
        } catch (SecurityException e) {
            Log.e(TAG, "No permission to record", e);
            stopSelf();
            return;
        }

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord init failed");
            stopSelf();
            return;
        }

        // 生成文件名
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = "adb_rec_" + timestamp + ".wav";

        // 保存到 Download 目录
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, fileName);
        currentFilePath = file.getAbsolutePath();

        isRecording = true;
        audioRecord.startRecording();

        // 启动前台通知
        startForeground(NOTIFICATION_ID, buildNotification("录音中: " + fileName));

        // 录音线程
        recordingThread = new Thread(() -> {
            byte[] buffer = new byte[bufferSize];
            try {
                FileOutputStream fos = new FileOutputStream(file);

                // 先写 WAV 头（占位，录完再填）
                writeWavHeader(fos, sampleRate, 1, 16);

                int totalBytes = 0;
                while (isRecording) {
                    int read = audioRecord.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        fos.write(buffer, 0, read);
                        totalBytes += read;
                    }
                }

                fos.close();

                // 回填 WAV 头
                updateWavHeader(file, totalBytes);

                Log.i(TAG, "Saved: " + currentFilePath);
            } catch (Exception e) {
                Log.e(TAG, "Recording error", e);
            }
        }, "AudioRecorder");
        recordingThread.start();
    }

    private void stopRecording() {
        isRecording = false;
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception e) {
                Log.e(TAG, "Stop error", e);
            }
            audioRecord = null;
        }
        stopForeground(true);
        stopSelf();
    }

    private void writeWavHeader(FileOutputStream fos, int sampleRate, int channels, int bitsPerSample) throws Exception {
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;

        byte[] header = new byte[44];
        // RIFF chunk
        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        // file size - 8 (placeholder)
        header[4] = 0; header[5] = 0; header[6] = 0; header[7] = 0;
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        // fmt sub-chunk
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        header[16] = 16; // sub-chunk size
        header[17] = 0; header[18] = 0; header[19] = 0;
        header[20] = 1; // PCM format
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) blockAlign;
        header[33] = 0;
        header[34] = (byte) bitsPerSample;
        header[35] = 0;
        // data sub-chunk
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        // data size (placeholder)
        header[40] = 0; header[41] = 0; header[42] = 0; header[43] = 0;

        fos.write(header, 0, 44);
    }

    private void updateWavHeader(File file, int totalBytes) {
        try {
            RandomAccessFile raf = new RandomAccessFile(file, "rw");
            // file size - 8
            raf.seek(4);
            raf.write(intToLittleEndian(36 + totalBytes));
            // data size
            raf.seek(40);
            raf.write(intToLittleEndian(totalBytes));
            raf.close();
        } catch (Exception e) {
            Log.e(TAG, "Update WAV header error", e);
        }
    }

    private byte[] intToLittleEndian(int value) {
        return new byte[]{
                (byte) (value & 0xff),
                (byte) ((value >> 8) & 0xff),
                (byte) ((value >> 16) & 0xff),
                (byte) ((value >> 24) & 0xff)
        };
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "ADB Mic 录音", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
                .setContentTitle("ADB Mic Recorder")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopRecording();
        super.onDestroy();
    }
}
