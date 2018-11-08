package com.ycloud.mediacodec.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.util.Log;

public class Files {
	public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
		// TODO Auto-generated method stub
		File root = Environment.getExternalStorageDirectory();
		root.mkdirs();
		File path = new File(root, uri.getEncodedPath());

		Log.e("H3c", "opeFile:" + path);
		int imode = 0;
		if (mode.contains("w")) {
			imode |= ParcelFileDescriptor.MODE_WRITE_ONLY;
			if (!path.exists()) {
				try {
					path.createNewFile();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		if (mode.contains("r"))
			imode |= ParcelFileDescriptor.MODE_READ_ONLY;
		if (mode.contains("+"))
			imode |= ParcelFileDescriptor.MODE_APPEND;

		return ParcelFileDescriptor.open(path, imode);
	}
}
