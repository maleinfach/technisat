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

	public Processor(InputStream poRead, OutputStream poWrite) {
		m_oRead = poRead;
		m_oWrite = poWrite;
		m_aBuffer = new byte[65536];
		/*
		 * Thread for Idle Communication
		 */
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
    	while(pnMinBytes>0 && m_oRead.available()<pnMinBytes)
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}    	
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

    /**
     * @deprecated
     * @comment
     *  using this method is problematic.
     * @return DataInputStream
     * @throws IOException
     */
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
    
    private byte readbyte() throws IOException {
    	int lnBytes = _read(0, 8, m_aBuffer, 1);
    	if(lnBytes>0)
    		return m_aBuffer[0];
    	throw new IOException("No Data");
    }
    
    private boolean readbyte(byte[] paData) {
    	int lnBytes = _read(0, 8, paData, paData.length);
    	return lnBytes==paData.length;
    }
    
    private short readshort() throws IOException {
    	byte[] laShort = new byte[2];
    	int lnBytes = _read(0, 8, laShort, 2);
    	if(lnBytes==2) {
    		return (new DataInputStream((new ByteArrayInputStream(laShort)))).readShort();
    	}
    	throw new IOException("No Short Value");
    }

    private int readint() throws IOException {
    	byte[] laShort = new byte[4];
    	int lnBytes = _read(0, 8, laShort, 4);
    	if(lnBytes==4) {
    		return (new DataInputStream((new ByteArrayInputStream(laShort)))).readInt();
    	}
    	throw new IOException("No Short Value");
    }     
    
    private long readlong() throws IOException {
    	byte[] laShort = new byte[8];
    	int lnBytes = _read(0, 8, laShort, 8);
    	if(lnBytes==8) {
    		return (new DataInputStream((new ByteArrayInputStream(laShort)))).readLong();
    	}
    	throw new IOException("No Short Value");
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
		String lcName = "";
		String lcLang = "";
		write(Header.PT_GETSYSINFO);
		try {
			byte[] laFlags = new byte[4];
			readbyte(laFlags);
			lcLang = readstring();
			lcName = readstring();
			ack();
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
	
	public String readstring() throws IOException {
		byte lnFieldLen = readbyte();
		byte[] laField = new byte[lnFieldLen & 0xff];
		if(readbyte(laField)) {
			if(laField[0]==0x05) //Ist mir noch nicht ganz Klar, vll. CodePage Informationen
				return new String(laField, 1, laField.length-1, "CP1252");
			else
				return new String(laField, "CP1252");
		}
		return null;
	}
	
	public DvrDirectory GetDir(String pcDir) throws IOException {
		Lock();
		
		DvrDirectory loDir = new DvrDirectory();
		Calendar loCalendar = Calendar.getInstance(TimeZone.getDefault());

		byte[] laGetDir = new byte[] //Command
			{
				Header.PT_GETDIR,
				0,
				(byte) (pcDir==null ? 0 : 1)
			};
		write(laGetDir); //Send
		readack();
		
		if(pcDir!=null) {
			write(pcDir); //Directory setzen hier weiß ich nicht obs in zukunft noch probleme mit dem Zeichensatz gibt		
			readbyte();
			ping();
		}

		try {
			short lnAnzElements = readshort();
			while(lnAnzElements>0) {
				/*
				 * Die Technisat Zeitstempel sind irgendwie die anzahl der Sekunden
				 * ab dem 1.1.2000 - 1 Monat oder so. Ganz komisch. 
				 */
				loCalendar.set(2000, 01, 01, 00, 00, 00);
				loCalendar.add(Calendar.MONTH, -1);
				byte lbType = readbyte();
				byte lbIsDir = readbyte(); //Nicht sicher
				switch(lbType) {
				case 0: //Directory
					loDir.m_oDirectorys.add(new DvrDirectory(readstring()));
					break;
				case 4: //File Record SD Quality
				case 7: //File Record HD Quality
					short lnIndex = readbyte();
					String lcFileName = readstring();
					long lnSize = readlong();
					int lnTimeStamp = readint();
					loCalendar.add(Calendar.SECOND, lnTimeStamp);
					loDir.m_oFiles.add( new DvrFile(lcFileName, lnSize, lnIndex, lbType, loCalendar.getTime()));
					break;
				default:
					throw new IOException("Unknown RecordType " + lbType);
				}
				lnAnzElements--;
			}
		} catch (IOException e) {
			e.printStackTrace();
			loDir = null;
		} finally {
			Unlock();
		}		
		return loDir;
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
		String lcPostCopyAction = Props.Get("POSTCOPYSCRIPT");
		int lnPostCopyThreads = Integer.parseInt(Props.Get("POSTCOPYTHREADCOUNT"));
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
			if(Props.Get("SAFEITY").equals("1")) {
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

	public void Lock() {
		try {
			m_oSemaphore.acquire();
		} catch (InterruptedException e) {
			System.out.println("Semaphore Failed!");
		}
	}
	
	public void Unlock() {
		m_oSemaphore.release();
	}

	public void Idle() throws IOException {
		write(Header.PT_ACK);
		readack();
	}

	public void Quit() {
		Lock();
		return;
	}
}
