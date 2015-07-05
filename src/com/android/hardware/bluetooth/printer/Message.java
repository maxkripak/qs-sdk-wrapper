package com.android.hardware.bluetooth.printer;

final class Message {
	private MessageType mType;
	private Object mData;

	public Message(MessageType type) {
		this(type, null);
	}

	public Message(MessageType type, Object data) {
		mType = type;
		mData = data;
	}

	public MessageType getType() {
		return mType;
	}

	public void setType(MessageType type) {
		mType = type;
	}

	public Object getData() {
		return mData;
	}

	public void setData(Object data) {
		mData = data;
	}
}