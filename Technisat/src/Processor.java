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
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.Semaphore;

public class Processor {
	private InputStream m_oRead;
	
	private OutputStream m_oWrite;
	
	private Idle m_oIdle;
	
	private byte[] m_aSent;
	
	private Semaphore m_oSemaphore = new Semaphore(1, true);
	
	private int m_nActivePostCopyThreads = 0;

	public Processor(InputStream poRead, OutputStream poWrite) {
		m_oRead = poRead;
		m_oWrite = poWrite;
		/*
		 * Thread for Idle Communication
		 */
		m_oIdle = new Idle(this);
		m_oIdle.start();
	}
	
	private int _read(int pnRetry, int pnMaxRetry, byte[] paBuffer, int pnOffSet, int pnCount) {
		int lnBytesReaded=0;
		try {
			lnBytesReaded = _read(paBuffer, pnOffSet, pnCount);
		} catch (IOException e) {
			if(pnRetry<pnMaxRetry)
				return _read(pnRetry+1, pnMaxRetry, paBuffer, pnOffSet, pnCount);
			else {
				System.out.println("Socket Error " + e.getMessage());
				return -1;
			}
		}
    	return lnBytesReaded;
	}
    
    private int _read(
    	byte[] paBuffer, int pnOffSet, int pnCount
    	) throws IOException {
      int lnReadPos = 0, lnBytes;
      do{
    	  /*
    	   * read data from the socket while the readed byte
    	   * count is lower than pnMinBytes
    	   */
    	  lnBytes = m_oRead.read(paBuffer, pnOffSet+lnReadPos, pnCount-lnReadPos );
    	  if(lnBytes>=0)
    		  lnReadPos+=lnBytes;
    	  else
    		  throw new IOException("Socket IO Exception "+lnBytes+ "("+lnReadPos+" of " + pnCount + " bytes readed)");
      } while(pnCount>lnReadPos);
      Logfile.Data("RxD", paBuffer, lnReadPos);
      return lnReadPos;
    }
    
