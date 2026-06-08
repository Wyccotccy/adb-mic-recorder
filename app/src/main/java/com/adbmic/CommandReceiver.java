package com.adbmic;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * ADB 命令接收器
 *
 * 使用方法：
 *   开始录音: adb shell am broadcast -a com.adbmic.START
 *   停止录音: adb shell am broadcast -a com.adbmic.STOP
 *   查看状态: adb shell am broadcast -a com.adbmic.STATUS
 */
public class CommandReceiver extends BroadcastReceiver {

    private static final String TAG = "AdbMic";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i(TAG, "Received command: " + action);

        switch (action != null ? action : "") {
            case "com.adbmic.START":
                startRecording(context);
                break;
            case "com.adbmic.STOP":
                stopRecording(context);
                break;
            case "com.adbmic.STATUS":
                checkStatus(context);
                break;
        }
    }

    private void startRecording(Context context) {
        Intent intent = new Intent(context, RecordingService.class);
        intent.setAction("com.adbmic.START_RECORD");
        context.startForegroundService(intent);

        // 写状态文件方便 adb 读取
        writeStatus("RECORDING");
        Log.i(TAG, "Recording started");
    }

    private void stopRecording(Context context) {
        Intent intent = new Intent(context, RecordingService.class);
        intent.setAction("com.adbmic.STOP_RECORD");
        context.startForegroundService(intent);

        writeStatus("STOPPED");
        Log.i(TAG, "Recording stopped");
    }

    private void checkStatus(Context context) {
        // 列出所有录音文件
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File[] files = dir.listFiles((d, name) -> name.startsWith("adb_rec_") && name.endsWith(".wav"));

        StringBuilder sb = new StringBuilder();
        sb.append("=== ADB Mic Recorder ===\n");
        sb.append("Files in Downloads:\n");

        if (files != null && files.length > 0) {
            for (File f : files) {
                sb.append("  ").append(f.getName())
                  .append(" (").append(f.length() / 1024).append(" KB)\n");
            }
        } else {
            sb.append("  (no recordings)\n");
        }

        sb.append("\nUsage:\n");
        sb.append("  Start: adb shell am broadcast -a com.adbmic.START\n");
        sb.append("  Stop:  adb shell am broadcast -a com.adbmic.STOP\n");
        sb.append("  Files: adb pull /sdcard/Download/ .\n");

        writeStatus(sb.toString());
        Log.i(TAG, sb.toString());
    }

    private void writeStatus(String status) {
        try {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File file = new File(dir, "adb_mic_status.txt");
            FileWriter fw = new FileWriter(file);
            fw.write(status);
            fw.write("\nUpdated: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
            fw.close();
        } catch (Exception e) {
            Log.e(TAG, "Write status error", e);
        }
    }
}
