package com.ferbo.cron;

import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.ferbo.cron.business.SendMailSalidasBL;
import com.ferbo.gestion.tools.DateTools;
import com.ferbo.gestion.tools.GestionException;
import com.ferbo.gestion.tools.business.PeriodoBL;
import com.ferbo.gestion.tools.model.Periodo;

public class SendMailSalidasJob implements Job {
	
	private static Logger log = LogManager.getLogger(SendMailSalidasJob.class);

	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {
		Date    fecha   = null;
		Periodo periodoSemanal = null;
		String  numeroCliente = null;
		JobDataMap         parametros = null;
		SendMailSalidasBL  sendMailBO = null;
		String[] numerosCliente = null;
		
		try {
			parametros = context.getMergedJobDataMap();
			sendMailBO = SendMailSalidasBL.getInstance();
			log.info("Ejecutando job de salidas...");
			fecha = DateTools.addDay(new Date(), -7);
			periodoSemanal = PeriodoBL.getSemanaLunesADomingo(DateTools.getAnio(fecha), DateTools.getSemanaAnio(fecha))
					.orElseThrow(() -> new GestionException("No es posible determinar el periodo."));
			
			numeroCliente = (String) parametros.get("numeroCliente");
			
			if(numeroCliente == null)
				log.info("Ninguna acción a realizar por el momento.");
			else {
				numerosCliente = numeroCliente.split(",");
				sendMailBO.exec(periodoSemanal, numerosCliente);
			}
			
			log.info("El envío de reportes de salidas terminó satisfactoriamente.");
		} catch(Exception ex) {
			log.error("Problema con el envío de los reportes de salidas automáticos...", ex);
		}
	}
}
