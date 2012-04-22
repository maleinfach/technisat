import java.io.IOException;
import java.io.PrintStream;

public class PostCopy extends Thread {
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
