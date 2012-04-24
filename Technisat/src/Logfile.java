import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

public class Logfile {
	OutputStream m_oParentStream;
	
	static File m_oLogFile;
	
	static PrintStream m_oLog;
	
	static SimpleDateFormat m_oDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	static Calendar m_oCalendar = Calendar.getInstance(TimeZone.getDefault());
	
	public static void Open(String pcLogFile) {
		m_oLogFile = new File(pcLogFile);
		try {
			if(!m_oLogFile.isFile()) {
				if(m_oLogFile.isDirectory())
					System.err.println(pcLogFile+" is a Directory!");
				else
					m_oLogFile.createNewFile();
			}
			m_oLog = new PrintStream(new FileOutputStream(m_oLogFile));
		} catch (IOException e) {
			e.printStackTrace();	
		}
	}
	
	public static void Write(String pcString) {
		m_oCalendar.setTimeInMillis(System.currentTimeMillis());
		if(m_oLog!=null) {
			m_oLog.println(m_oDate.format(m_oCalendar.getTime())+ " " + pcString);
		}
		System.out.println(pcString);
	}
	
	public static void Data(String pcPrefix, byte[] paData, int pnLen) {
		if(Props.Get("TRANSPORTLOG").equals("1")) {
			/*
			 * Transport Log um Kommunikationsfehler nachvollziehen zu können
			 */
			String lcMsg = "";
			String lcDispl = "";			
			Write(pcPrefix + " " + pnLen + " Bytes");
			for(int i=1; i<=pnLen; i++) {				
				String lcPart = String.format(Integer.toHexString(paData[i-1] & 0xff));
				lcMsg += " " + ( lcPart.length()>1 ? lcPart : "0" + lcPart );
				if(paData[i-1]>=32)
					lcDispl += String.valueOf((char)paData[i-1]);
				else
					lcDispl +=".";
				if(i%16==0 || i==pnLen) {
					for(int x=i; x%16!=0; x++)
						lcMsg += "   ";
					Write(lcMsg + " | " + lcDispl);
					lcMsg = "";
					lcDispl = "";
				}
			}
		}		
	}
}
