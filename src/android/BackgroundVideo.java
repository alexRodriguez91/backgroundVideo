package io.iclue.backgroundvideo;

import android.content.pm.PackageManager;

import android.content.Context; 
import android.app.Activity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RelativeLayout;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class BackgroundVideo extends CordovaPlugin {
    private static final String TAG = "BACKGROUND_VIDEO";
    private static final String ACTION_START_RECORDING = "start";
    private static final String ACTION_STOP_RECORDING = "stop";
    private static final String FILE_EXTENSION = ".mp4";
    private static final int START_REQUEST_CODE = 0;

    private String FILE_PATH = "";
    private VideoOverlay videoOverlay;
    private CallbackContext callbackContext;
    private JSONArray requestArgs;
    private Activity mActivity; 

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        mActivity = cordova.getActivity();
        FILE_PATH = cordova.getActivity().getFilesDir().toString() + "/";
        //FILE_PATH = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).toString() + "/";
    }


    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;
        this.requestArgs = args;

        try {
            Log.d(TAG, "ACTION: " + action);

            if (ACTION_START_RECORDING.equalsIgnoreCase(action)) {
                boolean recordAudio = args.getBoolean(2);

                List<String> permissions = new ArrayList<String>();
                if (!cordova.hasPermission(android.Manifest.permission.CAMERA)) {
                    permissions.add(android.Manifest.permission.CAMERA);
                }
                if (recordAudio && !cordova.hasPermission(android.Manifest.permission.RECORD_AUDIO)) {
                    permissions.add(android.Manifest.permission.RECORD_AUDIO);
                }
                if (permissions.size() > 0) {
                    cordova.requestPermissions(this, START_REQUEST_CODE, permissions.toArray(new String[0]));
                    return true;
                }

                Start(this.requestArgs);
                return true;
            }

            if (ACTION_STOP_RECORDING.equalsIgnoreCase(action)) {
                Stop();
                return true;
            }

            callbackContext.error(TAG + ": INVALID ACTION");
            return false;
        } catch (Exception e) {
            Log.e(TAG, "ERROR: " + e.getMessage(), e);
            callbackContext.error(TAG + ": " + e.getMessage());
        }

        return true;
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        for (int r : grantResults) {
            if (r == PackageManager.PERMISSION_DENIED) {
                callbackContext.error("Camera Permission Denied");
                return;
            }
        }

        if (requestCode == START_REQUEST_CODE) {
            Start(this.requestArgs);
        }
    }

    private void Start(JSONArray args) throws JSONException {
        final String filename = args.getString(0);
        final String cameraFace = args.getString(1);
        final boolean recordAudio = args.getBoolean(2);

        if (videoOverlay == null) {
            videoOverlay = new VideoOverlay(mActivity); //, getFilePath());

            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    try {
                        // código añadido
                        initPreviewSurface();
                    } catch (Exception e) {
                        Log.e(TAG, "Error during preview create", e);
                        callbackContext.error(TAG + ": " + e.getMessage());
                    }
                }
            });
        }

        videoOverlay.setCameraFacing(cameraFace);
        videoOverlay.setRecordAudio(recordAudio);

        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    videoOverlay.Start(getFilePath(filename));
                } catch (Exception e) {
                    e.printStackTrace();
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }
    
    
    //texture añadida
    private boolean initPreviewSurface() {
        if (mActivity != null) {
            mTextureView = new TextureView(mActivity);
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
            WindowManager mW = (WindowManager) mActivity.getSystemService(Context.WINDOW_SERVICE);
            int screenWidth = mW.getDefaultDisplay().getWidth();
            int screenHeight = mW.getDefaultDisplay().getHeight();
            mActivity.addContentView(mTextureView, new ViewGroup.LayoutParams(screenWidth, screenHeight));
            if (LOGGING) Log.i(TAG, "Camera preview surface initialized.");
            return true;
        } else {
            if (LOGGING) Log.w(TAG, "Could not initialize preview surface.");
            return false;
        }
    }


    private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            mCamera = getCameraInstance();

            if (mCamera != null) {

                mTextureView.setVisibility(View.INVISIBLE);
                mTextureView.setAlpha(0);

                try {
                    setPreviewParameters();

                    mCamera.setPreviewTexture(surface);
                    mCamera.setDisplayOrientation(mDisplayOrientation);
                    mCamera.setErrorCallback(mCameraErrorCallback);
                    mCamera.setPreviewCallback(mCameraPreviewCallback);

                    mFileId = 0;

                    mCamera.startPreview();
                    mPreviewing = true;
                    if (LOGGING) Log.i(TAG, "Camera [" + mCameraId + "] started.");
                } catch (Exception e) {
                    mPreviewing = false;
                    if (LOGGING) Log.e(TAG, "Failed to init preview: " + e.getMessage());
                    stopCamera();
                }
            } else {
                mPreviewing = false;
                if (LOGGING) Log.w(TAG, "Could not get camera instance.");
            }
        }

        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Ignored, Camera does all the work for us
        }

        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            stopCamera();
            return true;
        }

        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            // Invoked every time there's a new Camera preview frame
        }
    };






    private void Stop() throws JSONException {
        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (videoOverlay != null) {
                    try {
                        String filepath = videoOverlay.Stop();
                        callbackContext.success(filepath);
                    } catch (IOException e) {
                        e.printStackTrace();
                        callbackContext.error(e.getMessage());
                    }
                }
            }
        });
    }

    private String getFilePath(String filename) {
        // Add number suffix if file exists
        int i = 1;
        String fileName = filename;
        while (new File(FILE_PATH + fileName + FILE_EXTENSION).exists()) {
            fileName = filename + '_' + i;
            i++;
        }
        return FILE_PATH + fileName + FILE_EXTENSION;
    }

    //Plugin Method Overrides
    @Override
    public void onPause(boolean multitasking) {
        if (videoOverlay != null) {
            try {
                this.Stop();
            } catch (JSONException e) {
                Log.e(TAG, e.getMessage(), e);
                callbackContext.error(e.getMessage());
            }
        }
        super.onPause(multitasking);
    }

    @Override
    public void onDestroy() {
        try {
            this.Stop();
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage(), e);
        }
        super.onDestroy();
    }
}