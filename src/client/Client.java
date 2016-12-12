package client;

import java.io.*; 
import java.net.*; 
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import java.nio.ByteBuffer;
import java.util.*;
import java.lang.*;

/*
 * TODO: bash Script erstellen für: client - aufruf des Clients mit Parametern
 * 									server - wie client
 * 									make.sh
 * 		 Datenrate sekündlich ausgeben (evtl mit Leiste)
 * 		 Variabler TIMEOUT
 */
	class Client {
		private static int i = 0;
		static int PACKAGESIZE = 512;
		static int TIMEOUT = 500;
		static long totaltime = 0;
	    public static void main(String args[]) throws IOException 
	    
	    { 
	    	
	    	
	    	
	    	if (args.length != 3) 
	    	{										 
	    		System.out.println("Benötigte Argumente: Host, Port, Dateiname/pfad");    
	    		return;   
	      	} 
	      	  
	    	/******Variablen**********/
	    	int sendedBytes = 0;
	    	byte[] ACK = new byte[3];
	    	String host = args[0];   
	        int port = Integer.parseInt(args[1]);
	        String filepathString = args[2];
	        File file = new File(filepathString);
	        
	        if (!file.exists())
	        {
	        	System.out.printf("Datei \""+ file.getName() + "\" konnte nicht gefunden werden.\n");
	        	return;
	        }
	        
	        long filesize = file.length();
	        //1 % der Filegröße, wird für Fortschrittsanzeige benötigt
	        double percentCount =  (double)filesize/100;
	        //Erstelle zufällige Session-Nr
	        short sessionNr = randomSession();
	        
	        //Stelle Startpaket zusammen
	    	byte[] startPackageByte = getStartPackage(file, sessionNr);
	    	
	    	//CRC Code für ganze Datei erstellen
	    	long checksumFile = FileCRC(filepathString);
	    	
	    	/**************Senden des Starpakets********************/
	    	System.out.print("Starte die Übertragung zum Server.....\n");
	    	printDataRate(0.0,0.0);
	    	DatagramSocket socket = new DatagramSocket();   
	        socket.setSoTimeout(TIMEOUT);
	        
	        InetAddress serverAddress = InetAddress.getByName(host);
	        
	        DatagramPacket startPackage = 
	                new DatagramPacket(startPackageByte, startPackageByte.length, serverAddress, port);
	        
	        long timeStart = System.nanoTime();	//starte Zeitabnahme
	        socket.send(startPackage);
	        DatagramPacket receivePacket =  new DatagramPacket(ACK, ACK.length); 
	        
	        /****Warten auf das Ack des Servers****/
	        try
	        {	
		        socket.receive(receivePacket);
		        if (TIMEOUT > 250) //Verhindert das der Timeout zu klein wird
		        {
		        TIMEOUT *= 0.95;
		        }
		        socket.setSoTimeout(TIMEOUT);
		        long timeEnd = System.nanoTime();
		        long time = timeEnd - timeStart;
		        double timeSeconds = (double) time / 1000000000.0;
		            printDataRate(0.0,(startPackageByte.length*8/1024)/timeSeconds);
		          
		        
	        }
	        catch(SocketTimeoutException e) 	//Bei Timeout das Paket nochmals senden 
	        {
	        	TIMEOUT *= 1.10;
	        	socket.setSoTimeout(TIMEOUT);
	        	for (int x = 0; x < 10; x++)	//-> bis zu 10 mal
	        	{
	        		try
	        		{
	        			timeStart = System.nanoTime();
	        			socket.send(startPackage);
	        			socket.receive(receivePacket);
	        			long timeEnd = System.nanoTime();
	    		        long time = timeEnd - timeStart;
	    		        double timeSeconds = (double) time / 1000000000.0;
	    		        printDataRate(0.0,(startPackageByte.length*8/1024)/timeSeconds);
	        			
	        		}
	        		catch(SocketTimeoutException timeout)
	        		{
	        			TIMEOUT *= 1.10;
	        			socket.setSoTimeout(TIMEOUT);
	        			if (x == 9) 
	        			{
	        				System.out.print("\nFehler beim Übertragen des Startpakets. Die Übertragung wird abgebrochen.\n");
	        				socket.close(); 
	        				return;
	        			}      			
	        		}
	        		//Sobald ein korrektes Ack empfangen wurde unterbricht die Schleife
	        		if (compareACK(ACK, sessionNr)==1)
	        		{
	        			break;
	        		}
	        	} 
	        }
	        
	        //Falls kein Timeout aber falsches ACK Paket erneut senden
	        if (compareACK(ACK, sessionNr)==0)
        	{
	        	for (int x = 0; x < 10; x++)
		        {
	        	 try{
	        		timeStart = System.nanoTime();
		        	socket.send(startPackage);
		        	socket.receive(receivePacket);
		        	long timeEnd = System.nanoTime();
			        long time = timeEnd - timeStart;
			        double timeSeconds = (double) time / 1000000000.0;
			            printDataRate(0.0,(startPackageByte.length*8/1024)/timeSeconds);
		        	}
	        	 	catch(SocketTimeoutException timeout)
		        	{
	        	 		TIMEOUT *= 1.10;
	        	 		socket.setSoTimeout(TIMEOUT);
	        	 		if (x == 9) 
	        	 		{
	        	 			System.out.printf("\nFehler beim Übertragen des Startpakets. Die Übertragung wird abgebrochen.\n");
	        	 			socket.close(); 
	        	 			return;
	        	 		}      			
		            }
	        	 	//Sobald ein korrektes Ack empfangen wurde unterbricht die Schleife
		        	if (compareACK(ACK, sessionNr)==1)
		        	{
		        		break;
		        	}
		        	}
        		}
	        
	        /*
	         * Ab Hier wurde das Startpaket erfolgreich gesendet, sowie der erste Ack empfangen
	         */
	        FileInputStream fileStream = new FileInputStream(file);
	        FileChannel fileChnl = fileStream.getChannel();
	        ByteBuffer filebuffer = ByteBuffer.allocate(PACKAGESIZE); 
	        while(fileChnl.read(filebuffer) > 0)
	        {
	        	long current;
	        	DatagramPacket dataPackage;
	            filebuffer.flip();
	            sendedBytes += PACKAGESIZE;
	            if (sendedBytes < file.length())
	            {
	            	
	            	byte[]dataPackageBytes = getDataPackage(filebuffer.array(), sessionNr);
	            	dataPackage = 
			                new DatagramPacket(dataPackageBytes, dataPackageBytes.length, serverAddress, port);
	            	current = dataPackageBytes.length;
	            	timeStart = System.nanoTime();
		            socket.send(dataPackage);
		            
		            filebuffer.clear();
	            }
	            /******Falls letztes Paket gesendent wird*****/
	            else
	            {
	            	TIMEOUT = 2000;
	            	socket.setSoTimeout(TIMEOUT); //Timeout wird erhöht da berechnen von CRC Code im Server länger dauern kann (bei großen Dateien)
	            	int restBytes = (int)(PACKAGESIZE -(sendedBytes-filesize));
	            	byte[]dataPackageBytes = getDataEndPackage(filebuffer.array(), sessionNr, checksumFile, restBytes);
	            	dataPackage = 
			                new DatagramPacket(dataPackageBytes, dataPackageBytes.length, serverAddress, port);
	            	current = dataPackageBytes.length;
	            	timeStart = System.nanoTime();
	            	socket.send(dataPackage);
		            filebuffer.clear();
	            }
	            //Warte auf ACK
	            try{
		           socket.receive(receivePacket);
		           if (TIMEOUT > 250) 
			        {
		        	   TIMEOUT *= 0.95;
			        }
		           socket.setSoTimeout(TIMEOUT);
		           long timeEnd = System.nanoTime();
			       long time = timeEnd - timeStart;
			       double timeSeconds = (double) time / 1000000000.0;
			       totaltime += time;
	            	printDataRate((sendedBytes / percentCount)*0.01, (current*8/1024)/timeSeconds );
 
		           }catch(SocketTimeoutException e) 	//Bei Timeout das Paket nochmals senden -> bis zu 10 mal
			       {
		        	   TIMEOUT *= 1.10;
		        	   socket.setSoTimeout(TIMEOUT);
		        	   for (int x = 0; x < 10; x++)
		        	   {
		        		   try
		        		   {
		        			   timeStart = System.nanoTime();
		        			   socket.send(dataPackage);
		        			   socket.receive(receivePacket); 
		        			   long timeEnd = System.nanoTime();
		    			       long time = timeEnd - timeStart;
		    			       double timeSeconds = (double) time / 1000000000.0;
		    			       printDataRate((sendedBytes / percentCount)*0.01, (current*8/1024)/timeSeconds );
		        		   }
		        		   catch(SocketTimeoutException timeout)
		        		   {
		        			   TIMEOUT *= 1.10;
		        			   socket.setSoTimeout(TIMEOUT);
		        			   if (x == 9) 
			        		{
			        			System.out.printf("\nZu viele verlorene Pakete. Übertragung wird abgebrochen.\n");
			        			socket.close(); 
			        			return;
			        		}      			
		        		   }
			        		if (compareACK(ACK, sessionNr)==1)
			        		{
			        			break;
			        		}
			        	}
			        }
		            
			        //Falls kein Timeout aber falsches ACK --> Paket nochmals senden und auf ACK Warten
			        if (compareACK(ACK, sessionNr)==0)
		        	{
			        	for (int x = 0; x < 10; x++)
			        	{
			        	  try{
			        		  timeStart = System.nanoTime();
			        		  socket.send(dataPackage);
			        		  socket.receive(receivePacket);
			        		  long timeEnd = System.nanoTime();
			        		  long time = timeEnd - timeStart;
			        		  double timeSeconds = (double) time / 1000000000.0;
			        		  printDataRate((sendedBytes / percentCount)*0.01, (current*8/1024)/timeSeconds );
					        
			        		}catch(SocketTimeoutException timeout)
			        		{
			        			TIMEOUT *= 1.10;
			        			socket.setSoTimeout(TIMEOUT);
			        			if (x == 9) 
			        			{
			        				System.out.printf("\nZu viele verlorene Pakete. Übertragung wird abgebrochen.\n");
			        				socket.close(); 
			        				return;
			        			}      			
			        		}
			        		if (compareACK(ACK, sessionNr)==1)
			        		{
			        			break;
			        		}
			        	}
		        	}
	        }
	        
	        fileChnl.close();
	        fileStream.close();
	        
	        socket.close();
	        System.out.printf("\n\nDatei wurde erfolgreich an den Server Übertragen.\n");
	        double averageTime = (double)totaltime / 1000000000.0;
	        System.out.printf("Dateigröße: %d kbit\n" +
	        				  "Durschnittsdatenrate: %.2f kbit/s\n" +
	        				  "Gesamtdauer der Übertragung: %.2f Sekunden\n",filesize*8/1024, (filesize*8/1024)/averageTime, (double)totaltime / 1000000000.0 );
	        
	        return;
	        
	      } 
	        
	    /*Funktion zum Convertieren von long zu byte*/
	    public static byte[] longtoByteConv(long longnumber)
	    {
	    	java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(Long.SIZE);
	    	buffer.putLong(longnumber);
	    	return buffer.array();
	    }
	    
	    
	    
	    /*
	     * Funktion zum berechen des CRC Codes der gesamten Übertragenen Datei 
	     * -> wird im letzen Paket mitgesendent.
	     */
	    public static long FileCRC(String filenamestring) throws IOException
	    {
	    	Path file = Paths.get(filenamestring);
	    	byte[] data = Files.readAllBytes(file);
	    	CRC32 checksum = new CRC32();
	    	checksum.update(data, 0, data.length);
	    	long crcNumber = checksum.getValue();
	    	return crcNumber;	
	    }
	    
	    /*
	     * Funktion gibt eine zufällig SessionNr zurück welche für die ganze Übertragung gleich bleibg
	     */
	    public static short randomSession()
	    {
	    	Random randSession = new Random();
	    	
	    	short randomNumber = (short)randSession.nextInt(Short.MAX_VALUE + 1);
	    	return randomNumber;
	    }
	    
	    
	    /*
	     * Funktion gibt das Start-Paket als byte-Array zurück
	     */
	    public static byte[] getStartPackage(File file,short sessionNr) throws UnsupportedEncodingException
	    {
	    	String filenameString = file.getName(); 
	        short filenamelengthShort = (short)filenameString.length();
	        
	        //Sessionnummer (16 Bit = 2 Byte)
	    	byte[] session = ByteBuffer.allocate(2).putShort(sessionNr).array();
	    	
	    	//Paketnummer (8 Bit = 1 Byte) 
	    	byte[] packageNr = getnewPackageNr();
	    	
	    	//Kennung (40 Bit = 5 Byte) im ASCII-Standard
	    	String startString = "Start";
	    	byte[] kennung = startString.getBytes(StandardCharsets.US_ASCII); 
	    	
	    	//Dateilänge (64 Bit = 8 Byte)
	    	byte[] filelength = ByteBuffer.allocate(8).putLong(file.length()).array();
	    	
	    	//Länge des Dateinamens (16 Bit = 2 Byte)
	    	byte[] filenamelength = ByteBuffer.allocate(2).putShort(filenamelengthShort).array();
	    	
	    	//Dateiname als UTF8 String (0-255 Byte) -> länge kann von filenamelength abhängig gemacht werden
	    	byte[] filename = new byte[filenamelengthShort];
	    	filename = filenameString.getBytes("UTF-8");
	    	
	    	/*Startpaket ohne CRC-Code zusammenfügen*/
	    	//Speicher reservieren
	    	ByteBuffer startPackage_withoutCRC = ByteBuffer.allocate(session.length + packageNr.length + 
	    			kennung.length + filelength.length + filenamelength.length + filename.length );
	    	
	    	//Daten in den Bytebuffer schreiben
	    	startPackage_withoutCRC.put(session); startPackage_withoutCRC.put(packageNr); startPackage_withoutCRC.put(kennung);
	    	startPackage_withoutCRC.put(filelength); startPackage_withoutCRC.put(filenamelength); startPackage_withoutCRC.put(filename);
	    	
	    	/*CRC Code für das Startpaket erstellen*/ 											// Hier muss nachgebessert werden, wegen long wert unc CRC32
	    	Checksum checksumStart = new CRC32();
	    	checksumStart.update(startPackage_withoutCRC.array(), 0, startPackage_withoutCRC.array().length);
	    	long crcStartNumber= checksumStart.getValue();
//	    	ByteBuffer crcBuff = ByteBuffer.allocate(8);
//	    	crcBuff.putLong(crcStartNumber);
//	    	byte[] crcStart = crcBuff.array();
	    	byte[] crcStart = longtoByteCRC(crcStartNumber);
	    	/*Startpaket mit CRC-Code zusammenfügen*/
	    	ByteBuffer startPackageBuff = ByteBuffer.allocate(startPackage_withoutCRC.array().length + crcStart.length);
	    	//Add CRC Code 
	    	startPackageBuff.put(startPackage_withoutCRC.array());startPackageBuff.put(crcStart);
	    	byte[] startPackage = startPackageBuff.array();

	    	return startPackage;
	    }
	    
	    /*
	     * Funktion gibt ein Datenpaket als byte-Array zurück (ohne CRC-Code)
	     */
	    public static byte[] getDataPackage(byte[] fileBytes,short sessionNr) 
	    {
	    	byte[] session = ByteBuffer.allocate(2).putShort(sessionNr).array();
	    	byte[] packageNr = getnewPackageNr();

	    	ByteBuffer dataPackage = ByteBuffer.allocate(session.length + packageNr.length + fileBytes.length);
	    	
	    	/*Daten in den Bytebuffer schreiben*/
	    	dataPackage.put(session); dataPackage.put(packageNr);dataPackage.put(fileBytes);
	    	return dataPackage.array();
	    }
	    
	    public static byte[] getDataEndPackage(byte[] fileBytes,short sessionNr, long checksumFile, int rest) 
	    {
	    	byte[] session = ByteBuffer.allocate(2).putShort(sessionNr).array();
	    	byte[] packageNr = getnewPackageNr();
	    	byte[] fileBytesRest = Arrays.copyOfRange(fileBytes, 0, rest);
//	    	ByteBuffer crcBuff = ByteBuffer.allocate(8);
//	    	crcBuff.putLong(checksumFile);
	    	byte[] crc = longtoByteCRC(checksumFile);
	    	ByteBuffer dataPackage = ByteBuffer.allocate(session.length + packageNr.length + fileBytesRest.length + crc.length);
	    	
	    	/*Daten in den Bytebuffer schreiben*/
	    	dataPackage.put(session); dataPackage.put(packageNr);dataPackage.put(fileBytesRest); dataPackage.put(crc);
	    	return dataPackage.array();
	    }
	    /*
	     * Funktion gibt die nächste PacketNr zurück
	     */
	    public static byte[] getnewPackageNr()
	    {
	    	byte[] packageNr = new byte[1]; 
	    	packageNr[0] = (byte)(i%2);
	    	Client.i++;
	    	return packageNr;	
	    }
	    
	    /*
	     * Funktion zum prüfen der empfangen ACKs vom Server
	     */
	    public static short compareACK(byte[]ACK, short sessionNr)
	    {
	    	short packageNr = (short) ((i-1)%2); //Berechnet die Aktuelle Packet Nr 
	    	
	    	ByteBuffer buffer = ByteBuffer.allocate(ACK.length);
	        buffer.put(ACK);
	        
	        //Hole Daten aus empfangenen ACK
	        short sessionNrAck = buffer.getShort(0);
	        short packageNrAck = buffer.get(2);
	        
	        //Prüfe ob SessionNR und PackageNr richtig 
	        //wenn nicht -> return 0
	        if (sessionNrAck != sessionNr || packageNrAck != packageNr)
	        {
	        	return 0;
	        }
	        //ansonsten -> return 1
	    	return 1;	
	    }
	    
	    public static byte[] longtoByteCRC(long checksum)
	    {
	        byte[] checksumBytes=new byte[4];
	        for(int j=4-1;j>=0;j--) {
	          for(int i=0;i<8;i++) {
	            if((checksum%2)==1) {
	              checksumBytes[j] |=(1 << i);
	            } else {
	              checksumBytes[j] |=(0 << i);
	            }
	            checksum=checksum/2;
	          }
	        }
	        return checksumBytes;
	      }
	    
	    public static void printDataRate(double progressPercentage, double datarate) {
	        final int width = 50; 

	        System.out.print("\r[");
	        int i = 0;
	        for (; i <= (int)(progressPercentage*width); i++) {
	          System.out.print(".");
	        }
	        for (; i < width; i++) {
	          System.out.print(" ");
	        }
	        System.out.printf("]   %7.2f kbit/s", datarate);
	      }
	} 	  
	


