package com.android.hardware.bluetooth.printer;

public interface DeviceStateListener {
	public void onBeginConnection();

	public void onConnected();

	public void onConnectionFailed();

	public void onConnectionLost();

	public void onDisconnected();
}
