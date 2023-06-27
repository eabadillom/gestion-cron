package com.ferbo.cron.servlet;

import java.io.IOException;
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
import com.ferbo.gestion.model.Cliente;
import com.ferbo.gestion.tools.DBManager;

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

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		doGet(request, response);
	}

}
