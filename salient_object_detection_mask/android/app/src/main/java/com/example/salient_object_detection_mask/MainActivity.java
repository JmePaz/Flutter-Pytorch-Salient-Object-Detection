package com.example.salient_object_detection_mask;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import  io.flutter.plugin.common.MethodChannel;
import  androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import  io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugins.GeneratedPluginRegistrant;
import io.flutter.plugin.common.PluginRegistry;
import kotlin.NotImplementedError;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.io.File;
import java.util.stream.Collectors;


public class MainActivity extends FlutterActivity implements  FlutterPlugin, MethodChannel.MethodCallHandler {
    static  final String CHANNEL_NAME = "com.paz/imgProcessing";
    private MethodChannel channel;
    private  Module module = null;
    private Context context;
    private FlutterPlugin.FlutterPluginBinding binding;
    private PluginRegistry.Registrar registrar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);
        GeneratedPluginRegistrant.registerWith(flutterEngine);
        flutterEngine.getPlugins().add(this);
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPlugin.FlutterPluginBinding binding) {
        this.context = binding.getApplicationContext();
        this.binding = binding;
        channel = new MethodChannel(binding.getBinaryMessenger(), CHANNEL_NAME);
        channel.setMethodCallHandler(this::onMethodCall);
       // BinaryMessenger messenger = binding.getBinaryMessenger();
        //channel = new MethodChannel(messenger, CHANNEL_NAME);
        //channel.setMethodCallHandler(this);

    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPlugin.FlutterPluginBinding binding) {
        this.context = null;
        this.binding = null;
        channel.setMethodCallHandler(null);

    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {

            if(call.method.equals("loadModel")){
                String path = call.argument("path");
                loadModel(path);
                result.success(null);
            }
            else if(call.method.equals("segmentObjectOnImage")){
                Log.d("Debug", "Calling segmentation method");
                String path = call.argument("path").toString().replace("file://", "");
                Log.d("Debug", "Path of an Image: "+ path);
                final Bitmap imgBitMap = imgPathToBitmap(path);
               /* Bitmap imgBitMap = null;
                try {
                   imgBitMap = BitmapFactory.decodeStream(getAssets().open("horse.jpg"));
                }
                catch (IOException e){
                    result.error("Error", e.getMessage(), e);
                    return;
                }*/
                Log.d("Debug", "Running the Model");
               // final Bitmap segmentedBitmap = imgBitMap;
                Bitmap segmentedBitmap = segmentObjectOnImage(imgBitMap);
                if(segmentedBitmap == null){
                    Log.d("Debug", "Image not found in the path!");
                    result.error("Image NOT Found!", "not able to find the image path", null);
                    return;
                }
                Log.d("Debug", "Successful! Converting into buffer/byte[]");
                byte[] res = bitmapToBuffer(segmentedBitmap);
                result.success(res);
                //garabage
                segmentedBitmap = null;
                res = null;


            }
            else{
                throw  new NotImplementedError("Not implemented: "+ call.method);
            }
    }
    private void loadModel( String path){
        try {
            module =  LiteModuleLoader.load(assetFilePath(this.binding.getApplicationContext(), "u2netp.ptl"));
            
            //module = LiteModuleLoader.load(path);
            System.out.println("Model is loaded...");
        }
        catch (Exception e){
            throw new RuntimeException(e);
        }

    }

    private  Bitmap imgPathToBitmap(String imgPath){
        File imgFile = new File(imgPath);
        Bitmap mBitmap = null;
        if(imgFile.exists()) {
            mBitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
        }
        return  mBitmap;
    }

    private Bitmap segmentObjectOnImage(Bitmap imgBit){
        if(imgBit == null){
            return  null;
        }
        // rescaling
        Bitmap mBitmap = rescaleBitMap(imgBit, 320, true);

        //rescaling
        final Tensor imageInputTensor = TensorImageUtils.bitmapToFloat32Tensor(mBitmap,
                TensorImageUtils.TORCHVISION_NORM_MEAN_RGB, TensorImageUtils.TORCHVISION_NORM_STD_RGB);


        android.util.Log.d("ImageSegmentation Log",  "Ready for running the input");
        final long startTime = SystemClock.elapsedRealtime();
        final Tensor imageOutputTensor = module.forward(IValue.from(imageInputTensor)).toTensor();

        final long inferenceTime = SystemClock.elapsedRealtime() - startTime;
        android.util.Log.d("ImageSegmentation Log",  "inference time (ms): " + inferenceTime);
        //long[] shape = imageOutputTensor.shape();
        //android.util.Log.d("Shape of Tensor", Arrays.stream(shape).mapToObj(String::valueOf).collect(Collectors.joining(" ")));

        float[] dataOutput = imageOutputTensor.getDataAsFloatArray();

        //get min max value
        float minF = Float.MAX_VALUE;
        float maxF = Float.MIN_VALUE;
        for(float f : dataOutput){
            if(f<minF){
                minF = f;
            }
            if(f>maxF){
                maxF = f;
            }
        }
        Log.d("Min Max ", String.format("(%f, %f)",minF, maxF));

        int posMinX = dataOutput.length-1, posMaxX = 0, posMinY = dataOutput.length-1, posMaxY = 0;
        int width = mBitmap.getWidth();
        int height = mBitmap.getHeight();
        int[]pxValues = new int[width*height];
        final int MASK_MARGIN = 10;
        for(int j =0; j<height; j++) {
            for (int k = 0; k < width; k++) {
                int ind = j * width + k; //row based
                float pxVF = dataOutput[ind];
                int pxV_int = (int) (((pxVF - minF) / (maxF - minF)) * 255);

                // apply direct masking
                if(pxV_int <= MASK_MARGIN){
                    pxValues[ind] = 0x00000000;
                }
                else{
                    pxValues[ind] = mBitmap.getPixel(k, j);
                }

                //modify position for setting min box bounds (cropping)
                if(pxValues[ind] != 0x00000000){
                    //for x values
                    if(k<posMinX){
                        posMinX = k;
                    }
                    if(k>posMaxX){
                        posMaxX = k;
                    }
                    //for y values
                    if(j<posMinY){
                        posMinY = j;
                    }
                    if(j>posMaxY){
                        posMaxY = j;
                    }


                }
            }
        }

        Bitmap segmentedBitMap = Bitmap.createBitmap(pxValues, width, height, mBitmap.getConfig());
        final Bitmap croppedBitMap = Bitmap.createBitmap(segmentedBitMap, posMinX,  posMinY,posMaxX-posMinX,posMaxY-posMinY);
        final Bitmap resizedBitMap = rescaleBitMap(croppedBitMap, 400, true);
        return  resizedBitMap;
    }

    private  Bitmap rescaleBitMap(Bitmap imgBit, int minDimension, boolean isFlag){
        int newWidth = 0;
        int newHeight = 0;
        if(imgBit.getHeight()>=imgBit.getWidth()){
            newWidth = minDimension;
            newHeight = (minDimension * imgBit.getHeight())/imgBit.getWidth();
        }
        else{
            newHeight = minDimension;
            newWidth = (minDimension*imgBit.getWidth())/imgBit.getHeight();
        }

        return  Bitmap.createScaledBitmap(imgBit, newWidth, newHeight, isFlag);
    }

    private  byte[] bitmapToBuffer(Bitmap imgBit){
        ByteArrayOutputStream byteOutputS = new ByteArrayOutputStream();
        imgBit.compress(Bitmap.CompressFormat.PNG, 100, byteOutputS);
        return  byteOutputS.toByteArray();
    }

    /**
     * Copies specified asset to the file in /files app directory and returns this file absolute path.
     *
     * @return absolute file path
     */
    public static String assetFilePath(Context context, String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        }

    }


}
