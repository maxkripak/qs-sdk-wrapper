package com.android.hardware.bluetooth.printer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Message;

final class ServiceHandler extends Handler {

	private Map<Integer, Callable<Void>> mHandlers;

	@SuppressLint("UseSparseArrays")
	public ServiceHandler() {
		super();

		mHandlers = new HashMap<Integer, Callable<Void>>();
	}

	public void registerHandler(Integer type, Callable<Void> handler) {
		mHandlers.put(type, handler);
	}

	public void unregisterHandler(Integer type) {
		mHandlers.remove(type);
	}

	@Override
	public void handleMessage(Message msg) {
		super.handleMessage(msg);

		final int DEVICE_RESPONSE = 1;

		switch (msg.what) {
		case DEVICE_RESPONSE:
			int responseType = msg.arg1;
			Callable<Void> handler = mHandlers.get(responseType);

			if (handler != null)
				try {
					handler.call();
				} catch (Exception e) {
					e.printStackTrace();
				}

			break;
		}
	}
}
