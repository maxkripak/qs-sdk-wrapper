package com.android.hardware.bluetooth.printer;

enum MessageType {
	Unknown,
	
	SearchForDevice,
	Connect,
	Disconnect,
	
	PrintText,
	PrintImage;
}