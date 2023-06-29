package com.ferbo.cron.servlet;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ferbo.cron.business.SendMailInventarioBL;
import com.ferbo.gestion.business.ClienteBL;
import com.ferbo.gestion.jasper.ReporteInventarioJR;
import com.ferbo.gestion.model.Cliente;
import com.ferbo.gestion.tools.DBManager;
import com.ferbo.gestion.tools.FSTools;

/**
 * Servlet implementation class ReporteInventarioServlet
 */
public class ReporteInventarioServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static Logger log = LogManager.getLogger(ReporteInventarioServlet.class);
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public ReporteInventarioServlet() {
        super();
    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String numeroCliente = null;
		ClienteBL clienteBO = null;
		Cliente cliente = null;
		ReporteInventarioJR inventarioJR = null;
		Connection conn = null;
		byte[] bytes = null;
		OutputStream output = null;
		FSTools fsTools = new FSTools();
		String logoPath = null;
		
		try {
			numeroCliente = request.getParameter("numeroCliente");
			logoPath = "/images/logo.png";
			conn = DBManager.getConnection();
			clienteBO = new ClienteBL(conn);
			cliente = clienteBO.get(numeroCliente);
			inventarioJR = new ReporteInventarioJR(conn, fsTools.getResourcePath(logoPath));
			
			bytes = inventarioJR.getPDFReporteInventario(cliente.getIdCliente(), null);
			
			response.setContentType("application/pdf");
			response.setHeader("Content-Type", "application/pdf");
			response.setHeader("Content-Disposition", "attachment; filename=\"ReporteInventario.pdf\"");
			response.setContentLength(bytes.length);
			
			output = response.getOutputStream();
			output.write(bytes, 0, bytes.length);
			output.flush();
			output.close();
			
		} catch(Exception ex) {
			log.error("Problema con el envío de los reportes de inventario automáticos...", ex);
		} finally {
			DBManager.close(conn);
		}
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		String numeroCliente = null;
		SendMailInventarioBL mailBO = null;
		List<Cliente> clientes = null;
		ClienteBL clienteBO = null;
		Cliente cliente = null;
		
		Connection conn = null;
		
		try {
			numeroCliente = request.getParameter("numeroCliente");
			mailBO = SendMailInventarioBL.getInstance();
			conn = DBManager.getConnection();
			
			if(numeroCliente == null || "".equals(numeroCliente.trim())) {
				mailBO.exec();
			} else {
				clienteBO = new ClienteBL(conn);
				cliente = clienteBO.get(numeroCliente);
				clientes = new ArrayList<Cliente>();
				clientes.add(cliente);
				mailBO.setConn(conn);
				mailBO.sendMail(clientes);
			}
			
		} catch(Exception ex) {
			log.error("Problema con el envío de los reportes de inventario automáticos...", ex);
		} finally {
			DBManager.close(conn);
		}
	}

}
