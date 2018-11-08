package com.ycloud.player.widget;

import android.os.Message;

public interface MediaPlayerListener {
	
	public static final int MSG_PLAY_ERROR = -1;
	
	public static final int MSG_PLAY_BUFFERING_START = 1;
	
	public static final int MSG_PLAY_BUFFERING_END = 2;
	
	public static final int MSG_PLAY_PREPARED = 3;

	public static final int MSG_PLAY_COMPLETED = 4;

	public static final int MSG_PLAY_BUFFERING_UPDATE = 5;

	public static final int MSG_PLAY_SEEK_COMPLETED = 6;

	public static final int MSG_PLAY_PLAYING = 7;
	
	void notify(Message msg);
}
