package com.ferbo.cron.business;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.mail.Message;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ferbo.gestion.core.business.ContactosBL;
import com.ferbo.gestion.core.dao.MailDAO;
import com.ferbo.gestion.core.model.Cliente;
import com.ferbo.gestion.core.model.ClienteContacto;
import com.ferbo.gestion.core.model.Contacto;
import com.ferbo.gestion.core.model.Mail;
import com.ferbo.gestion.core.model.MedioContacto;
import com.ferbo.gestion.reports.jasper.ReporteSalidasJR;
import com.ferbo.gestion.tools.DateTools;
import com.ferbo.gestion.tools.IOTools;
import com.ferbo.mail.MailHelper;
import com.ferbo.mail.beans.Adjunto;
import com.ferbo.mail.beans.Correo;

public class SendMailSalidasBL {
	
	private static Logger log = LogManager.getLogger(SendMailSalidasBL.class);
	
	private Connection conn = null;
	private ContactosBL contactosBO = null;
	private static SendMailSalidasBL obj = null;
	private MailHelper helper = null;
	private List<Correo> correosList = null;
	private Cliente clienteActual = null;
	private MailDAO mailDAO = null;
	private Date fechaInicio = null;
	private Date fechaFin = null;
	
	private static final String SUBJECT_TEMPLATE = "Reporte de salidas %s - FERBO (del %s al %s)";
	
	private static boolean isRunning = false;
	
	private SendMailSalidasBL() {
		this.mailDAO = new MailDAO();
	}
	
	public static synchronized SendMailSalidasBL getInstance() {
		if(obj == null)
			obj = new SendMailSalidasBL();
		return obj;
	}
	
	public void exec() {
		//TODO pendiente implementar la ejecución general de todos los clientes.
	}
	
	public synchronized void sendMail(List<Cliente> clientes, Date fechaInicio, Date fechaFin)
	throws SQLException {
		this.fechaInicio = fechaInicio;
		this.fechaFin = fechaFin;
		
		isRunning = true;
		helper = new MailHelper("mail/inventarios");
		List<Message> notSentList = null;
		
		for(Cliente cliente : clientes) {
			this.clienteActual = cliente;
			this.processContacts(cliente);
		}
		notSentList = helper.sendMessages();
		
		isRunning = false;
	}
	
	private void processContacts(Cliente cliente)
	throws SQLException {
		if(cliente.isHabilitado() == false) {
            log.info("Cliente no habilitado: {}", cliente.getNumero());
            return;
        }
	    
		contactosBO = new ContactosBL(cliente.getIdCliente());
		correosList = new ArrayList<Correo>();
		
		List<ClienteContacto> clienteContactos = contactosBO.getClienteContactos(conn, cliente.getIdCliente());
		
		for(ClienteContacto clienteContacto : clienteContactos) {
			
			if(clienteContacto.isHabilitado() == false) {
				log.info("Contacto no habilitado: idContacto=" + clienteContacto.getIdContacto());
				continue;
			}
			
			if(clienteContacto.isInventario() == false) {
			    log.info("Contacto no habilitado para envío de reportes de inventario..." + clienteContacto);
			    continue;
			}
			
			log.info(String.format("Contacto: %s %s %s", clienteContacto.getContacto().getNombre(), clienteContacto.getContacto().getApellido1(), clienteContacto.getContacto().getApellido2()));
			this.processContact(clienteContacto.getContacto());
		}
		if(this.correosList.size() > 0)
		    this.processMail();
		contactosBO = null;
	}
	
	private void processContact(Contacto contacto)
	throws SQLException {
		List<MedioContacto> mediosContacto = contactosBO.getMediosContacto(conn, contacto.getIdContacto());
		
		for(MedioContacto medio : mediosContacto) {
			if("M".equalsIgnoreCase(medio.getTipoMedio()) == false) {
				continue;
			}
			
			Mail mail = mailDAO.get(conn, medio.getIdMail());
			String destinatario = String.format("%s %s %s", contacto.getNombre(), contacto.getApellido1(), contacto.getApellido2());
			Correo correo = new Correo(mail.getMail(), destinatario);
			correosList.add(correo);
			log.info("Correo agregado: " + correo);
		}
	}
	
	private void processMail() {
		String         mailHTML = null;
		File           mailFile = null;
		FileReader     mailReader = null;
		BufferedReader reader = null;
		StringBuilder  sb = null;
		String         subject = null;
		Adjunto        adjunto = null;
		
		
		byte[]         bytes = null;
		List<Adjunto> attachmentList = null;
		ReporteSalidasJR inventarioJR = null;
		
		String logoPath = null;
		File   fileLogoPath = null;
		
		try {
		    helper.newMessage();
		    subject = String.format(SUBJECT_TEMPLATE,
		    		this.clienteActual.getNombre(),
		    		DateTools.getString(this.fechaInicio, DateTools.FORMATO_DD_MM_YYYY),
		    		DateTools.getString(this.fechaFin, DateTools.FORMATO_DD_MM_YYYY)
	    		);
		    
		    mailHTML = "/mail/mailSalidas.html";
            mailFile = new File( getClass().getResource(mailHTML).getFile() );
            mailReader = new FileReader(mailFile);
            reader = new BufferedReader(mailReader);
            sb = new StringBuilder();
            String linea = null;
            while((linea = reader.readLine()) != null) {
                sb.append(linea);
            }
            String html = sb.toString();
		    
		    for(Correo mail : correosList) {
		        helper.addTo(mail);
		        log.info(String.format("Agregando mail %s", mail.getMail()));
		    }
            helper.setSubject(subject);
            helper.setMailBody(html);
            
            logoPath = "/images/logo.png";
            fileLogoPath = new File( getClass().getResource(logoPath).getFile());
            inventarioJR = new ReporteSalidasJR(conn, fileLogoPath.getPath());
            
            bytes = inventarioJR.getPDF(this.fechaInicio, this.fechaFin, this.contactosBO.getIdCliente(), null, null);
            String nombrePDF = String.format("ReporteSalidas.pdf");
            adjunto = new Adjunto(nombrePDF, Adjunto.TP_ARCHIVO_PDF, bytes);
            helper.addAttachment(adjunto);
            
            bytes = inventarioJR.getXLSX(this.fechaInicio, this.fechaFin, this.contactosBO.getIdCliente(), null, null);
            String nombreXLS = String.format("ReporteSalidas.xlsx");
            adjunto = new Adjunto(nombreXLS, Adjunto.TP_ARCHIVO_XLSX, bytes);
            helper.addAttachment(adjunto);
            
            attachmentList = helper.getAttachmentList();
            
            if(attachmentList == null || attachmentList.size() == 0) {
                log.info(String.format("El cliente %d no tiene inventario, no se enviará reporte de inventario.", contactosBO.getIdCliente()));
                return;
            }
            
            for(Adjunto attachment : attachmentList) {
                log.info(String.format("Tamaño del adjunto (%s): %d", attachment.getNombreArchivo(), attachment.getContenido().length));
            }
            
			helper.addMessage();
			
			log.info("El mensaje se agregó a la cola de envío de correos.");
			
		} catch(Exception ex) {
			log.error("Problema para generar el mensaje de correo electrónico...", ex);
		} finally {
		    IOTools.close(reader);
		}
	}

	public Connection getConn() {
		return conn;
	}

	public void setConn(Connection conn) {
		this.conn = conn;
	}

}
