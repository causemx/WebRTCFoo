/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.itri.example.depthviewfinder;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.Int2;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Renderscript-based converter for a depth viewfinder
 */
public class DepthViewfinderProcessor {

    private static final String TAG = "DepthViewfinderProcessor";

    private ImageReader mImageReader;
    private Allocation mInputAllocation;
    public Allocation mOutputAllocation;

    private Handler mProcessingHandler;
    private ScriptC_depth_image mDepthImageScript;

    public ProcessingTask mProcessingTask;

    private int mConfidenceThreshold;
    private int mDepthImageMode;

    private Handler mStateHandler;
    private StateListener mStateListener;

    private ConcurrentLinkedQueue<SnapshotRequest> mSnapshotRequestQueue;

    //add by ynhuang @ 20200716
//    public ArrayList<HashMap<Long, byte?[]>> tmp = new ArrayList<HashMap<Long, byte[]>>();
    public HashMap<Long, byte[]> bufferTmp = new HashMap<Long, byte[]>();
    public HashMap<Long, short[]> rawTmp = new HashMap<Long, short[]>();
    private int tmpSize = 100;
    public DepthViewfinderProcessor(RenderScript rs, Size dimensions) {
        // Allocation does not support the DEPTH16 format, so we use ImageReader to receive camera output and then deliver it to Allocation.
        mImageReader = ImageReader.newInstance(dimensions.getWidth(), dimensions.getHeight(), ImageFormat.DEPTH16, 10);

//        Type.Builder d16TypeBuilder = new Type.Builder(rs, Element.createPixel(rs, Element.DataType.UNSIGNED_16, Element.DataKind.PIXEL_DEPTH));
        Type inputType = new Type.Builder(rs, Element.U16(rs))
                .setX(dimensions.getWidth())
                .setY(dimensions.getHeight())
                .create();
        mInputAllocation = Allocation.createTyped(rs, inputType, Allocation.USAGE_SCRIPT);
        mInputAllocation.setAutoPadding(false);
//        Log.d(TAG, "DepthViewfinderProcessor: element = " + mInputAllocation.getElement().getBytesSize() + ", count = " + mInputAllocation.getType().getCount() + ", stride = " + mInputAllocation.getStride());

        Type outputType = new Type.Builder(rs, Element.RGBA_8888(rs))
                .setX(dimensions.getWidth())
                .setY(dimensions.getHeight())
                .create();
        mOutputAllocation = Allocation.createTyped(rs, outputType, Allocation.USAGE_IO_OUTPUT | Allocation.USAGE_SCRIPT);

        HandlerThread processingThread = new HandlerThread("DepthViewfinderProcessor");
        processingThread.start();
        mProcessingHandler = new Handler(processingThread.getLooper());

        mDepthImageScript = new ScriptC_depth_image(rs);

        mProcessingTask = new ProcessingTask();

        mSnapshotRequestQueue = new ConcurrentLinkedQueue<>();

        //add by ynhuang @ 20200715
        mBufferSnapshotRequestQueue = new ConcurrentLinkedQueue<>();
    }

    public Surface getInputSurface() {
        return mImageReader.getSurface();
    }

    public void setOutputSurface(Surface output) {
        mOutputAllocation.setSurface(output);
    }

    public void setConfidenceThreshold(int threshold) {
        if (threshold < mDepthImageScript.get_DEPTH16_CONFIDENCE_MIN() || threshold > mDepthImageScript.get_DEPTH16_CONFIDENCE_MAX()) {
            throw new IllegalArgumentException(String.format("threshold must within [%d, %d]", mDepthImageScript.get_DEPTH16_CONFIDENCE_MAX(), mDepthImageScript.get_DEPTH16_CONFIDENCE_MAX()));
        }
        mConfidenceThreshold = threshold;
    }

