import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Calendar;
import java.util.Properties;
import java.util.TimeZone;
import java.util.concurrent.Semaphore;

public class Processor {
	InputStream m_oRead;
	OutputStream m_oWrite;
	Idle m_oIdle;
	byte[] m_aBuffer;
	byte[] m_aSent;
	Semaphore m_oSemaphore = new Semaphore(1, true);
	Properties m_oProps ;

	public Processor(InputStream poRead, OutputStream poWrite, Properties poProps) {
		m_oRead = poRead;
		m_oWrite = poWrite;
		m_aBuffer = new byte[65536];
		/*
		 * Thread for Idle Communication
		 */
		m_oProps = poProps;
		m_oIdle = new Idle(this);
		m_oIdle.start();
	}
	
	private int _read(int pnRetry, int pnMaxRetry, byte[] paBuffer, int pnMinBytes) {
		int lnBytesReaded=0;
		try {
			lnBytesReaded = _read(paBuffer, pnMinBytes);
		} catch (IOException e) {
			if(pnRetry<pnMaxRetry)
				return _read(pnRetry+1, pnMaxRetry, paBuffer, pnMinBytes);
			else {
				System.out.println("Socket Error " + e.getMessage());
				return -1;
			}
		}
    	return lnBytesReaded;
	}
    
    private int _read(
    		byte[] paBuffer, int pnMinBytes
    		) throws IOException {
    	
    	int lnBytes = m_oRead.read(paBuffer, 0, ( pnMinBytes > 0 ? pnMinBytes : paBuffer.length) );
    	Logfile.Data("RxD", paBuffer, lnBytes);
		return lnBytes;
    }
    
    private boolean readack() throws IOException {
    	byte[] laBuffer = new byte[1];
    	if(_read(0, 32, laBuffer, 1)>0) {
    		switch(laBuffer[0]) {
    		case 1:
    			return true;
    		case -4:
    			Logfile.Write("Disk is Busy (Record/Replay in Progress)");
    			/*
    			 * Busy Loop
    			 */
    			try {
					Thread.sleep(1000);
					rewrite();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}    			
    			return readack();
    		case -7:
    			Logfile.Write("Disk is staring up...");
    			return readack();
    		}
    	}
		return false;
    }

    private DataInputStream readdata() throws IOException {
    	byte[] laBuffer = new byte[65536];
    	int lnBytes = _read(0, 8, laBuffer, 0);
    	if(lnBytes>0) {
    		byte[] laData = new byte[lnBytes];
    		System.arraycopy(laBuffer, 0, laData, 0, lnBytes);
    		return new DataInputStream(new ByteArrayInputStream(laData));
    	}
    	return null;
    }
    
	public void write(byte[] paData) {
    	try {
    		Logfile.Data("TxD", paData, paData.length);
    		m_aSent = paData;
   			m_oWrite.write(paData);
		} catch (IOException e) {
			System.out.println("Write Failed");
		} 
	}
	
	public void rewrite() {
    	try {
    		Logfile.Data("TxD", m_aSent, m_aSent.length);
   			m_oWrite.write(m_aSent);
		} catch (IOException e) {
			System.out.println("Write Failed");
		}		
	}
	
	public void write(byte pByte) {
		byte[] laBytes = new byte[1];
		laBytes[0] = pByte;
		write(laBytes);
	}
	
	public void write(String pcValue) {
		byte[] laBytes = pcValue.getBytes();
		write(laBytes);
	}

    public String GetHex(byte[] paData, int pnBytes) {
    	String lcResponse = "";
    	for(int i=0; i<pnBytes; i++) {
    		lcResponse = lcResponse + Integer.toHexString(paData[i] & 0xff) + " ";
    	}
    	return lcResponse;
    }
	
    public String GetHex(byte[] paData) {
    	return GetHex(paData, paData.length);
    }

	public String GetReceiverInfo() {
		Lock();
		String lcName = "Unknown";
		write(Header.PT_GETSYSINFO);
		try {
			DataInputStream loRecInfo = readdata();
			byte[] laFlags = new byte[4];
			loRecInfo.read(laFlags);
			byte lnLangLen = loRecInfo.readByte();
			byte[] laLang = new byte[lnLangLen];
			loRecInfo.read(laLang);
			byte lnNameLen = loRecInfo.readByte();
			byte[] laName = new byte[lnNameLen];
			loRecInfo.read(laName);
			ack();
			lcName = new String(laName);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			Unlock();
		}		
		return lcName;
	}
	
