package com.ferbo.cron;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class SendMailInventarioJob implements Job {
	
	private static Logger log = LogManager.getLogger(SendMailInventarioJob.class);

	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {
		log.info("Iniciando envío de reportes de inventario por correo electrónico...");
		log.info("El envio de reportes de inventario terminó satisfactoriamente.");
	}

}
