import java.io.DataOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ListIterator;
import java.util.Vector;

public class DvrDirectory {
	public Vector<DvrDirectory> m_oDirectorys = new Vector<DvrDirectory>();	
	public Vector<DvrFile> m_oFiles = new Vector<DvrFile>();
	public String m_cDisplayName = null ;
	public String m_cRemoteName = null;
	public String m_cDescription = null;
	public DvrDirectory m_oParent;
	public boolean m_bIsOpen = false;
	
	public DvrDirectory(DvrDirectory poParent, String pcRemoteName, String pcDisplayName, String pcDescription) {
		m_oParent = poParent;
		m_cDisplayName = pcDisplayName;
		m_cRemoteName = pcRemoteName;
		m_cDescription = pcDescription;
		
		if(poParent!=null)
			m_oDirectorys.add( new DvrDirectory(null, "..", "..", null));
	}
	
	public DvrDirectory(DvrDirectory poSourceDir) {
		m_cDisplayName = poSourceDir.m_cDisplayName;
		m_cRemoteName = poSourceDir.m_cRemoteName;
		m_cDescription = poSourceDir.m_cDescription;		
	}
	
	public String toString() {
		if(m_cDescription!=null)
			return m_cDisplayName + " ("+m_cDescription+")";
		else
			return m_cDisplayName;
	}
	
	public DvrFile GetFileByRecNo(int pnRecNo) {
		ListIterator<DvrFile> loFileIt = m_oFiles.listIterator();
		while(loFileIt.hasNext()) {
			DvrFile loTest = loFileIt.next();
			if(loTest.getIndex()==pnRecNo) {
				return loTest;
			}
		}
		return null;
	}

	public boolean DirExist(String pcDir) {
		ListIterator<DvrDirectory> loDirIt = m_oDirectorys.listIterator();
		while(loDirIt.hasNext()) {
			if(loDirIt.next().m_cDisplayName.toUpperCase().equals(pcDir.toUpperCase()))
				return true;
		}
		return false;
	}

	public void PrintTo(PrintStream poWrite) {		
		ListIterator<DvrDirectory> loIt = m_oDirectorys.listIterator();
		while(loIt.hasNext()) {
			poWrite.println("<DIRECTORY> "+loIt.next());
		}
		ListIterator<DvrFile> loFileIt = m_oFiles.listIterator();
		while(loFileIt.hasNext()) {
			poWrite.println(loFileIt.next());
		}		
	}

	public DvrDirectory GetSubDirectory(String pcDir) {
		ListIterator<DvrDirectory> loIt = m_oDirectorys.listIterator();
		while(loIt.hasNext()) {
			DvrDirectory loDir = loIt.next();
			if(loDir.m_cDisplayName.toUpperCase().equals(pcDir.toUpperCase())) {
				return loDir;
			}
		}
		return null;
	}

	public String GetFullPath() {
		return ( m_oParent==null ? "$/" : m_oParent.GetFullPath() + m_cDisplayName + "/" );
	}
}
