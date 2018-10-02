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

import java.io.PrintWriter;
import com.intersystems.gateway.bh.BusinessOperation;
import com.sap.conn.jco.*;
import com.sap.conn.idoc.jco.*;
import com.sap.conn.idoc.*;
import java.io.*;
import java.sql.DriverManager;
import com.intersystems.jdbc.IRISConnection;
import com.intersystems.jdbc.IRIS;

/**
 * 
 * Java Business Operation sends IDocs to the SAP destination.
 *
 */
public class SAPOperation implements BusinessOperation {

	// Settings available for this Operation. Can be extended and granulized, but for now decided to use .jCoDestination and .jCoServer parameter files. 
	// Prior to IRIS 2018.2 we've no possibilities to categorize Settings.
	// Prior to IRIS 2018.2 it's impossible to auto catch up instance parameters.
	public static final String SETTINGS = "JCoDestinationsDir,JCoName,LogFileName,IRISHost,IRISPort,IRISNamespace,IRISUsername,IRISPassword";

	// Instance Parameters (JavaGateway might be placed on separate instance)
	protected static int IRISPort = 0;
	protected static String IRISHost = "";
	protected static String IRISNamespace = "";
	protected static String IRISUsername = "";
	protected static String IRISPassword = "";

	// Log Writer
	private PrintWriter logFile;

	// Settings properties
	String destinationName = null;
	//String iDocSampleFile = null;
	String destDir = null;

	// These variables holds JCo objects
	IDocFactory iDocFactory;
	JCoDestination destination;
	IDocRepository iDocRepository;

	// Holding IRIS objects
	IRISConnection irisConn;
	IRIS irisInstance;

	// Some constants
	protected static String partSeparator = ":::::";

	/**
	 * Initialization (called when Production and/or business element starts
	 */
	public boolean OnInit(String[] args) throws Exception {

		/**
		 * Prior to IRIS version 2018.2 there is no way to access Production object, so we have to parse Settings and write logs to file.
		 */
		for (int i = 0; i < args.length-1; i++) {
			if (args[i] == null) continue;

			switch (args[i]) 
			{

			case "-LogFileName":
				// Full path to file in which we'll write debug and log information
				logFile = new PrintWriter(new FileOutputStream(args[++i], true), true);
				logFile.println("LogFile opened");
				break;

			case "-JCoDestinationsDir":
				// Path to search for destinations files
				destDir = args[++i];
				System.setProperty("jco.destinations.dir", destDir);
				break;

			case "-JCoName":
				// .jCoDestination file name (without extension)
				destinationName = args[++i];
				break;

				/*case "-IDocSampleFile":
				// Sample file with correct IDoc for testing
				iDocSampleFile = args[++i];
				break;
				 */
			case "-IRISHost":
				// IRIS Host
				IRISHost = args[++i];
				if (IRISHost.equals("")) IRISHost = "localhost";
				break;

			case "-IRISPort":
				// IRIS Master Port
				IRISPort = Integer.parseInt(args[++i]);
				break;

			case "-IRISNamespace":
				// IRIS Namespace
				IRISNamespace = args[++i];
				break;

			case "-IRISUsername":
				// IRIS UserName
				IRISUsername = args[++i];
				break;

			case "-IRISPassword":
				// IRIS Password for UserName
				IRISPassword = args[++i];
				break;

			default:
				// no action for now

			}

		}

		// Writing parsed parameters to LogFile
		logFile.println("SAP Settings: \n");
		//logFile.println("\tiDocSampleFile=" + iDocSampleFile);
		logFile.println("\tdestinationName=" + destinationName);
		logFile.println("\tjco.destinations.dir=" + destDir);
		logFile.println();
		logFile.println("IRIS Settings: \n");
		logFile.println("\tHost: " + IRISHost);
		logFile.println("\tPort: " + String.valueOf(IRISPort));
		logFile.println("\tNamespace: " + IRISNamespace);
		logFile.println("\tUsername: " + IRISUsername);
		logFile.println("\t Password: " + IRISPassword);
		logFile.println();


		// Connecting to SAP
		try {
			// Connecting to SAP Destination
			logFile.println("Connecting to SAP...");
			destination = JCoDestinationManager.getDestination(destinationName);
			logFile.println("destinationName = " + destination.getDestinationName());

			// Getting IDoc Repository
			iDocRepository = JCoIDoc.getIDocRepository(destination);
			logFile.println("iDocReposiroty.name = " + iDocRepository.getName());

			// Getting IDoc Factory
			iDocFactory = JCoIDoc.getIDocFactory();
			logFile.println("iDocFactory.version=" + iDocFactory.getVersion());

		}
		catch (Exception e)
		{
			// TDA - just log the exception for now, but we've to extend exception handling for Production in future
			e.printStackTrace();
			logFile.println(e.getMessage());
			logFile.close();
			return false;
		}

		// Connecting to IRIS
		try {
			logFile.println("(back-)Connecting to IRIS...");
			irisConn = (IRISConnection) DriverManager.getConnection
					(	"jdbc:IRIS://" + 
							IRISHost + ":" +
							IRISPort + "/" +
							IRISNamespace,
							IRISUsername,
							IRISPassword);

			// create IRIS Native object
			irisInstance = IRIS.createIRIS(irisConn);
			logFile.println("Connected to IRIS.");
		}
		catch (Exception e) {
			e.printStackTrace();
			logFile.println(e.getMessage());
			logFile.close();
			irisConn.close();
			irisInstance.close();
			return false;
		}

		return true;
	}

	/**
	 * Stopping. Called when Production and/or business element stops.
	 */
	@Override
	public boolean OnTearDown() throws Exception {
		try {
			logFile.println("Tearing down Operation...");
			irisConn.close();
			irisInstance.close();
		}
		catch (Exception e) {
			e.printStackTrace();
			logFile.println(e.getMessage());
		}
		logFile.println("Operation stopped.");
		logFile.close();
		return true;
	}

	/**
	 * Handle IRIS message and send IDoc to SAP.
	 */
	@Override
	public boolean OnMessage(String message) throws Exception {
		logFile.println("Received message: \n" + message);

		// Crutch for IDs
		// Splitting message by ':::::'
		String[] messageParts = message.split(partSeparator);
		String token 	= messageParts[0];
		String request 	= messageParts[1];
		String iDocXML 	= messageParts[2];

		logFile.println();
		logFile.println("\tToken: " + token);
		logFile.println("\tRequest: " + request);
		logFile.println("\tStream: " + iDocXML);
		logFile.println();


		// Sending IDoc to SAP.
		try {
			// Starting new SAP-side transaction and get it's unique ID. 
			String tid = destination.createTID();
			logFile.println("transactionID = " + tid);

			IDocXMLProcessor processor = iDocFactory.getIDocXMLProcessor();
			IDocDocumentList iDocList = processor.parse(iDocRepository, iDocXML);
			JCoIDoc.send(iDocList, IDocFactory.IDOC_VERSION_DEFAULT, destination, tid);
			destination.confirmTID(tid);

			// New linked record
			if (irisInstance.classMethodBoolean("RG.ESB.SAP.TIDQueue", "AddRecord", request, token, tid)) {
				logFile.println("Created new TIDQueue Record for TID = " + tid);
			}
			else {
				logFile.println("Unable to create new TIDQueue Record for TID = " + tid);
				return false;
			}
		}
		catch(Exception ex)
		{
			// TDA - just log the exception for now, but we've to extend exception handling for Production in future
			logFile.println(ex.getMessage());
			return false;
		}
		return true;
	}



}