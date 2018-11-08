package com.ycloud.jpeg;

import android.graphics.Bitmap;
import com.ycloud.utils.YYLog;
import org.libjpegturbo.turbojpeg.TJ;
import org.libjpegturbo.turbojpeg.TJCompressor;
import org.libjpegturbo.turbojpeg.TJDecompressor;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Administrator on 2018/8/8.
 * High level wrapper API for libturbo-jpeg java API
 */

public class YYJpeg {
    private static final String TAG = "YYJpeg";
    private static final String TJ_OPTIMIZE  = "turbojpeg.optimize";
    private AtomicBoolean mRecycled = new AtomicBoolean(false);
    private boolean mUseOptimize = true;

    // for decoder
    private byte [] imgBuf;
    private int width;
    private int height;

    static final String[] SUBSAMP_NAME = {
            "4:4:4", "4:2:2", "4:2:0", "Grayscale", "4:4:0", "4:1:1"
    };

    static final String[] COLORSPACE_NAME = {
            "RGB", "YCbCr", "GRAY", "CMYK", "YCCK"
    };

    public YYJpeg() {
        System.setProperty(TJ_OPTIMIZE, "1");
    }


    public boolean compress(Bitmap bitmap, int quality, OutputStream stream) {
        if (stream == null) {
            throw new NullPointerException("stream is null .");
        }

        if (quality < 0 || quality > 100) {
            throw new IllegalArgumentException("quality must be 0..100 .");
        }

        if (bitmap == null || bitmap.isRecycled()) {
            throw new IllegalArgumentException("bitmap is null or isRecycled .");
        }

        ByteBuffer buffer = ByteBuffer.allocate(bitmap.getByteCount());
        bitmap.copyPixelsToBuffer(buffer);
        return TJCompress(quality, stream, buffer, bitmap.getWidth(), bitmap.getHeight());
    }

    public boolean compress(byte[] jpeg, int width , int height, int quality, OutputStream stream) {
        if (stream == null) {
            throw new NullPointerException("stream is null .");
        }

        if (quality < 0 || quality > 100) {
            throw new IllegalArgumentException("quality must be 0..100 .");
        }

        if (jpeg == null || jpeg.length <= 0) {
            throw new IllegalArgumentException("jpeg buffer is null or size == 0 .");
        }

        return TJCompress(quality, stream, ByteBuffer.wrap(jpeg), width, height);
    }

    public boolean compress(int quality, OutputStream stream) {
        if (stream == null) {
            throw new NullPointerException("stream is null .");
        }

        if (quality < 0 || quality > 100) {
            throw new IllegalArgumentException("quality must be 0..100 .");
        }

        if (mRecycled.get()) {
            throw new IllegalStateException("Use YYJpeg object after it recycled !");
        }

        if (imgBuf == null || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("this method can only be use by a " +
                                                "YYJpeg object get from YYJpegFactory.decodeFile()");
        }

        return TJCompress(quality, stream, ByteBuffer.wrap(imgBuf), width, height);
    }

    private boolean TJCompress(int quality, OutputStream stream, ByteBuffer imgBuf, int width, int height) {
        boolean ret = false;
        int flags = 0;
        TJCompressor tjc = null;

        if (!mUseOptimize) {
            System.clearProperty(TJ_OPTIMIZE);
        }

        try {
            long time = System.currentTimeMillis();
            tjc = new TJCompressor();
            tjc.setSubsamp(TJ.SAMP_444);
            tjc.setJPEGQuality(quality);
            tjc.setSourceImage(imgBuf.array(), 0, 0, width, 0, height, TJ.PF_RGBA);
            byte[] jpegBuf = tjc.compress(flags);
            int jpegSize = tjc.getCompressedSize();

            YYLog.info(TAG, "compress cost:" + (System.currentTimeMillis() - time) + " ms, " +
                                                            "jpg size:" + jpegSize/1024.0f + " KB.");
            stream.write(jpegBuf, 0, jpegSize);
            stream.flush();
            ret = true;
        } catch (Exception e) {
            YYLog.error(TAG, "Exception: " + e.getMessage());
        }

        try {
            if (tjc != null) {
                tjc.close();
            }
        } catch (Exception e) {
            YYLog.error(TAG, "Exception: " + e.getMessage());
        }
        return ret;
    }


    static YYJpeg decodeStream(InputStream fis, boolean inJustDecodeBounds) {
        try {
            int jpegSize = fis.available();
            if (jpegSize < 1) {
                return null;
            }
            byte[] jpegBuf = new byte[jpegSize];

            if (fis.read(jpegBuf) != jpegSize) {
                YYLog.error(TAG, " Read size form stream not the same as available size.");
            }

            fis.close();

            YYJpeg yyJpeg = new YYJpeg();
            return yyJpeg.TJDecompress(jpegBuf, inJustDecodeBounds) ? yyJpeg : null;

        } catch (Exception e) {
            YYLog.error(TAG, "Exception: " + e.getMessage());
        }
        return null;
    }

    private boolean TJDecompress(byte[] jpegBuf, boolean inJustDecodeBounds) {
        int flags = 0;
        boolean ret = false;
        TJDecompressor tjd = null;
        try {
            imgBuf = null;
            tjd = new TJDecompressor(jpegBuf);
            width = tjd.getWidth();
            height = tjd.getHeight();
            YYLog.info(TAG,  "Input" +
                    " Image (jpg):  " + width + " x " + height +
                    " pixels, " + SUBSAMP_NAME[tjd.getSubsamp()] +
                    " subsampling, " + COLORSPACE_NAME[tjd.getColorspace()]);
            if (!inJustDecodeBounds) {
                imgBuf = tjd.decompress(width, 0, height, TJ.PF_RGBA, flags);
            }
            if (imgBuf != null) {
                ret = true;
            } else {
                YYLog.error(TAG, "tjd.decompress failed ~!");
            }

        } catch (Exception e) {
            YYLog.error(TAG, "Exception: " + e.getMessage());
        }

        try {
            if (tjd != null) {
                tjd.close();
            }
        }catch (Exception e) {
            YYLog.error(TAG, "Exception : " + e.getMessage());
        }
        return ret;
    }

    public void copyPixelsToBuffer(ByteBuffer dst) {
        if (mRecycled.get()) {
            throw new IllegalStateException("Use YYJpeg object after it recycled !");
        }

        if (imgBuf == null) {
            throw new IllegalArgumentException("this method can only be use by a " +
                    "YYJpeg object get from YYJpegFactory.decodeFile()");
        }

        if (dst.capacity() < imgBuf.length) {
            throw new IllegalArgumentException("Buffer not large enough for pixels");
        }


        System.arraycopy(imgBuf, 0, dst.array(), 0, imgBuf.length);
    }

    public void recycle() {
        if (imgBuf != null) {
            imgBuf = null;
        }
        mRecycled.set(true);
    }

    /**
     * Returns true if this YYJpeg has been recycled. If so, then it is an error
     * to try to access its pixels buffer (imgBuf).
     *
     * @return true if the YYJpeg has been recycled
     */
    public final boolean isRecycled() {
        return mRecycled.get();
    }

    public int getWidth() {
        if (mRecycled.get()) {
            YYLog.error(TAG, "Called getWidth() on a recycle()'d YYJpeg! This is undefined behavior!");
        }
        return width;
    }

    public int getHeight() {
        if (mRecycled.get()) {
            YYLog.error(TAG, "Called getHeight() on a recycle()'d YYJpeg! This is undefined behavior!");
        }
        return height;
    }

    public void disableOptimize() {
        mUseOptimize = false;
    }
}
