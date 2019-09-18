package com.maromav.arduinohexuploader;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;
import android.widget.Toast;

import UsbSerialHelper.SerialPortStreamUart;
import com.unity3d.player.UnityPlayer;
import com.unity3d.player.UnityPlayerActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import ArduinoUploader.ArduinoSketchUploader;
import ArduinoUploader.ArduinoSketchUploaderOptions;
import ArduinoUploader.ArduinoUploaderException;
import ArduinoUploader.Hardware.ArduinoModel;
import ArduinoUploader.IArduinoUploaderLogger;
import CSharpStyle.IProgress;

public class ArduinoHexFileUploader extends UnityPlayerActivity {

    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    private UsbManager usbManager;
    private UsbDevice usbDevice;

    private int boardType;
    private String filePath;

    private SerialPortStreamUart serialPortStreamUart;
    private Context mContext;

    // 유니티에서 받은 메세지를 토스트 메세지로 표현
    public void UnityAnswer_ShowToast(String message) {
        Toast.makeText(UnityPlayer.currentActivity.getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    // 유니티에서 초기화
    public void UnityAnswer_Initialize() {
    }

    // 유니티에서 아두이노 헥스파일 업로드 요청
    public void UnityAnswer_UploadArduinoHex(int type, String path) {
        usbManager = (UsbManager) UnityPlayer.currentActivity.getSystemService(Context.USB_SERVICE);
        usbDevice = null;

        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while(deviceIterator.hasNext()){
            usbDevice = deviceIterator.next();
            break;
        }

        if (usbDevice == null) {
            // 연결된 usbDevice 없음
            Toast.makeText(UnityPlayer.currentActivity.getApplicationContext(), "USB에 연결된 기기가 없습니다. 안드로이드 기기와 아두이노를 OTG 케이블로 연결해주세요.", Toast.LENGTH_SHORT).show();
        } else {
            // 연결된 usbDevice 있음
            boardType = type;
            filePath = path;

            if (!usbManager.hasPermission(usbDevice)) {
                // 권한이 없음
                PendingIntent permissionIntent = PendingIntent.getBroadcast(UnityPlayer.currentActivity, 0, new Intent(ACTION_USB_PERMISSION), 0);
                IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
                UnityPlayer.currentActivity.registerReceiver(usbReceiver, filter);
                usbManager.requestPermission(usbDevice, permissionIntent);
            } else {
                // 권한이 이미 있음
                // 업로드
                Upload();
            }
        }
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (UnityPlayer.currentActivity) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    mContext = context;
                    serialPortStreamUart = new SerialPortStreamUart(mContext, usbDevice.getDeviceName(), 115200);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            // 권한 승인
                            // 업로드
                            Upload();

                            // usbReceiver 해제
                            UnityPlayer.currentActivity.unregisterReceiver(usbReceiver);
                        }
                    }
                    else {
                        // 권한 거절
                        Toast.makeText(UnityPlayer.currentActivity.getApplicationContext(), "USB 권한이 거절되었습니다.", Toast.LENGTH_SHORT).show();

                        // usbReceiver 해제
                        UnityPlayer.currentActivity.unregisterReceiver(usbReceiver);
                    }
                }
            }
        }
    };

    // Hex file upload start
    private void Upload() {
        IArduinoUploaderLogger logger = new IArduinoUploaderLogger() {

            @Override
            public void Error(String message, Exception exception) {
                UnityCall_Message("Error:" + message);
            }

            @Override
            public void Warn(String message) {
                UnityCall_Message("Warn:" + message);
            }

            @Override
            public void Info(String message) {
                UnityCall_Message("Info:" + message);
            }

            @Override
            public void Debug(String message) {
                UnityCall_Message("Debug:" + message);
            }

            @Override
            public void Trace(String message) {
                UnityCall_Message("Trace:" + message);
            }
        };

        ArduinoSketchUploaderOptions options = new ArduinoSketchUploaderOptions();

        switch (boardType) {
            case 0:
                options.setArduinoModel(ArduinoModel.UnoR3);
                options.setFileName(filePath);
                options.setPortName(usbDevice.getDeviceName());
        }


        IProgress<Double> progress = new IProgress<Double>() {

            @Override
            public void Report(Double value) {
                UnityCall_Message("Upload progress: " + value * 100);
            }
        };

        ArduinoSketchUploader<SerialPortStreamUart> uploader = new ArduinoSketchUploader<SerialPortStreamUart>(mContext, SerialPortStreamUart.class, options, logger, progress);

        try {
            uploader.UploadSketch();
        } catch (ArduinoUploaderException ex) {
            Log.e("HexUploader", ex.toString());
        } catch (Exception ex) {
            Log.e("HexUploader", ex.toString());
        }
    }

    // 유니티에 메세지 전송
    public static void UnityCall_Message(String message) {
        UnityPlayer.UnitySendMessage("ArduinoHexUploader", "JavaAnswer_Message", message);
    }
}
