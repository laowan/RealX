package com.ycloud.jpeg;

import com.ycloud.utils.YYLog;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by Administrator on 2018/8/10.
 */

public class YYJpegFactory {
    public static class Options {
        /**
         * If set to true, the decoder will return null (no bitmap), but
         * the out... fields will still be set, allowing the caller to query
         * the bitmap without having to allocate the memory for its pixels.
         */
        public boolean inJustDecodeBounds;
    }

    /**
     * Decode a file path into a YYJpeg. If the specified file name is null,
     * or cannot be decoded into a YYJpeg, the function returns null.
     *
     * @param pathName complete path name for the file to be decoded.
     * @return the resulting decoded YYJpeg, or null if it could not be decoded.
     */
    public static YYJpeg decodeFile(String pathName) {
        return decodeFile(pathName, null);
    }

    /**
     * Decode a file path into a YYJpeg. If the specified file name is null,
     * or cannot be decoded into a YYJpeg, the function returns null.
     *
     * @param pathName complete path name for the file to be decoded.
     * @param opts null-ok; Options that control downsampling and whether the
     *             image should be completely decoded, or just is size returned.
     * @return The decoded YYJpeg, or null if the image data could not be
     *         decoded, or, if opts is non-null, if opts requested only the
     *         size be returned (in opts.outWidth and opts.outHeight)
     */
    public static YYJpeg decodeFile(String pathName, YYJpegFactory.Options opts) {
        YYJpeg bm = null;
        InputStream stream = null;
        try {
            boolean justDecodeHead = false;
            if (opts != null) {
                justDecodeHead = opts.inJustDecodeBounds;
            }
            stream = new FileInputStream(pathName);
            bm = YYJpeg.decodeStream(stream, justDecodeHead);
        } catch (Exception e) {
            /*  do nothing.
                If the exception happened on open, bm will be null.
            */
            YYLog.error("YYJpegFactory", "Unable to decode stream: " + e);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    // do nothing here
                }
            }
        }
        return bm;
    }

}
