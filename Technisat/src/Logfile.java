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
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		try {
			m_oLog = new PrintStream(new FileOutputStream(m_oLogFile));
		} catch (FileNotFoundException e) {
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
}