    private boolean readack() throws IOException {
    	byte[] laBuffer = new byte[1];
    	if(_read(0, 32, laBuffer, 0, 1)>0) {
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
    
    private byte readbyte() throws IOException {
    	byte[] laBuffer = new byte[1];
    	int lnBytes = _read(0, 8, laBuffer, 0, 1);
    	if(lnBytes>0)
    		return laBuffer[0];
    	throw new IOException("No Data");
    }
    
    private void readbyte(byte[] paData) {    	
    	readbyte(paData, 0, paData.length);
    }
    

	private void readbyte(byte[] paBuffer, int pnOffSet, int pnCount) {
		_read(0, 8, paBuffer, pnOffSet, pnCount);
	}    
 
    private short readshort() throws IOException {
    	byte[] laShort = new byte[2];
    	int lnBytes = _read(0, 8, laShort, 0, 2);
    	if(lnBytes==2) {
    		return (new DataInputStream((new ByteArrayInputStream(laShort)))).readShort();
    	}
    	throw new IOException("No Short Value");
    }

    private int readint() throws IOException {
    	byte[] laShort = new byte[4];
    	int lnBytes = _read(0, 8, laShort, 0, 4);
    	if(lnBytes==4) {
    		return (new DataInputStream((new ByteArrayInputStream(laShort)))).readInt();
    	}
    	throw new IOException("No Short Value");
    }     
    
    private long readlong() throws IOException {
    	byte[] laShort = new byte[8];
    	int lnBytes = _read(0, 8, laShort, 0, 8);
    	if(lnBytes==8) {
    		return (new DataInputStream((new ByteArrayInputStream(laShort)))).readLong();
    	}
    	throw new IOException("No Short Value");
    }    
    
    private void write(byte[] paData) {
    	try {
    		Logfile.Data("TxD", paData, paData.length);
    		m_aSent = paData;
   			m_oWrite.write(paData);
		} catch (IOException e) {
			System.out.println("Write Failed");
		} 
	}
	
	private void rewrite() {
    	try {
    		Logfile.Data("TxD", m_aSent, m_aSent.length);
   			m_oWrite.write(m_aSent);
		} catch (IOException e) {
			System.out.println("Write Failed");
		}		
	}
	
	private void write(byte pByte) {
		byte[] laBytes = new byte[1];
		laBytes[0] = pByte;
		write(laBytes);
	}
	
	private void write(String pcValue) {
		write(pcValue.getBytes());
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
	
	private String readstring() throws IOException {
		byte lnFieldLen = readbyte();
		byte[] laField = new byte[lnFieldLen & 0xff];
		readbyte(laField);
		
		if(laField[0]==0x05)
			return new String(laField, 1, laField.length-1, "CP1252");
		else
			return new String(laField, "CP1252");
	}
	
	public DvrDirectory GetDir(String pcDir) {
		DvrDirectory loDir = new DvrDirectory();
		Lock();
		try {		
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
				write(pcDir); //Directory setzen hier weiÃŸ ich nicht obs in zukunft noch probleme mit dem Zeichensatz gibt		
				readbyte();
				ping();
			}
		
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
		boolean lbRead = true;		
		long lnBytesReaded = 0;

		try {
			Logfile.Write("Copy File " + poFile.getFileName() + " to "+pcDstFile);
			loTs = new BufferedOutputStream(new FileOutputStream(loTsFile));			

			loSocketWrite.writeByte(Header.PT_GETFILE); //Download Command;
			loSocketWrite.writeShort(poFile.getIndex()); //File Index		
			loSocketWrite.writeLong(0); //Start Position (maybe!!)
			write(loSocketWriteLow.toByteArray()); // Send Message to DVR
			
			byte[] laFileInfo = new byte[25];
			Logfile.Data("File Header", laFileInfo, laFileInfo.length);
			readbyte(laFileInfo);
			write(Header.PT_ACK);
			byte[] laTemp = new byte[3];
			do{		
				byte lbChunkType = readbyte();
				int lnChunkSize = 0;

				switch(lbChunkType) { 
				case 0: //Data TS Chunk
					lnChunkSize = readint();
					readbyte(laTemp);
					if(laBuffer.length<lnChunkSize)
						laBuffer = new byte[lnChunkSize];
					
					readbyte(laBuffer,0,lnChunkSize);					
					loTs.write(laBuffer,0,lnChunkSize);
					
					lnPerfBytes+=lnChunkSize;
					lnBytesReaded+=lnChunkSize;
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
					lnChunkSize = readint();
					readbyte(laTemp);					
					laBuffer = new byte[lnChunkSize];
					readbyte(laBuffer);
					break;
				case 2:
					lnChunkSize = readint();
					readbyte(laTemp);					
					laBuffer = new byte[lnChunkSize];
					readbyte(laBuffer);	
					break;
				case -7:
					Logfile.Write("[BUSY] Recording or Replay in Progress");
					break;
				case (byte) 0xff:
					ack();
					if(lbPostCopyAction) {
						PostCopy loPostCopy = new PostCopy(lcPostCopyAction, poFile, pcDstFile, this);
						loPostCopy.start();
					}
					lbRead = false;
					break;
				default:
					throw new Exception("Unknown Chunk Type " + lbChunkType);
				}
			} while(lbRead);			
			Logfile.Write("Transmition Complete");
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			Unlock();
		}
		return true;
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
			lbResponse = readbyte();
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

	private void Lock() {
		try {
			m_oSemaphore.acquire();
		} catch (InterruptedException e) {
			System.out.println("Semaphore Failed!");
		}
	}
	
	private void Unlock() {
		m_oSemaphore.release();
	}

	private void Idle() throws IOException {
		write(Header.PT_ACK);
		readack();
	}

	public int GetActiveThreadCount() {
		Lock();
		Logfile.Write("Active Threads "+m_nActivePostCopyThreads);
		int lnReturn = m_nActivePostCopyThreads;
		Unlock();
		return lnReturn;
	}
	

	public void Quit() {
		Lock();
	}		
	
	private class Idle extends Thread {
		public Idle(Processor poProcessor) {
			m_oProcessor = poProcessor;
		}
		public void run() {		
			while(true) {
				/*
				 * Processor Lock
				 */
				m_oProcessor.Lock();
				boolean lbTempDisTransLog = Props.TestProp("TRANSPORTLOG", "1");
				if(lbTempDisTransLog)
					Props.Set("TRANSPORTLOG", "0");
				try {
					m_oProcessor.Idle();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				if(lbTempDisTransLog)
					Props.Set("TRANSPORTLOG", "1");			
				/*
				 * Processor Unlock
				 */
				m_oProcessor.Unlock();
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}	
		Processor m_oProcessor;
	}
	
	private class PostCopy extends Thread {
		String m_cCommand;
		DvrFile m_oFile;
		Processor m_oProcessor;
		String m_cDstFile;
		
		public PostCopy(String pcCommand, DvrFile poFile,String pcDstFile, Processor poProcessor) {
			m_cCommand=pcCommand;
			m_oFile=poFile;
			m_oProcessor=poProcessor;
			m_cDstFile=pcDstFile;
		}
		
		public void run() {
			try {
				Runtime loRt = Runtime.getRuntime();
				String[] lcCommand = new String[] {
					m_cCommand,
					m_cDstFile,
					String.valueOf(m_oFile.getIndex())
				};
				
				Logfile.Write("Execute: "+lcCommand);
				Process loProc = loRt.exec(lcCommand);
				
				try {
					int lnExitCode = loProc.waitFor();
					Logfile.Write("PostCopyScript exited with Exit Code "+lnExitCode);
					m_oProcessor.Lock();
					m_oProcessor.m_nActivePostCopyThreads--;
					m_oProcessor.Unlock();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			
		}
	}
}
