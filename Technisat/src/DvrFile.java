import java.text.SimpleDateFormat;
import java.util.Date;

public class DvrFile {	
	public DvrFile(DvrDirectory poParent, String pcFileName, long pnFileSize, short pnIndex, byte pbType, Date pdDate) {
		m_cFileName = pcFileName;
		m_nFileSize = pnFileSize;
		m_nIndex = pnIndex;
		m_nType = pbType;
		m_dDate = pdDate;
		m_oParent = poParent;
	}
	public String toString() {
		SimpleDateFormat loForm = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
		return
					(m_nIndex>=0 ? String.format("%4d", m_nIndex) : "  --") + " " + 
					getTypeString() + " "+ 
					String.format("%5d",m_nFileSize/1024/1024)+"MB " + 
					loForm.format(m_dDate) + " " + 
					m_cFileName;
	}
	public String getTypeString() {
		switch(m_nType) {
		case 1:
			return "BIN  ";
		case 4:
			return "TS/SD";
		case 7:
			return "TS/HD";
		}
		return "     ";
	}
	public short getIndex() {
		return m_nIndex;
	}
	DvrDirectory m_oParent;
	String m_cFileName;
	long m_nFileSize;
	short m_nIndex;
	byte m_nType;
	Date m_dDate;
	
	public long getFileSize() {
		return m_nFileSize;
	}
	public String getFileName() {
		return m_cFileName;
	}
	
	String[] m_aReplace = new String[]{"\\","/",":","*","?","\"","<",">","|"};
	
	public String getUniqueFileName() {
		SimpleDateFormat loForm = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
		String lcFileName = m_cFileName;
		for(int i=0; i<m_aReplace.length; i++) {
			lcFileName = lcFileName.replace(m_aReplace[i], "");
		}
		lcFileName = loForm.format(m_dDate) + " " + lcFileName;
		return lcFileName;
	}
	public short getRecNo() throws Exception {
		if(m_nIndex<0)
			throw new Exception("File has no Record Number");
		return m_nIndex;
	}
	public boolean isRecNo() {
		return m_nIndex>0;
	}
}