    public void setDepthImageMode(int mode) {
        if (mode != mDepthImageScript.get_DEPTH_IMAGE_MODE_GRAY() && mode != mDepthImageScript.get_DEPTH_IMAGE_MODE_COLOR() && mode != mDepthImageScript.get_DEPTH_IMAGE_MODE_CONFIDENCE()) {
            throw new IllegalArgumentException("Invalid mode value.");
        }
        mDepthImageMode = mode;
    }

    public void setStateListener(StateListener listener, Handler handler) {
        mStateListener = listener;

        if (mStateListener != null) {
            mStateHandler = (handler != null) ? handler : new Handler(Looper.getMainLooper());
        }
        else {
            mStateHandler = null;
        }
    }

    public void triggerSnapshot(SnapshotListener listener, Handler handler) {
        if (listener != null) {
            mSnapshotRequestQueue.add(new SnapshotRequest(listener, (handler != null) ? handler : new Handler(Looper.getMainLooper())));
        }
    }

    private void onProcessingStart(final int dropFrames) {
        if (mStateListener == null || mStateHandler == null) return;

        mStateHandler.post(new Runnable() {
            @Override
            public void run() {
                mStateListener.onProcessingStart(DepthViewfinderProcessor.this, dropFrames);
            }
        });
    }

    private void onProcessingFinished(final ProcessingResult result) {
        if (mStateListener != null && mStateHandler != null) {
            result.setReadOnly(true);
            mStateHandler.post(new Runnable() {
                @Override
                public void run() {
                    mStateListener.onProcessingFinished(DepthViewfinderProcessor.this, result);
                    result.close();
                }
            });
        }

        final SnapshotRequest request = mSnapshotRequestQueue.poll();

        if (request != null && request.mListener != null && request.mHandler != null) {
            final Bitmap depthImage = Bitmap.createBitmap(mOutputAllocation.getType().getX(), mOutputAllocation.getType().getY(), Bitmap.Config.ARGB_8888);
            mOutputAllocation.copyTo(depthImage);

            final short[] depthRaw = result.mImageBuffer.clone();

            request.mHandler.post(new Runnable() {
                @Override
                public void run() {
                    request.mListener.onSnapshotReady(depthImage, depthRaw, result.mTimestamp);
                }
            });
        }

        //add by ynhuang @ 20200715
        final BufferSnapshotRequest request2 = mBufferSnapshotRequestQueue.poll();
        if (request2 != null && request2.mListener !=null && request2.mHandler != null) {
            final Bitmap depthImage = Bitmap.createBitmap(mOutputAllocation.getType().getX(), mOutputAllocation.getType().getY(), Bitmap.Config.ARGB_8888);
            mOutputAllocation.copyTo(depthImage);

            final short[] depthRaw = result.mImageBuffer.clone();

            //add by ynhuang @ 20200908
            final int dimensionX = mOutputAllocation.getType().getX();
            final int dimensionY = mOutputAllocation.getType().getY();
            //add by ynhuang @ 20200713
            int bytes = depthImage.getByteCount();
            ByteBuffer buf = ByteBuffer.allocate(bytes);
            depthImage.copyPixelsToBuffer(buf);

            final byte[] byteArray = buf.array();
//            Log.d(TAG, "byteArray: " + byteArray + ", result.mTimestamp: " + result.mTimestamp);
            if(bufferTmp.size()>tmpSize || rawTmp.size()>tmpSize) {
                bufferTmp.clear();
                rawTmp.clear();
            }
            bufferTmp.put(result.mTimestamp, byteArray);
            rawTmp.put(result.mTimestamp, depthRaw);
//            Log.d(TAG, "tmp.size(): " + bufferTmp.size());
              //use Bitmap.Config.ARGB_8888 instead of type is OK
//            final Bitmap stitchBmp = Bitmap.createBitmap(mOutputAllocation.getType().getX(), mOutputAllocation.getType().getY(), Bitmap.Config.ARGB_8888);
//            stitchBmp.copyPixelsFromBuffer(ByteBuffer.wrap(byteArray));
//            //imageView.setImageBitmap(stitchBmp);
            request2.mHandler.post(new Runnable() {
                @Override
                public void run() {
                    request2.mListener.onBufferSnapshotReady(byteArray, depthRaw, result.mTimestamp, dimensionX, dimensionY);
                }
            });
        }
        //add by ynhuang end
    }

