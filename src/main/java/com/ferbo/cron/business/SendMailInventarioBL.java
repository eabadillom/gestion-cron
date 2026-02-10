package com.ferbo.cron.business;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.Message;
import javax.mail.MessagingException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ferbo.gestion.core.business.ClienteBL;
import com.ferbo.gestion.core.business.ContactosBL;
import com.ferbo.gestion.core.business.InventarioBO;
import com.ferbo.gestion.core.dao.EnvioInventarioDAO;
import com.ferbo.gestion.core.dao.IDAO;
import com.ferbo.gestion.core.dao.MailDAO;
import com.ferbo.gestion.core.model.Cliente;
import com.ferbo.gestion.core.model.ClienteContacto;
import com.ferbo.gestion.core.model.Contacto;
import com.ferbo.gestion.core.model.EnvioInventario;
import com.ferbo.gestion.core.model.Mail;
import com.ferbo.gestion.core.model.MedioContacto;
import com.ferbo.gestion.reports.jasper.ReporteInventarioJR;
import com.ferbo.gestion.tools.DBManager;
import com.ferbo.gestion.tools.GestionException;
import com.ferbo.gestion.tools.IOTools;
import com.ferbo.mail.MailHelper;
import com.ferbo.mail.beans.Adjunto;
import com.ferbo.mail.beans.Correo;


public class SendMailInventarioBL {

	private static Logger log = LogManager.getLogger(SendMailInventarioBL.class);
	
	private Connection conn = null;
	private ContactosBL contactosBO = null;
	private static SendMailInventarioBL obj = null;
	private MailHelper helper = null;
	private List<Correo> correosList = null;
	private Cliente clienteActual = null;
	private MailDAO mailDAO = null;
	private static final String SUBJECT_TEMPLATE = "Reporte de inventario %s - FERBO";
	
	private static boolean isRunning = false;
	
	public Connection getConn() {
		return conn;
	}
	
	public void setConn(Connection conn) {
		this.conn = conn;
	}
	
	private SendMailInventarioBL() {
		this.mailDAO = new MailDAO();
	}
	
	public static synchronized SendMailInventarioBL getInstance() {
		if(obj == null)
			obj = new SendMailInventarioBL();
		return obj;
	}
	
	public void exec() {
	    List<Cliente> clientes = null;
	    ClienteBL clienteBO = null;
	    try {
	        this.conn = DBManager.getConnection();
	        
	        clienteBO = new ClienteBL(conn);
	        
	        if(isRunning == false) {
	            clientes = clienteBO.get(true);
                this.sendMail(clientes);
	        } else {
	            log.info("El proceso de envío de reportes de inventario ya se encuentra en ejecución.");
	        }
	        
	    } catch(Exception ex) {
	        log.error("Problema para realizar el envío de reporte de inventario automático...", ex);
	    } finally {
	        DBManager.close(this.conn);
	    }
	}
	
	public void exec(String... numerosCliente) {
		List<Cliente> clientes = new ArrayList<Cliente>();
	    ClienteBL clienteBO = null;
	    try {
	        this.conn = DBManager.getConnection();
	        
	        clienteBO = new ClienteBL(conn);
	        
	        if(isRunning == false) {
	        	
	        	for(String numero : numerosCliente) {
	        		Cliente cliente = clienteBO.get(numero);
	        		clientes.add(cliente);
	        	}
	            
                this.sendMail(clientes);
	        } else {
	            log.info("El proceso de envío de reportes de inventario ya se encuentra en ejecución.");
	        }
	        
	    } catch(Exception ex) {
	        log.error("Problema para realizar el envío de reporte de inventario automático...", ex);
	    } finally {
	        DBManager.close(this.conn);
	    }
	}
	
	public void sendMail(List<Cliente> clientes) throws SQLException {
	    isRunning = true;
	    helper = new MailHelper("mail/inventarios");
	    List<Message> notSentList = null;
	    
	    InventarioBO   inventarioBO = null;
	    inventarioBO = new InventarioBO(conn);
	    
	    for(Cliente cliente : clientes) {
    		this.clienteActual = cliente;
    		try {
    			if(inventarioBO.tieneInventario(cliente.getIdCliente(), new Date()) == false)
    				continue;
    		} catch(GestionException ex) {
    			log.info("Problema para validar la existencia de inventario del cliente...", ex);
    		}
    		log.info("----------------------------------------------------------------------------------");
    		log.info("Cliente: {} - {}", cliente.getNumero(), cliente.getNombre());
    		this.processContacts(cliente);
    	}
	    
			
		notSentList = helper.sendMessages();
		
		this.handleNotSent(clientes, notSentList);
		
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
		String         mailInventarioHTML = null;
		File           mailInventarioFile = null;
		FileReader     mailInventarioReader = null;
		BufferedReader reader = null;
		StringBuilder  sb = null;
		String         subject = null;
		Adjunto        adjunto = null;
		
		
		byte[]         bytes = null;
		List<Adjunto> attachmentList = null;
		ReporteInventarioJR inventarioJR = null;
		
		String logoPath = null;
		File   fileLogoPath = null;
		
		try {
		    helper.newMessage();
		    subject = String.format(SUBJECT_TEMPLATE, this.clienteActual.getNombre());
		    
		    mailInventarioHTML = "/mail/mailInventario.html";
            mailInventarioFile = new File( getClass().getResource(mailInventarioHTML).getFile() );
            mailInventarioReader = new FileReader(mailInventarioFile);
            reader = new BufferedReader(mailInventarioReader);
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
            inventarioJR = new ReporteInventarioJR(conn, fileLogoPath.getPath());
            
            bytes = inventarioJR.getPDFReporteInventario(contactosBO.getIdCliente(), null) ;
            String nombrePDF = String.format("ReporteInventario.pdf");
            adjunto = new Adjunto(nombrePDF, Adjunto.TP_ARCHIVO_PDF, bytes);
            helper.addAttachment(adjunto);
            
            bytes = inventarioJR.getXLSReporteInventario(contactosBO.getIdCliente(), null);
            String nombreXLS = String.format("ReporteInventario.xls");
            adjunto = new Adjunto(nombreXLS, Adjunto.TP_ARCHIVO_XLS, bytes);
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
	
	private void handleNotSent(List<Cliente> clientes, List<Message> notSentList)
	throws SQLException {
		String regex = Pattern.quote("^".concat(SUBJECT_TEMPLATE).concat("$")).replace("%s", "(.+?)"); 
		Pattern pattern = Pattern.compile(regex);
		
		List<Cliente> reprocessList = new ArrayList<Cliente>();
		
		for(Cliente cliente : clientes) {
			Message encontrado = notSentList.stream()
					.filter( m -> {
						Matcher matcher = null;
						try {
							matcher = pattern.matcher(m.getSubject());
						} catch (MessagingException e) {
							e.printStackTrace();
						}
						return matcher.find() && matcher.group(1).equals(cliente.getNombre());
					})
					.findFirst()
					.orElse(null)
					;
			if(encontrado == null)
				continue;
			
			reprocessList.add(cliente);
		}
		
		for(Cliente cliente : reprocessList) {
			EnvioInventario envioInventario = EnvioInventario.builder()
				.IdCliente(cliente.getIdCliente())
				.FechaEnvio(LocalDate.now())
				.Enviado(Boolean.FALSE)
				.build();
			
			IDAO<EnvioInventario> envioDAO = new EnvioInventarioDAO();
			envioDAO.insert(conn, envioInventario);
		}
		conn.commit();
	}
}
