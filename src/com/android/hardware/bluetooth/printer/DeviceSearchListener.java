package com.android.hardware.bluetooth.printer;

public interface DeviceSearchListener {
	public void onStart();

	public void onStop();

	public boolean onNewDevice(final String name, final String macAddress);
}
