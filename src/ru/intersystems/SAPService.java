/**
 The MIT License (MIT)

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), 
to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, 
and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, 
DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, 
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 */
package ru.intersystems;

/**
 * @author trefilov_d Dmitry.Trefilov@intersystems.com
 * 
 */

import com.intersystems.gateway.bh.BusinessService;
import com.intersystems.gateway.bh.Production;
import com.intersystems.gateway.bh.Production.Severity;

import com.sap.conn.idoc.jco.*;
import com.sap.conn.idoc.*;
import java.io.*;
import com.sap.conn.jco.server.*;

import java.sql.DriverManager;
import com.intersystems.jdbc.IRISConnection;
import com.intersystems.jdbc.IRIS;


/**
 * 
 * Java Business Service intended to listen for and deal with incoming iDocs.
 *
 */
public class SAPService implements BusinessService {

	// Settings available for this Service. Can be extended and granulized, but for now decided to use .jCoDestination and .jCoServer parameter files. 
	public static final String SETTINGS = "JCoDestinationsDir,JCoName,IRISHost,IRISPort,IRISNamespace,IRISUsername,IRISPassword";

	// Listener Thread
	static Thread messageThread = null;

	// Production pointer
	static Production production = null;

	// .jCoDestination file name (without extension)
	String destinationName = null;

	// Path to search for destinations files
	String destDir = null;

	// IDocServer object
	JCoIDocServer server;

	// Holding IRIS objects
	static IRISConnection irisConn;
	static IRIS irisInstance;



	/**
	 * Initialization (called when Production and/or business element starts
	 */
	@Override
	public boolean OnInit(Production p) throws Exception {
		production = p;

		try {

			// Getting settings and dump it to Events
			destDir = production.GetSetting("JCoDestinationsDir");
			System.setProperty("jco.destinations.dir", destDir);
			production.LogMessage("jco.destinations.dir=" + destDir, Production.Severity.INFO);

			destinationName = production.GetSetting("JCoName");
			production.LogMessage("JCoName=" + destinationName, Severity.INFO);

			// Preparing and Starting Server
			server = JCoIDoc.getServer(destinationName); 
			production.LogMessage("iDoc Server Initialized", Severity.INFO);

			server.setIDocHandlerFactory(new MyIDocHandlerFactory());
			server.setTIDHandler(new MyTidHandler());
			production.LogMessage("TID handler Started", Severity.INFO);

			MyThrowableListener listener = new MyThrowableListener();
			server.addServerErrorListener(listener);
			server.addServerExceptionListener(listener);
			server.setConnectionCount(1);
			server.start();
			production.LogMessage("iDoc Server Started", Severity.INFO);


		}
		catch (Exception e) 
		{
			production.LogMessage(e.getMessage(), Severity.ERROR);
			return false;
		}

		// Connecting to IRIS
		try {
			production.LogMessage("(back-)Connecting to IRIS...", Severity.INFO);
			irisConn = (IRISConnection) DriverManager.getConnection
					("jdbc:IRIS://" + 
							production.GetSetting("IRISHost") + ":" +
							production.GetSetting("IRISPort") + "/" +
							production.GetSetting("IRISNamespace"),
							production.GetSetting("IRISUsername"),
							production.GetSetting("IRISPassword")
							);

			// create IRIS Native object
			irisInstance = IRIS.createIRIS(irisConn);
			production.LogMessage("Connected to IRIS.", Severity.INFO);
		}
		catch (Exception e) {
			production.LogMessage(e.getMessage(), Severity.ERROR);
			irisConn.close();
			irisInstance.close();
			return false;
		}

		// All is OK
		return true;
	}

	/**
	 * Stopping Server. Called when Production and/or business element stops.
	 */
	@Override
	public boolean OnTearDown() throws Exception {
		production.LogMessage("Stopping JCoServer...", Severity.INFO);
		server.stop();

		// JCoIDocServer need some time to reach stopped state
		while (server.getState() != JCoServerState.STOPPED) {
			production.LogMessage("Waiting for JCoServer to stop...", Severity.INFO);
			Thread.sleep(500);
		}
		production.LogMessage("JCoServer stopped, Releasing...", Severity.INFO);

		// Releasing all allocated resources
		server.release();
		production.LogMessage("JCoServer released", Severity.INFO);

		return true;
	}

	/**
	 * 
	 * Handle received IDoc message
	 *
	 */

	static class MyIDocHandler implements JCoIDocHandler
	{
		/**
		 * Storing received message As Ensemble Message
		 */
		public void handleRequest(JCoServerContext serverCtx, IDocDocumentList idocList)
		{
			try {

				IDocXMLProcessor xmlProcessor = 
						JCoIDoc.getIDocFactory().getIDocXMLProcessor();
				/*
				fos = new FileOutputStream(iDocOutputDir + "\\" + serverCtx.getTID()+"_idoc.xml");
				osw = new OutputStreamWriter(fos, "UTF8");
				xmlProcessor.render(idocList, osw, 
						IDocXMLProcessor.RENDER_WITH_TABS_AND_CRLF);			
				osw.flush();
				 */

				// Creating new Request
				String requestOID = irisInstance.classMethodString("RG.ESB.Message.IDocStreamRequest", "AddRecord", serverCtx.getTID(), serverCtx.getSessionID(), serverCtx.getConnectionID());
				if (! requestOID.isEmpty()) {
					production.LogMessage("Created new Request Record for TID = " + serverCtx.getTID(), Production.Severity.TRACE);

				}
				else {
					production.LogMessage("Unable to create new Request Record for TID = " + serverCtx.getTID(), Production.Severity.ERROR);
					return;
				}

			}
			catch (Throwable e)
			{
				try {
					production.LogMessage("GeneralError" + e.getMessage(), Production.Severity.ERROR);
				}
				catch (Exception ex) {
					System.out.println("NoWay");
				}
			}
		}

	}
	static class MyIDocHandlerFactory implements JCoIDocHandlerFactory
	{
		private JCoIDocHandler handler = new MyIDocHandler();
		public JCoIDocHandler getIDocHandler(JCoIDocServerContext serverCtx)
		{
			return handler;
		}
	}

	static class MyThrowableListener implements JCoServerErrorListener, JCoServerExceptionListener
	{

		public void serverErrorOccurred(JCoServer server, String connectionId, JCoServerContextInfo ctx, Error error)
		{
			System.out.println(">>> Error occured on " + server.getProgramID() + " connection " + connectionId);
			error.printStackTrace();
		}
		public void serverExceptionOccurred(JCoServer server, String connectionId, JCoServerContextInfo ctx, Exception error)
		{
			System.out.println(">>> Error occured on " + server.getProgramID() + " connection " + connectionId);
			error.printStackTrace();
		}
	}

	private class MyTidHandler implements JCoServerTIDHandler
	{
		public boolean checkTID(JCoServerContext serverCtx, String tid)
		{
			System.out.println("checkTID called for TID="+tid);
			return true;
		}


		public void confirmTID(JCoServerContext serverCtx, String tid)
		{
			System.out.println("confirmTID called for TID="+tid);
		}


		public void commit(JCoServerContext serverCtx, String tid)
		{
			System.out.println("commit called for TID="+tid);
		}


		public void rollback(JCoServerContext serverCtx, String tid)
		{
			System.out.print("rollback called for TID="+tid);
		}
	}


}
