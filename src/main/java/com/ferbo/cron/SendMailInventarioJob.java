package com.ferbo.cron;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.ferbo.cron.business.SendMailInventarioBL;

public class SendMailInventarioJob implements Job {
	
	private static Logger log = LogManager.getLogger(SendMailInventarioJob.class);

	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {
		log.info("Iniciando envío de reportes de inventario por correo electrónico...");
		
		JobDataMap parametros = context.getMergedJobDataMap();
		SendMailInventarioBL sendMailBO = SendMailInventarioBL.getInstance();
		
		String numeroCliente = (String) parametros.get("numeroCliente");
		
		if(numeroCliente == null)
			sendMailBO.exec();
		else {
			String[] numerosCliente = numeroCliente.split(",");
			sendMailBO.exec(numerosCliente);
		}
		
		log.info("El envio de reportes de inventario terminó satisfactoriamente.");
	}

}
