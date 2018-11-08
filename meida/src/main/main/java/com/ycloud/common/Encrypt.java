package com.ycloud.common;

import java.io.FileInputStream;
import java.io.IOException;

import android.util.Log;

public class Encrypt {

	private static final String TAG = "Encrypt";
	private static final long DISTURB_RAND_NUM = 8;
	private static final int FIRST_ENCRYPT_ALGORITHM_INDEX = 1;
	private static final int ENCRYPT_ALGORITHM_NUM = 2;

	public static final int USE_RC4 = 1;
	public static final int USE_CHACHA20 = 2;

	public static String decrypt(FileInputStream inputStream, String key, int decryptType) {
		if (inputStream == null || key == null) {
			Log.e(TAG, "NULL POINTER");
			return null;
		}

		if (decryptType < FIRST_ENCRYPT_ALGORITHM_INDEX || decryptType > ENCRYPT_ALGORITHM_NUM) {
			Log.e(TAG, "Unknown decryptType");
			return null;
		}
		String decryptResult = null;
		try {
			int textLength = 0, rand_val = 0;
			int result;
			byte[] lengthBuffer = new byte[4];
			result = inputStream.read(lengthBuffer);
			if (result != -1) {
				textLength = byte2int(lengthBuffer);

				if (textLength == 1 || textLength == 0)
					return null;

				char[] tempchars = new char[textLength];

				result = inputStream.read(lengthBuffer);
				if (result != -1)
					rand_val = byte2int(lengthBuffer);

				for (int i = 0; i < textLength; i++) {
					tempchars[i] = (char) (inputStream.read() ^ (rand_val & 255));
				}
				switch (decryptType) {
				case USE_RC4:
					decryptResult = encrypt_core_RC4(new String(tempchars), key);
					break;
				default:
					break;
				}

			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return decryptResult;
	}

	private static String encrypt_core_RC4(String text, final String _key) {
		char[] plaintext = text.toCharArray();
		int[] keyStream = new int[plaintext.length];
		char[] cipherText = new char[plaintext.length];
		char[] key = _key.toCharArray();

		int[] S = new int[256];
		int i, j = 0, k;
		int tmp;

		if (plaintext == null || key == null) {
			Log.i(TAG, "null pointer\n");
			return null;
		}

		int textlength = text.length(), keylength = _key.length();
		if (keylength > 0) {
			// Key Schedule Algorithm
			for (i = 0; i < 256; i++)
				S[i] = i;

			for (i = 0; i < 256; i++) {
				j = (j + S[i] + key[i % keylength]) % 256;
				tmp = S[i];
				S[i] = S[j];
				S[j] = tmp;
			}
			// PRGA(Pseudo Random Generation Algorithm)
			i = j = k = 0;
			while (k < textlength) {
				i = (i + 1) % 256;
				j = (j + S[i]) % 256;
				tmp = S[i];
				S[i] = S[j];
				S[j] = tmp;
				keyStream[k++] = S[(S[i] + S[j]) % 256];
			}
			// XOR
			for (k = 0; k < textlength; k++) {
				cipherText[k] = (char) ((keyStream[k] ^ plaintext[k]) & 255);
				// System.out.println("iOutputChar[x]:"+cipherText[k]);
			}
		} else { // keylength=0
			for (k = 0; k < textlength; k++) {
				cipherText[k] = plaintext[k];
			}
		}
		return new String(cipherText);
	}

	public static String getFileKey(FileInputStream inputStream) {
		int keylength = 0;
		char[] key = null;
		byte[] keylengthBuffer = new byte[4];
		try {
			while (((char) inputStream.read()) != '\n')
				;
			inputStream.skip(DISTURB_RAND_NUM);
			inputStream.read(keylengthBuffer);
			keylength = byte2int(keylengthBuffer);
			key = new char[keylength];
			for (int i = 0; i < keylength; i++)
				key[i] = (char) inputStream.read();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return String.valueOf(key);
	}

	public static int byte2int(byte[] res) {
		int targets = (res[0] & 0xff) | (res[1] << 8) | (res[2] << 16) | (res[3] << 24);
		return targets;
	}
}