    private void onProcessingError(final String errorString) {
        if (mStateListener != null && mStateHandler != null) {
            mStateHandler.post(new Runnable() {
                @Override
                public void run() {
                    mStateListener.onProcessingError(DepthViewfinderProcessor.this, errorString);
                }
            });
        }

        final SnapshotRequest request = mSnapshotRequestQueue.poll();
        if (request != null && request.mListener != null && request.mHandler != null) {
            request.mHandler.post(new Runnable() {
                @Override
                public void run() {
                    request.mListener.onSnapshotFailed(errorString);
                }
            });
        }

        //add by ynhuang @ 20200715
        final BufferSnapshotRequest request2 = mBufferSnapshotRequestQueue.poll();
        if (request2 != null && request2.mListener != null && request2.mHandler != null) {
            request2.mHandler.post(new Runnable() {
                @Override
                public void run() {
                    request2.mListener.onBufferSnapshotFailed(errorString);
                }
            });
        }
    }

    /**
     * Simple class to keep track of incoming frame count,
     * and to process the newest one in the processing thread
     */
    class ProcessingTask implements Runnable, ImageReader.OnImageAvailableListener {
        private int mPendingFrames = 0;
        private ProcessingResult[] mResults;
        private int mCurrentResultIndex = -1;

        private ProcessingTask() {
            mResults = new ProcessingResult[5];
            for (int i = 0; i < mResults.length; i++) {
                mResults[i] = new ProcessingResult();
            }
            mImageReader.setOnImageAvailableListener(this, null);
        }

        @Override
        public void onImageAvailable(ImageReader reader) {
            synchronized (this) {
                mPendingFrames++;
//                Log.d(TAG, "onBufferAvailable: mPendingFrames = " + mPendingFrames);
                mProcessingHandler.post(this);
            }
        }

        @Override
        public void run() {
            mCurrentResultIndex = findNextFreeResultIndex();
            if (mCurrentResultIndex < 0) {
                Log.e(TAG, "ProcessingTask run(): no free result buffer");
                return;
            }
            ProcessingResult result = mResults[mCurrentResultIndex];

            // Find out how many frames have arrived
            int pendingFrames;
            synchronized (this) {
                pendingFrames = mPendingFrames;
                mPendingFrames = 0;

                // Discard extra messages in case processing is slower than frame rate
                mProcessingHandler.removeCallbacks(this);
            }
            onProcessingStart(pendingFrames - 1);

            // Get to newest input
            Image inputImage = mImageReader.acquireLatestImage();

            if (inputImage != null) {
//                Log.d(TAG, "inputImage: format = " + inputImage.getFormat() + ", width = " + inputImage.getWidth() + ", height = " + inputImage.getHeight() + ", planes = " + inputImage.getPlanes());
                inputImage.getPlanes()[0].getBuffer().asShortBuffer().get(result.mImageBuffer);
                result.mTimestamp = inputImage.getTimestamp();
                result.mImageWidth = inputImage.getWidth();
                result.mImageHeight = inputImage.getHeight();
                inputImage.close();

                mInputAllocation.copyFromUnchecked(result.mImageBuffer);

                Int2 depthMinMax = mDepthImageScript.reduce_findMinAndMax(mInputAllocation).get();
//                Log.d(TAG, "depthMinMax: min = " + depthMinMax.x + ", max = " + depthMinMax.y);
                result.mDepthMin = depthMinMax.x;
                result.mDepthMax = depthMinMax.y;

//                mDepthImageScript.set_gMinDepthRange(depthMinMax.x);
                mDepthImageScript.set_gMinDepthRange(mDepthImageScript.get_DEPTH16_RANGE_MIN());
                mDepthImageScript.set_gMaxDepthRange(depthMinMax.y);
                mDepthImageScript.set_gConfidenceThreshold(mConfidenceThreshold);
                mDepthImageScript.set_gDepthImageMode(mDepthImageMode);
                mDepthImageScript.forEach_convertToRGBA(mInputAllocation, mOutputAllocation);

                mOutputAllocation.ioSend();

                onProcessingFinished(result);

                return;
            }
            onProcessingError("Fail to acquire the image.");
        }

