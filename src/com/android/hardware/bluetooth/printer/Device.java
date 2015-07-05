package com.android.hardware.bluetooth.printer;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import com.example.bluetoothprinter.BlueToothService;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.graphics.Bitmap;

public final class Device {

	private AtomicBoolean mRunning;

	private Thread mConsumerThread;
	private ConcurrentLinkedQueue<Message> mMessages;

	private ServiceHandler mHandler;

	private volatile String mCurrentMacAddress;

	private Context mContext;

	private DeviceSearchListener mSearchListener;
	private DeviceStateListener mPrintDeviceStateListener;
	private PrintStateChangedListener mPrintStateChangedListener;

	private Object mMessageWaiter = new Object();

	public Device(Context context) {
		mRunning = new AtomicBoolean(false);
		mContext = context;

		initializeServiceHandler();
	}

	private void initializeServiceHandler() {
		mHandler = new ServiceHandler();

		mHandler.registerHandler(BlueToothService.STATE_CONNECTED, mSuccessConnectionHandler);
		mHandler.registerHandler(BlueToothService.SUCCESS_CONNECT, mSuccessConnectionHandler);

		mHandler.registerHandler(BlueToothService.FAILED_CONNECT, mFailedConnectionHandler);
		mHandler.registerHandler(BlueToothService.LOSE_CONNECT, mLostConnectionHandler);
	}

	private Callable<Void> mSuccessConnectionHandler = new Callable<Void>() {

		@Override
		public Void call() throws Exception {
			if (mPrintDeviceStateListener != null)
				mPrintDeviceStateListener.onConnected();

			return null;
		}

	};

	private Callable<Void> mFailedConnectionHandler = new Callable<Void>() {

		@Override
		public Void call() throws Exception {
			setMacAddress("");

			if (mPrintDeviceStateListener != null)
				mPrintDeviceStateListener.onConnectionFailed();

			disconnect();
			return null;
		}

	};

	private Callable<Void> mLostConnectionHandler = new Callable<Void>() {

		@Override
		public Void call() throws Exception {
			setMacAddress("");

			if (mPrintDeviceStateListener != null)
				mPrintDeviceStateListener.onConnectionFailed();

			disconnect();
			return null;
		}

	};

	public void setSearchListener(DeviceSearchListener listener) {
		mSearchListener = listener;
	}

	public void setPrintDeviceStateListener(DeviceStateListener listener) {
		mPrintDeviceStateListener = listener;
	}

	public void setPrintStateListener(PrintStateChangedListener listener) {
		mPrintStateChangedListener = listener;
	}

	public void run() {
		if (mRunning.get())
			return;

		mRunning.set(true);
		mMessages = new java.util.concurrent.ConcurrentLinkedQueue<Message>();

		mConsumerThread = new Thread(mConsumerRunnable);
		mConsumerThread.start();
	}

	public void stop() {
		if (!mRunning.get())
			return;

		mRunning.set(false);
	}

	public void search() {
		postMessage(new Message(MessageType.SearchForDevice));
	}

	public void connect(String mac) {
		postMessage(new Message(MessageType.Connect, mac));
	}

	public void disconnect() {
		mMessages.clear();
		postMessage(new Message(MessageType.Disconnect));
	}

	public void printText(String text) {
		postMessage(new Message(MessageType.PrintText, text));
	}

	public void printImage(Bitmap bitmap) {
		postMessage(new Message(MessageType.PrintImage, bitmap));
	}

	private void postMessage(Message message) {
		if (!mRunning.get())
			return;

		mMessages.add(message);
		notifyForMessages();
	}

