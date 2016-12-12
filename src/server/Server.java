package server;
import java.io.*; 
import java.net.*; 
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

public class Server {

	 /*
	 *
	 */
	static int PACKAGESIZE = 512;
	public static void main(String[] args) throws IOException 
	{
    	if (args.length != 1) //Alle Argumente?
    	{										 
    		System.out.println("Benötigte Argumente: Port");    
    		return;   
      	}   
    	
        int port = Integer.parseInt(args[0]);

        DatagramSocket serverSocket;
        try
        {
        	serverSocket= new DatagramSocket(port); 
        }catch(java.net.BindException e)
        {
        	System.out.println("Port wird schon verwendet. Server schon gestartet?");
        	return;
        }
	    byte[] receiveStart = new byte[277]; 
	      
	      
        DatagramPacket receiveStartPackage =  new DatagramPacket(receiveStart, receiveStart.length); 
        System.out.print("Warte auf eingehende Verbindung....\n\n");
        serverSocket.receive(receiveStartPackage); 
        InetAddress IPAddress = receiveStartPackage.getAddress();
        int port_receive = receiveStartPackage.getPort();
	    while(true) 
	        { 
	    	  
	    	  int receivedBytes = 0;
	          ByteBuffer startpackageBuffer = ByteBuffer.allocate(receiveStart.length);
	          startpackageBuffer.put(receiveStart);
	           
	         
	          //SessionNr von 0-1
	          short sessionNr = startpackageBuffer.getShort(0);
	          
	          short packageNr = startpackageBuffer.get(2);
	          //Kennung von 3-7
	          byte[] KennungBytes = Arrays.copyOfRange(receiveStart, 3, 8);
	          String Kennung = new String(KennungBytes, StandardCharsets.US_ASCII);
	          //filelength von 8-16
	          long fileLength = startpackageBuffer.getLong(8);
	          //fileNameLength von 17-18
	          short fileNameLength = startpackageBuffer.getShort(16);
	          //filename 
	          byte[] filenameBytes = Arrays.copyOfRange(receiveStart, 18, 18 + fileNameLength);
	          String filename = new String(filenameBytes);
	          byte[] crcBytes = new byte[4];
	          crcBytes = Arrays.copyOfRange(receiveStart, 18+fileNameLength, 18+fileNameLength+4); 
	          
	          
	          //CRC-Code vom Startpaket, long da bei int eine minus zahl rauskommen kann.
	          long checksumStartReceived = CRCfromByteArray(crcBytes);
	          //Get CRC-Code From Package	          
	          //1. CRC Code abschneiden.
	          byte[] startarray = Arrays.copyOfRange(receiveStart, 0, 18+fileNameLength);
	          //2. CRC Code erstellen  
	          Checksum checksumStart = new CRC32();
	          checksumStart.update(startarray, 0, startarray.length);
	          long crcStartCalc= checksumStart.getValue();
	          //Checksum mit empfangener vergleichen
	          if (crcStartCalc != checksumStartReceived || packageNr != 0 || !Kennung.equals("Start"))
	          {
	        	  System.out.printf("Fehlerhaftes Startpaket...\n");
	        	  //Warte auf neues Paket
	        	  serverSocket.receive(receiveStartPackage); 
				  IPAddress = receiveStartPackage.getAddress();
				  port_receive = receiveStartPackage.getPort();
	          }
	          /*
	           * Else Anweisung läuft bis die Datei ganz gesendet wurde.
	           */     
	          else 
	          {
	        	  ByteBuffer ACKStart = getACK(sessionNr, packageNr);
	        	  //Sende ACK
	              DatagramPacket sendStartACK = new DatagramPacket(ACKStart.array(), ACKStart.array().length, IPAddress, port_receive);      
	              serverSocket.send(sendStartACK); 
	              System.out.printf("Starte Dateiübertragung....\n");
	          
	          //*******Print all the things****************
//	          System.out.printf("Session-Number = %d \n", sessionNr);
//	          System.out.printf("Package-Number = %d \n", packageNr);
//	          System.out.printf("Kennung = %s \n", Kennung);
//	          System.out.printf("Dateilänge = %d \n", fileLength);
//	          System.out.printf("DateiNamenlänge = %d \n", fileNameLength);
//	          System.out.printf("Filename = %s \n", filename);
//	          System.out.printf("CRC-Start-received = %d \n", checksumStartReceived);
//	          System.out.printf("CRC-Start-calc = %d \n\n\n", crcStartCalc);
	          
	          //*******FILE schon vorhanden?************************
	              File file = new File(filename);
	              while (file.exists())
	              {
	            	  String newFilename = file.getName() + "1";
	            	  file = new File(newFilename);	
	              }
	          
	              FileOutputStream fileOutputStream = new FileOutputStream(file);
	              FileChannel fileChnl = fileOutputStream.getChannel();

	          
	              while(true)
	              {	
	            	  short packageNrOld = packageNr; 
	            	  byte[] receiveData = new byte[2+1+PACKAGESIZE+8]; //SessionNr + PackageNr + Daten + CRC 
	            	  
	            	  DatagramPacket receiveDataPackage =  new DatagramPacket(receiveData, receiveData.length); 
	            	  serverSocket.receive(receiveDataPackage);
	            	  ByteBuffer dataPackageBuffer = ByteBuffer.allocate(receiveData.length);
	            	  dataPackageBuffer.put(receiveData);
		          
	            	  short sessionNrData = dataPackageBuffer.getShort(0);
	            	  packageNr = dataPackageBuffer.get(2);
	            	  byte[] KennungBytesData = Arrays.copyOfRange(receiveData, 3, 8);
		          
	            	  String KennungData = new String(KennungBytesData, StandardCharsets.US_ASCII);
	            	  

	            	  //Überprüfen ob Startpaket
	            	  if (sessionNrData != sessionNr && KennungData.equals("Start"))
	            	  {
	            		  port_receive = receiveDataPackage.getPort();
	            		  IPAddress = receiveDataPackage.getAddress();
	            		  receiveStart = Arrays.copyOfRange(receiveData, 0, 277); 
	            		  break;
	            	  }
	            	  //Überprüfen ob andere bzw Fehlerhafte sessionNr und kein Startpaket 
	            	  if (sessionNrData == sessionNr)
	            	  {
	            	  //Gleiches Paket nochmal bekommen bzw. falsches Paket bekommen -> Sende Altes Ack nochmals
	            	  if (packageNr == packageNrOld && sessionNrData == sessionNr)
	            	  {
	            		  ByteBuffer ACKData = getACK(sessionNrData, packageNr);
	            		  DatagramPacket sendDataACK = new DatagramPacket(ACKData.array(), ACKData.array().length, IPAddress, port_receive);      
			     
	            		  serverSocket.send(sendDataACK);     
	            	  }
	            	  /*********Wenn richtiges Datenpaket empfangen wird*******************/
	            	  else
	            	  {
	            		  receivedBytes += PACKAGESIZE;
	            		  byte[] data = new byte[PACKAGESIZE];
	            		  if (receivedBytes < fileLength)
	            		  {
	            			  data = Arrays.copyOfRange(receiveData, 3, 3 + PACKAGESIZE);
	            			  ByteBuffer fileDataBuffer = ByteBuffer.allocate(data.length);
	            			  fileDataBuffer.put(data);
	            			  fileDataBuffer.flip();
	            			  fileChnl.write(fileDataBuffer);
	            			  fileDataBuffer.clear();
	            			  //Sende ACK
	            			  ByteBuffer ACKData = getACK(sessionNrData, packageNr);
	            			  DatagramPacket sendDataACK = new DatagramPacket(ACKData.array(), ACKData.array().length, IPAddress, port_receive);      
	            			  serverSocket.send(sendDataACK);
	            			  dataPackageBuffer.clear();
	            		  }
	            		  /********Falls letztes Datenpaket empfangen wird**************/
	            		  else 
	            		  {
	            			  int lastDataSize = (int) (PACKAGESIZE - (receivedBytes - fileLength));
	            			  data = Arrays.copyOfRange(receiveData, 3, 3 + lastDataSize);
	            			  ByteBuffer fileDataBuffer = ByteBuffer.allocate(data.length);
	            			  fileDataBuffer.put(data);
	            			  fileDataBuffer.flip();
	            			  fileChnl.write(fileDataBuffer);
	            			  fileDataBuffer.clear();
	            			  fileChnl.close();
	            			  fileOutputStream.close();
	            			  //CRC Code der Datei aus Buffer holen:
	            			  byte[] crcBytesFile = new byte[4];
	            			  crcBytesFile = Arrays.copyOfRange(receiveData, 3 + lastDataSize, 3 + lastDataSize + 4);
	            			  long checksumFileReceived = CRCfromByteArray(crcBytesFile);
	            			  long checksumFile = FileCRC(file.getName());
	            			  if (checksumFile != checksumFileReceived )
	            			  {
	            				  System.out.print("Fehler im CRC Code der Datei..\n");
	            				  sessionNr = 0; //verhindert das für die Wiederholungspakete ein Ack gesendet wird
	            				  
	            			  }
	            			  else
	            			  {
	            				  //Sende ACK
	            				  ByteBuffer ACKData = getACK(sessionNrData, packageNr);
	            				  DatagramPacket sendDataACK = new DatagramPacket(ACKData.array(), ACKData.array().length, IPAddress, port_receive);      
	            				  serverSocket.send(sendDataACK);
	            				  dataPackageBuffer.clear();
	            				  System.out.print("Datei wurder erfolgreich empfangen\n\n" +
	            				  		"Warte auf eingehende Verbindung....\n\n");
	            			  }
	            		  }
	            	  }
	            	  }
	          		}
	          	}
	        }
	}
	
	
    public static ByteBuffer getACK(short sessionNr, short packageNr)
    {
    	byte[] session = ByteBuffer.allocate(2).putShort(sessionNr).array();
  	  	byte[] packageNrByte = new byte[1]; 
  	  	packageNrByte[0] = (byte)packageNr;
  	  	ByteBuffer AckStart = ByteBuffer.allocate(session.length + packageNrByte.length);
  	  	AckStart.put(session); AckStart.put(packageNrByte);
  	  	return AckStart;
    }

    public static long FileCRC(String filenamestring) throws IOException
    {
    	InputStream fileInput = new FileInputStream(filenamestring); 
    	int connectfile;
    	CRC32 checksum = new CRC32();
    	
    	//Berechne den CRC Code der Datei
    	while ((connectfile = fileInput.read()) != -1 ) 
    	{
    		checksum.update(connectfile);
    	}
    	fileInput.close();
    	return checksum.getValue();	
    }
    
    public static long bytesToLongCRC(byte[] crcBytes)
    {
        ByteBuffer buffer = ByteBuffer.allocate(Long.SIZE);
        buffer.put(crcBytes, 0, Long.SIZE);
        buffer.flip();
        return buffer.getLong();
    }
    
    public static long CRCfromByteArray(byte[] crcByte) {
        long checksum = 0;
        int exponent = 0;
        for(int i=crcByte.length-1;i>=0;i--) {
          for(int j=0;j<8;j++) {
            if((((byte)crcByte[i]) & (0x01 << j))>0) {
              checksum+=Math.pow(2, exponent);
            }
            exponent++;
          }
        }
        return checksum;
      }
    
}
