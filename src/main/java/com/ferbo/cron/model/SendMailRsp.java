package com.ferbo.cron.model;

import com.google.gson.annotations.SerializedName;

public class SendMailRsp {
	
	@SerializedName(value = "code")
	private Integer code = null;
	
	@SerializedName(value = "message")
	private String message = null;
	
	public Integer getCode() {
		return code;
	}
	
	public void setCode(Integer code) {
		this.code = code;
	}
	
	public String getMessage() {
		return message;
	}
	
	public void setMessage(String message) {
		this.message = message;
	}
}