	private void waitForServiceBinder(final BlueToothService service) {
		final int WAIT_TIMEOUT = 500;

		Object waiter = new Object();

		while (!service.IsOpen() && mRunning.get()) {
			try {
				waiter.wait(WAIT_TIMEOUT);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private void searchForDevice(final BlueToothService service) {
		if (!service.IsOpen())
			service.OpenDevice();

		waitForServiceBinder(service);

		if (service.GetScanState() == BlueToothService.STATE_SCANING)
			return;

		Set<BluetoothDevice> devices = service.GetBondedDevice();

		if (mSearchListener != null) {
			for (BluetoothDevice currentDevice : devices) {
				if (mSearchListener != null) {
					boolean processNext = mSearchListener.onNewDevice(currentDevice.getName(),
							currentDevice.getAddress());

					if (!processNext)
						return;
				}
			}
		}

		service.ScanDevice();
		service.setOnReceive(new BlueToothService.OnReceiveDataHandleEvent() {

			public void OnReceive(BluetoothDevice device) {
				boolean isScanFinished = device == null;
				if (isScanFinished) {
					service.StopScan();

					if (mSearchListener != null)
						mSearchListener.onStop();
				} else {
					if (mSearchListener != null) {
						if (!mSearchListener.onNewDevice(device.getName(), device.getAddress())) {
							service.StopScan();

							if (mSearchListener != null) {
								mSearchListener.onStop();
							}
						}
					}
				}
			}
		});

		if (mSearchListener != null)
			mSearchListener.onStart();
	}

	private void connectTo(String mac, final BlueToothService service) {
		service.ConnectToDevice(mac);
		setMacAddress(mac);

		if (mPrintDeviceStateListener != null)
			mPrintDeviceStateListener.onBeginConnection();
	}

	private void disconnectFrom(final BlueToothService service) {
		service.DisConnected();

		final int DISCONNECT_WAIT_TIMEOUT = 200;

		Object waiter = new Object();
		try {
			waiter.wait(DISCONNECT_WAIT_TIMEOUT);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private boolean isDeviceNotReady(final BlueToothService service) {
		return !service.HasDevice() || service.getState() != BlueToothService.STATE_CONNECTED;
	}

	private void printTextOn(String message, final BlueToothService service) {
		if (isDeviceNotReady(service)) {
			if (mPrintStateChangedListener != null)
				mPrintStateChangedListener.onFailed();

			return;
		}

		final byte[] PRINT_MODE_COMMAND = new byte[] { 27, 56, 2 };

		service.write(PRINT_MODE_COMMAND);
		service.PrintCharacters(message);

		if (mPrintStateChangedListener != null)
			mPrintStateChangedListener.onSuccess();
	}

	private void printImageOn(Bitmap bitmap, final BlueToothService service) {
		if (isDeviceNotReady(service)) {
			if (mPrintStateChangedListener != null)
				mPrintStateChangedListener.onFailed();

			return;
		}

		service.PrintImage(bitmap);

		if (mPrintStateChangedListener != null)
			mPrintStateChangedListener.onSuccess();
	}

	private void handleMessage(Message message, final BlueToothService service) {
		switch (message.getType()) {
		case SearchForDevice:
			searchForDevice(service);
			break;

		case Connect:
			connectTo((String) message.getData(), service);
			break;

		case Disconnect:
			disconnectFrom(service);
			break;

		case PrintText:
			printTextOn((String) message.getData(), service);
			break;

		case PrintImage:
			printImageOn((Bitmap) message.getData(), service);
			break;

		case Unknown:
		default:
			break;
		}
	}

	private synchronized void setMacAddress(String address) {
		mCurrentMacAddress = address;
	}

	public synchronized String getMacAddress() {
		return mCurrentMacAddress;
	}

	private Runnable mConsumerRunnable = new Runnable() {
		private BlueToothService mService;

		public void run() {
			mService = new BlueToothService(Device.this.mContext, Device.this.mHandler);

			if (!mService.IsOpen())
				mService.OpenDevice();

			while (mRunning.get()) {
				if (mMessages.isEmpty())
					waitForMessages();
				else
					handleMessage(mMessages.poll(), mService);
			}

			disconnectFrom(mService);
		}
	};

	private void waitForMessages() {
		synchronized (mMessageWaiter) {
			try {
				mMessageWaiter.wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private void notifyForMessages() {
		synchronized (mMessageWaiter) {
			mMessageWaiter.notifyAll();
		}
	}
}