        private int findNextFreeResultIndex() {
            int nextIndex = -1;
            int testIndex = (mCurrentResultIndex + 1) % mResults.length;
            for (int i = 0; i < mResults.length; i++) {
                if (!mResults[testIndex].isReadOnly()) {
                    nextIndex = testIndex;
                    break;
                }
                testIndex = (testIndex + 1) % mResults.length;
            }

//            Log.d(TAG, "findNextFreeResultIndex: nextIndex = " + nextIndex);
            return nextIndex;
        }
    }

    class ProcessingResult implements AutoCloseable {
        private short[] mImageBuffer;
        private long mTimestamp;
        private int mImageWidth, mImageHeight;
        private int mDepthMin, mDepthMax;
        boolean mReadOnly = false;

        private ProcessingResult() {
            mImageBuffer = new short[mInputAllocation.getBytesSize() / 2];
        }

        @Override
        public void close() {
            setReadOnly(false);
        }

        public int getDepthRange(int x, int y) {
            short depthRawValue = getDepthRawValue(x, y);

            return (depthRawValue & 0x1FFF);
        }

        public float getDepthConfidence(int x, int y) {
            int confidenceRaw = (getDepthRawValue(x, y) >> 13) & 0x07;

            confidenceRaw = (confidenceRaw == 0) ? 7 : (confidenceRaw - 1);

            return (confidenceRaw / 7.0f);
        }

        public int getDepthMin() {
            return mDepthMin;
        }

        public int getDepthMax() {
            return mDepthMax;
        }

        public long getTimestamp() {
            return mTimestamp;
        }

        private short getDepthRawValue(int x, int y) {
            if (x < 0 || x >= mImageWidth || y < 0 || y >= mImageHeight) throw new IndexOutOfBoundsException();

            return mImageBuffer[x + y * mImageWidth];
        }

        private synchronized void setReadOnly(boolean readOnly) {
            mReadOnly = readOnly;
        }

        private synchronized boolean isReadOnly() {
            return mReadOnly;
        }
    }

    private class SnapshotRequest {
        SnapshotListener mListener;
        Handler mHandler;

        SnapshotRequest(SnapshotListener listener, Handler handler) {
            mListener = listener;
            mHandler = handler;
        }
    }

    /**
     * Simple listener to know the processor state.
     */
    public interface StateListener {
        void onProcessingStart(DepthViewfinderProcessor processor, int dropFrames);
        void onProcessingFinished(DepthViewfinderProcessor processor, ProcessingResult result);
        void onProcessingError(DepthViewfinderProcessor processor, String errorString);
    }

    public interface SnapshotListener {
        void onSnapshotReady(Bitmap depthImage, short[] depthRaw, long timestamp);
        void onSnapshotFailed(String errorString);
    }

    //add by ynhuang @ 20200716
    private ConcurrentLinkedQueue<BufferSnapshotRequest> mBufferSnapshotRequestQueue;

    public void bufferSnapshot(BufferSnapshotListener listener, Handler handler) {
        if (listener != null) {
            mBufferSnapshotRequestQueue.add(new BufferSnapshotRequest(listener, (handler != null) ? handler : new Handler(Looper.getMainLooper())));
        }
    }

    private class BufferSnapshotRequest {
        BufferSnapshotListener mListener;
        Handler mHandler;

        BufferSnapshotRequest(BufferSnapshotListener listener, Handler handler) {
            mListener = listener;
            mHandler = handler;
        }
    }

    public interface BufferSnapshotListener {
        void onBufferSnapshotReady(byte[] byteArray, short[] depthRaw, long timestamp, int dimensionX, int dimensionY);
        void onBufferSnapshotFailed(String errString);
    }
}
