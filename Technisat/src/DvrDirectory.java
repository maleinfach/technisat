import java.io.DataOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ListIterator;
import java.util.Vector;

public class DvrDirectory {
	public Vector<DvrDirectory> m_oDirectorys = new Vector<DvrDirectory>();	
	public Vector<DvrFile> m_oFiles = new Vector<DvrFile>();
	public String m_cName = null ;
	public String m_cDescription = null;
	
	public DvrDirectory(String pcName, String pcDescription) {
		m_cName=pcName;
		m_cDescription=pcDescription;
	}
	
	public DvrDirectory(String pcName) {
		m_cName=pcName;
	}
	
	public DvrDirectory() {
		m_cName="";
	}
	
	public String toString() {
		if(m_cDescription!=null)
			return m_cName + " ("+m_cDescription+")";
		else
			return m_cName;
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
			if(loDirIt.next().m_cName.toUpperCase().equals(pcDir.toUpperCase()))
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
}