	private boolean ping() throws IOException {
		ack();
		return readack();
	}
	
	private boolean ack() {
		byte[] laBuffer = new byte[] { Header.PT_ACK };
		write(laBuffer);
		return true;
	}
	
	public DvrDirectory GetRoot() {
		Lock();
		short lnAnz;
		boolean lbBusy = true;
		try {
			DvrDirectory loReturn = new DvrDirectory();
			DataInputStream loData = null;
			/*
			 * Write Device Root Request to the Device
			 * Command 0x03 0x00 0x00
			 */
			byte[] laGetRoot = new byte[3];
			laGetRoot[0]=Header.PT_GETDIR;
			write(laGetRoot);
			readack(); // Read Response
			
			loData = readdata();
			lnAnz = loData.readShort();
			while(lnAnz>0) {
				/*
				 * Read Root Directorys from the Receiver
				 */
				byte lbType = loData.readByte();
				lbType = loData.readByte();
				byte lnLen = loData.readByte();
				byte[] lcDir = new byte[lnLen];
				loData.read(lcDir);
				//System.out.println(lcDir);
				loReturn.m_oDirectorys.add(new DvrDirectory(new String(lcDir)));
				lnAnz--;
			}
			//write(Header.PT_ACK);
			//loData = read();
			
			Unlock();
			return loReturn;			
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}	
	
	public DvrDirectory GetDir(String pcDir) throws IOException {
		Lock();
		DvrDirectory loDir = new DvrDirectory();
		Calendar loCalendar = Calendar.getInstance(TimeZone.getDefault());
		DataInputStream loData = null;
		byte[] laGetDir = new byte[3];
		/*
		 * 0x03
		 */
		laGetDir[0]=Header.PT_GETDIR;
		/*
		 * 0x00, 0x01 (0x01 = Da kommt noch ein Verzeichnis Name)
		 */
		laGetDir[1]=0;
		laGetDir[2]=1;
		write(laGetDir); //Send
		readack();
		
		write(pcDir); //Directory setzen hier weiß ich nicht obs in zukunft noch probleme mit dem Zeichensatz gibt		
		loData = readdata();

		ping();
		
		try {
			loData = readdata();
			short lnAnzElements = loData.readShort();
			while(lnAnzElements>0) {
				/*
				 * Die Technisat Zeitstempel sind irgendwie die anzahl der Sekunden
				 * ab dem 1.1.2000 - 1 Monat oder so. Ganz komisch. 
				 */
				loCalendar.set(2000, 01, 01, 00, 00, 00);
				loCalendar.add(Calendar.MONTH, -1);
				byte lbType = loData.readByte();
				short lnIndex = loData.readShort();
				byte lnLen = loData.readByte();
				byte lbUnknown = loData.readByte();
				byte[] laFileName = new byte[lnLen-1];
				loData.read(laFileName);
				String lcFileName = new String(laFileName,"CP1252"); //Dateinamen sind in CP1252 codiert

				//System.out.println(lcFileName);
				long lnSize = loData.readLong();
				byte[] lbTimeStamp = new byte[4];
				int lnTimeStamp = loData.readInt();
				//loData.read(lbTimeStamp);
				//System.out.println(GetHex(lbTimeStamp));
				
				/*
				 *TODO: Zeitzone prüfen 
				 */
				loCalendar.add(Calendar.SECOND, lnTimeStamp);
				DvrFile loFile = new DvrFile(lcFileName, lnSize, lnIndex, lbType, loCalendar.getTime());
				//System.out.println(loFile);
				loDir.m_oFiles.add(loFile);				
				lnAnzElements--;
				if(loData.available() == 0 && lnAnzElements>0) {
					loData = readdata();
				}
			}
			Unlock();
			return loDir;
		} catch (IOException e) {
			e.printStackTrace();
		}
		Unlock();
		return null;
	}
	
	/*
	 * Download a File from the Reciever to the
	 * Destination File specified in pcDstFile
	 */
	public int m_nActivePostCopyThreads = 0;
	
	public boolean Download(DvrFile poFile, String pcDstFile) {
		/*
		 * Parameter Checks
		 */
		if(pcDstFile.endsWith("/")) {
			pcDstFile = pcDstFile + poFile.getUniqueFileName();
		}
		String lcPostCopyAction = m_oProps.getProperty("POSTCOPYSCRIPT");
		int lnPostCopyThreads = Integer.parseInt(m_oProps.getProperty("POSTCOPYTHREADCOUNT"));
		boolean lbStartDownload = false;
		boolean lbPostCopyAction = false;
		long lnPrintInfo = 1000;
		
		if(lcPostCopyAction.equals("")) {
			Lock();
		} else {
			lbPostCopyAction=true;
			do {
				Lock();
				if(m_nActivePostCopyThreads<lnPostCopyThreads) {
					m_nActivePostCopyThreads++;
					lbStartDownload=true;
				} else {
					Unlock();
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			} while(!lbStartDownload);
		}
		
		byte[] laBuffer = new byte[65536];
		long lnPerfTime = System.currentTimeMillis();
		long lnPerfBytes = 0;
		/*
		 * Socket Streams
		 */
		ByteArrayOutputStream loSocketWriteLow = new ByteArrayOutputStream();
		DataOutputStream loSocketWrite = new DataOutputStream(loSocketWriteLow);		
		DataInputStream loSocketRead = null;
		
		/*
		 * File Streams
		 */
		File loTsFile = new File(pcDstFile);
		if(loTsFile.exists()) {
			if(m_oProps.getProperty("SAFEITY").equals("1")) {
				Logfile.Write("Error File "+pcDstFile+" already exists!");
				Unlock();
				return false;
			}
		}
		BufferedOutputStream loTs = null;
		BufferedOutputStream loFile = null;
		BufferedOutputStream loNextFile = null;
		
		int lnReadSize = 0;
		int lnChunkSize = 0;
		boolean lbRead = true;		
		long lnBytesReaded = 0;
		
		try {
			loTsFile.createNewFile();
		} catch (IOException e1) {
			e1.printStackTrace();
			Unlock();
			return false;
		}
		try {
			loTs = new BufferedOutputStream(new FileOutputStream(loTsFile));
		} catch (FileNotFoundException e1) {
			Unlock();
			e1.printStackTrace();
			return false;
		}
		
		try {
			Logfile.Write("Copy File " + poFile.getFileName() + " to "+pcDstFile);
			
			loSocketWrite.writeByte(Header.PT_GETFILE); //Download Command;
			loSocketWrite.writeShort(poFile.getIndex()); //File Index
			
			//TODO: Implement Download Resume
			//------
			loSocketWrite.writeLong(0); //Start Position (maybe!!)
			write(loSocketWriteLow.toByteArray()); // Send Message to DVR
			loSocketRead = readdata();
			write(Header.PT_ACK);			
			/*
			 * Read First Chunk
			 */
			loSocketRead = readdata();
			while(lbRead) {				
				if(loSocketRead.available()==0) {					
					loSocketRead = readdata();					
				}				
				if(lnChunkSize==0) {
					/*
					 * ChunkSize=0 dann muss jetzt ein Chunk Header kommen.
					 */
					byte[] laChunkInfo = new byte[8];
					byte[] laTemp = new byte[8]; 
					loSocketRead.read(laChunkInfo);

					DataInputStream loChunkReader = new DataInputStream(new ByteArrayInputStream(laChunkInfo));
					byte lbChunkType = loChunkReader.readByte(); 
					switch(lbChunkType) { 
					case 0:
						lnChunkSize = loChunkReader.readInt();
						loChunkReader.read(laTemp,0,3); //Was das ist hab ich noch nicht raus gefunden
						loFile = loTs;
						
						/*
						 * Performance Info berechnen / Ausgeben
						 * Das was jetzt kommt ist alles nur für die Ausgabe der Statusinformationen.
						 * Für die eigentliche Datenübertragung unwichtig.
						 */
						if(System.currentTimeMillis()-lnPerfTime>lnPrintInfo) {
							long lnFileSize = poFile.getFileSize()/1000;
							long lnFileSizeDl = lnBytesReaded/1000;
							double ln100 = 100;
							double lnFileSizeF = lnFileSize;
							double lnFileSizeDlF = lnFileSizeDl;
							double lnPercentDone = (ln100/lnFileSizeF)*lnFileSizeDlF;
							
							double lnKbs = lnPerfBytes/(lnPrintInfo/1000);
							Logfile.Write("["+String.format("%6.2f",lnPercentDone)+"%]"+String.format("%9.2f", lnKbs/1024) + "Kb/s, "+poFile.getFileName()); // + "("+lnFileSizeDlF+"/"+lnFileSizeF+")"); 
							lnPerfTime = System.currentTimeMillis();
							lnPerfBytes = 0;
						}
						break;
					case 1:
						lnChunkSize = loChunkReader.readInt();
						loChunkReader.read(laTemp,0,3);
						//System.out.println("[1???] Read MKV Chunk "+lnChunkSize+" Bytes "+GetHex(laTemp,3));
						loFile = loTs;
						break;						
					case 2:
						lnChunkSize = loChunkReader.readInt();
						loChunkReader.read(laTemp,0,3);
						//System.out.println("[2???] Read DESC Chunk "+lnChunkSize+" Bytes "+GetHex(laTemp,3));
						loFile = null;
						break;
					case -7:
						lnChunkSize = 0;
						Logfile.Write("[BUSY] Recording or Replay in Progress");
						loFile = null;
						break;
					case -17:
						lnChunkSize = 0;
						Logfile.Write("[CONN] Connection Problems");
						break;
					case (byte) 0xff:
						Logfile.Write("TRANSFER COMPLETE");
						write(Header.PT_ACK);
						Unlock();
						if(lbPostCopyAction) {
							PostCopy loPostCopy = new PostCopy(lcPostCopyAction, poFile, pcDstFile, this);
							loPostCopy.start();
						}
						return true;
					default:
						Logfile.Write("[UNKNOWN TYPE "+lbChunkType+"]");
						Unlock();
						return false;
					}
				}
				if(lnChunkSize>0) {					
					lnReadSize = loSocketRead.read(laBuffer, 0, lnChunkSize>laBuffer.length ? laBuffer.length : lnChunkSize );
					if(lnReadSize<=0) {
						System.out.println("No Data, Bad Connection "+lnReadSize + "("+lnChunkSize+")");
					}
					
					if(lnReadSize>0) {
						if(loNextFile!=null) {
							loFile = loNextFile;
							loNextFile = null;
						}
						if(loFile!=null)
							loFile.write(laBuffer,0,lnReadSize);
						
						lnPerfBytes+=lnReadSize;
						lnBytesReaded+=lnReadSize;
						lnChunkSize = lnChunkSize - lnReadSize;
					}
				} else {
					Logfile.Write("NULL DATA");
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
			Logfile.Write("Transmition Complete");
		} catch (IOException e) {
			e.printStackTrace();
		}		
		Unlock();		
		return false;
	}

	/*
	 * Remove a File from the Receiver FS
	 */
	public boolean Rm(DvrFile poFile) {
		Lock();
		Logfile.Write("Removing File " + poFile);
		boolean lbOk = false;
		ByteArrayOutputStream loLowLevelCommand = new ByteArrayOutputStream();
		DataOutputStream loCommand = new DataOutputStream(loLowLevelCommand);
		byte lbResponse = 0;
		try {
			loCommand.writeByte(0x17);
			loCommand.writeShort(poFile.getRecNo());
			write(loLowLevelCommand.toByteArray());
			DataInputStream loResponse = readdata();			
			lbResponse = loResponse.readByte();
			if(lbResponse==1)
				lbOk = true;
			else
				System.out.println("Error in Receiver Response (RM Command) " + lbResponse);
		} catch (IOException e) {
			e.printStackTrace();
		}
		Unlock();
		return lbOk;
	}

	public DvrDirectory Cd(String pcDir) {
		if(pcDir.equals("/")) {
			return GetRoot();
		}
		return null;
	}

	public void Lock() {
		try {
			//System.out.println("BeginAcuire()");
			m_oSemaphore.acquire();
			//System.out.println("Acuire()");
		} catch (InterruptedException e) {
			System.out.println("Semaphore Failed!");
		}
	}
	
	public void Unlock() {
		//System.out.println("BeginUnlock()");
		m_oSemaphore.release();
		//System.out.println("Unlock()");
	}

	public void Idle() throws IOException {
		DataInputStream loResponse = null;
		write(Header.PT_ACK);
		readack();
	}

	public void Quit() {
		Lock();
		return;
	}
}
