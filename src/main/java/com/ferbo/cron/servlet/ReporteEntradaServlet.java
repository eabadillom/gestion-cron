package com.ferbo.cron.servlet;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ferbo.cron.business.SendMailEntradasBL;
import com.ferbo.cron.model.SendMailRsp;
import com.ferbo.gestion.core.business.ClienteBL;
import com.ferbo.gestion.core.model.Cliente;
import com.ferbo.gestion.reports.jasper.ReporteEntradasJR;
import com.ferbo.gestion.tools.DBManager;
import com.ferbo.gestion.tools.DateTools;
import com.ferbo.gestion.tools.FSTools;
import com.ferbo.gestion.tools.GestionException;
import com.ferbo.gestion.tools.business.PeriodoBL;
import com.ferbo.gestion.tools.model.Periodo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class ReporteEntradaServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	private static Logger log = LogManager.getLogger(ReporteEntradaServlet.class);
	
	public ReporteEntradaServlet() {
		super();
	}
	
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
	throws ServletException, IOException {
		
		String            numeroCliente = null;
		ClienteBL         clienteBO     = null;
		Cliente           cliente       = null;
		ReporteEntradasJR entradasJR     = null;
		Connection        conn          = null;
		byte[]            bytes         = null;
		OutputStream      output        = null;
		FSTools           fsTools       = null;
		String            logoPath      = null;
		Date              fecha   = null;
		Periodo           periodoSemanal = null;
		
		try {
			numeroCliente = request.getParameter("numeroCliente");
			logoPath = "/images/logo.png";
			conn = DBManager.getConnection("jdbc/gestion");
			clienteBO = new ClienteBL(conn);
			cliente = clienteBO.get(numeroCliente);
			
			//Debido a que este reporte se va a ejecutar los lunes de cada semana,
			//obtenemos un Date con una semana atrás.
			fecha = DateTools.addDay(new Date(), -7);
			
			periodoSemanal = PeriodoBL.getSemanaLunesADomingo(
						DateTools.getAnio(fecha),
						DateTools.getSemanaAnio(fecha)
					).orElseThrow(() -> new GestionException("No es posible determinar el periodo."));
			
			log.info("Reporte de entradas {}, Periodo: {} al {}",
					cliente.getNombre(),
					DateTools.getString(periodoSemanal.getFechaInicio(), DateTools.FORMATO_DD_MM_YYYY),
					DateTools.getString(periodoSemanal.getFechaFin(),    DateTools.FORMATO_DD_MM_YYYY));
			
			fsTools = new FSTools();
			
			entradasJR = new ReporteEntradasJR(conn, fsTools.getResourcePath(logoPath));
			
			bytes = entradasJR.getPDF(periodoSemanal.getFechaInicio(), periodoSemanal.getFechaFin(), cliente.getIdCliente(), null, null);
			
			response.setContentType("application/pdf");
			response.setHeader("Content-Type", "application/pdf");
			response.setHeader("Content-Disposition", "attachment; filename=\"ReporteEntradas.pdf\"");
			response.setContentLength(bytes.length);
			
			output = response.getOutputStream();
			output.write(bytes, 0, bytes.length);
			output.flush();
			output.close();
			
		} catch(Exception ex) {
			log.error("Problema para ejecutar el reporte de entradas...", ex);
		} finally {
			DBManager.close(conn);
		}
	}
	
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
	throws ServletException, IOException {
		String             numeroCliente = null;
		SendMailEntradasBL mailBO        = null;
		List<Cliente>      clientes      = null;
		ClienteBL          clienteBO     = null;
		Cliente            cliente       = null;
		Connection         conn          = null;
		String             mensaje       = null;
		
		Gson               prettyGson    = null;
		String             jsonResponse  = null;
		SendMailRsp        respuesta     = null;
		Integer            httpStatus    = -1;
		
		Date               fecha   = null;
		Periodo            periodoSemanal = null;
		
		try {
			respuesta = new SendMailRsp();
			prettyGson = new GsonBuilder().setPrettyPrinting().create();
			numeroCliente = request.getParameter("numeroCliente");
			mailBO = SendMailEntradasBL.getInstance();
			conn = DBManager.getConnection();
			
			if(numeroCliente == null || "".equals(numeroCliente.trim())) {
				mensaje = "Proceso concluido.";
			} else {
				//Debido a que este reporte se va a ejecutar los lunes de cada semana,
				//obtenemos un Date con una semana atrás.
				fecha = DateTools.addDay(new Date(), -7);
				
				clienteBO = new ClienteBL(conn);
				cliente = clienteBO.get(numeroCliente);
				
				periodoSemanal = PeriodoBL.getSemanaLunesADomingo(
						DateTools.getAnio(fecha), DateTools.getSemanaAnio(fecha)
					).orElseThrow(() -> new GestionException("No es posible determinar el periodo."));
				
				log.info("Reporte de entradas {}, Periodo: {} al {}",
				cliente.getNombre(),
				DateTools.getString(periodoSemanal.getFechaInicio(), DateTools.FORMATO_DD_MM_YYYY),
				DateTools.getString(periodoSemanal.getFechaFin(),    DateTools.FORMATO_DD_MM_YYYY));
				
				clientes = new ArrayList<Cliente>();
				clientes.add(cliente);
				mailBO.setConn(conn);
				mailBO.sendMail(clientes, periodoSemanal.getFechaInicio(), periodoSemanal.getFechaFin());
				
				mensaje = "El reproceso del reporte terminó correctamente.";
				log.info("El reporte de entradas del cliente {} terminó correctamente.", cliente.getNombre());
			}
				
			respuesta.setCode(0);
			respuesta.setMessage(mensaje);
			httpStatus = HttpServletResponse.SC_OK;
			
		} catch(Exception ex) {
			log.error("Problema con el envío de los reportes de entradas automáticos...", ex);
			respuesta.setCode(1);
			respuesta.setMessage("El envío de reportes tuvo un problema. Verifique las bitácoras.");
			httpStatus = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
		} finally {
			DBManager.close(conn);
			jsonResponse = prettyGson.toJson(respuesta);
			
			response.setStatus(httpStatus);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().print(jsonResponse);
            response.getWriter().flush();
		}
	}
}
