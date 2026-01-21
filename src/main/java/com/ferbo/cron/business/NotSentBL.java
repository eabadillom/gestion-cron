package com.ferbo.cron.business;

import java.util.ArrayList;
import java.util.List;

import javax.mail.Message;

public class NotSentBL {
	public static List<Message> notSentMessages = null;
	
	static {
		notSentMessages = new ArrayList<Message>();
	}
}
