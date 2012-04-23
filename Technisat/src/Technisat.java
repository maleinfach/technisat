import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.ListIterator;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Technisat {

	/**
	 * @param args
	 */
	private String m_cPath;
	
	private DvrDirectory m_oDirectory;
	
	private Properties m_oProps = new Properties();

	private String m_cReceiver = "";
	
	public static void main(String[] args) throws Exception {
		Technisat loTech = new Technisat();
		File loStartScript = null;
		/*
		 * Parsing Command Line Arguments
		 */				
		for(int i=0; i<args.length; i++) {
			//System.out.println("arg["+i+"] "+args[i]);
			
			if(args[i].equals("--script")) {
				loStartScript = new File(args[i+1]);				
				if(!loStartScript.isFile()) {
					Logfile.Write("File doesnt Exist "+loStartScript);
					System.exit(1);
				}
			}
		}
		loTech.Set("PostCopyScript=");
		loTech.Set("PostCopyThreadCount=1");
		loTech.Set("Safeity=0");
		loTech.Set("Transportlog=0");
		
		loTech.Shell(loStartScript);
	}
	
	public void Ls(DvrDirectory poDir) {
		poDir.PrintTo(System.out);
	}
	
	public boolean Cd(String pcDir) throws IOException {
		if(IsConnected()) {
			DvrDirectory loDir = null;
			if(pcDir.equals("/")) {
				loDir = m_oProcessor.GetRoot();
				if(loDir!=null) {
					m_cPath=pcDir;
					m_oDirectory = loDir;
					return true;
				}
			} else {
				if(m_oDirectory.DirExist(pcDir)) {
					loDir = m_oProcessor.GetDir(pcDir);
					if(loDir!=null) {
						m_cPath="/"+pcDir+"/";
						m_oDirectory = loDir;
						return true;
					}
				} else {
					System.out.println("Unknown Directory "+pcDir);
					return true;
				}
			}
		}
		return false;
	}
	
	private void Shell(File poStartScript) {		
		boolean lbReadCommand = true;
		Map<String,String> loEnv = System.getenv();
		String lcOs = (loEnv.get("OS"));
		if(lcOs!=null) {
			if(lcOs.equals("Windows_NT")) {
				try {
					System.setOut(new PrintStream(System.out,true,"CP850"));
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				}
			}
		}
		else {
			lcOs="?";
		}
		
		String lcCommand = "";
		if(poStartScript!=null) {
			//System.out.print("Starting Script "+poStartScript);
			try {
				BufferedReader loShell = new BufferedReader(new InputStreamReader(new FileInputStream(poStartScript)));
				String lcLine;
				try {
					lcLine = loShell.readLine();
					while(lcLine!=null) {
						if(!Execute(lcLine))
							Logfile.Write("Invalid Command: "+lcLine);
						lcLine = loShell.readLine();
					}
				} catch (IOException e) {
					Logfile.Write("Error Reading Line");
				}
			} catch (FileNotFoundException e) {
				Logfile.Write("Error Reading File "+poStartScript);
			}
		}
		
		System.out.println("Technisat Receiver Command Line Tool");
		System.out.println("Website Blog: http://www.maleinfach.de");
		System.out.println("GitHub: https://github.com/FreeOrg/technisat");
		System.out.println("-----------------");

		BufferedReader loShell = null;
		try {
			if(lcOs.equals("Windows_NT"))
				loShell = new BufferedReader(new InputStreamReader(System.in,"CP850"));
			else
				loShell = new BufferedReader(new InputStreamReader(System.in));
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
		}
		
		while(lbReadCommand) {
			if(m_cReceiver.equals(""))
				System.out.print("Technisat "+m_cPath+"> ");
			else
				System.out.print(m_cReceiver+" "+m_cPath+"> ");
			try {
				lcCommand = loShell.readLine();
				if(!Execute(lcCommand))
					Logfile.Write("Invalid Command: "+lcCommand);
			} catch (IOException e) {
				System.out.println("Error");
			}
		}		
	}
	
	private DvrDirectory FilterDir(DvrDirectory poDir, String pcFilter) {
		DvrDirectory loResponse = new DvrDirectory();
		Pattern loPattern = Pattern.compile("^"+pcFilter.replace("*", ".*")+"$", Pattern.CASE_INSENSITIVE);
		ListIterator<DvrFile> loIt = poDir.m_oFiles.listIterator();
		while(loIt.hasNext()) {
			DvrFile loFile = loIt.next();
			Matcher loMatcher = loPattern.matcher(loFile.getFileName());
			if(loMatcher.matches()) {
				loResponse.m_oFiles.add(loFile);
			}
		}
		ListIterator<DvrDirectory> loDirIt = poDir.m_oDirectorys.listIterator();
		while(loDirIt.hasNext()) {
			DvrDirectory loDir = loDirIt.next();
			Matcher loMatcher = loPattern.matcher(loDir.m_cName);
			if(loMatcher.matches()) {
				loResponse.m_oDirectorys.add(loDir);
			}
		}
		return loResponse;
	}
	
	private DvrDirectory FilterDirParser(
			String[] paCommand, DvrDirectory poDirectory) {

		String lcCommand = paCommand[0];
		String[] laMatches = Match(lcCommand,"(\\d+)(.*)");
		DvrDirectory loFilterDir = null;
		/*
		 * Try Matching a Single Rec by Record Number
		 */
		if(laMatches!=null) {
			loFilterDir = new DvrDirectory();
			DvrFile loSingleRec = poDirectory.GetFileByRecNo(Integer.parseInt(laMatches[0]));
			if(loSingleRec!=null) {
				loFilterDir = new DvrDirectory();
				loFilterDir.m_oFiles.add(loSingleRec);
			}
		} else {
			/*
			 * Try Matching a Collection of Files by Fiele Selection
			 */
			laMatches = Match(lcCommand,"\\{(.*)\\}(.*)");
			if(laMatches!=null) {
				loFilterDir = FilterDir(poDirectory, laMatches[0]);
			}
		}
		
		if(loFilterDir!=null) // Update Command Line
			if(laMatches.length>1)
				paCommand[0]=laMatches[1].trim();
			else
				paCommand[0]=null;

		return loFilterDir;
	}
	
	private boolean Execute(String pcCommand) throws IOException {
		
		if(pcCommand.trim().equals(""))
			return true;
		
		if(pcCommand.startsWith("#"))
			return true;
		
		if(pcCommand.startsWith("connect "))
			return Connect(pcCommand.substring(8));		
		
		if(pcCommand.equals("help"))
			return Help();
		
		if(pcCommand.startsWith("set "))
			return Set(pcCommand.substring(4));
		
		if(pcCommand.equals("set"))
			return SetList();
		/*
		if(pcCommand.equals("clear"))
			return Clear();
		*/
		
		if(pcCommand.equals("quit") || pcCommand.equals("exit"))
			return Quit();
		
		if(!IsConnected()) {
			Logfile.Write("You must Connect a Receiver with the Connect Command");
			return false;
		}
		
		if(pcCommand.equals("ls"))
			return Ls("{*}");
		
		if(pcCommand.startsWith("ls "))
			return Ls(pcCommand.substring(3));

		if(pcCommand.startsWith("cd "))
			return Cd(pcCommand.substring(3));
		
		if(pcCommand.startsWith("cp "))
			return Cp(pcCommand.substring(3));
		
		if(pcCommand.startsWith("arch "))
			return Arch(pcCommand.substring(5));
		
		if(pcCommand.startsWith("rm "))
			return Rm(pcCommand.substring(3));
		
		return false;
	}

	private boolean Set(String pcCommand) {
		int lnComp = pcCommand.indexOf("=");
		if(lnComp>0) {
			int lnIndex = pcCommand.indexOf("=");
			if(lnIndex>0) {
				String lcSetVar = pcCommand.substring(0,lnIndex);
				pcCommand = pcCommand.substring(lnIndex+1);
				//System.out.println("SET VAR "+lcSetVar+" TO "+pcCommand);
				m_oProps.setProperty(lcSetVar.toUpperCase(), pcCommand.trim());
				if(lcSetVar.toUpperCase().equals("LOGFILE")) {
					Logfile.Open(pcCommand);
				}
				if(lcSetVar.toUpperCase().equals("TRANSPORTLOG")) {
					Logfile.m_bTransportLog = pcCommand.equals("1") ? true : false;
				}				
				return true;
			}
		}
		Logfile.Write("Invalid. Usage SET [VAR]=[VALUE]");
		return true;
	}
	
	private boolean SetList() {
		m_oProps.list(System.out);
		return true;
	}

	private boolean Clear() {
		String ESC = "\033[";
		System.out.print(ESC + "2J"); System.out.flush();		 
		return true;
	}

	private boolean Help() {
		System.out.println("Commad List");
		System.out.println("");
		System.out.println("help");
		System.out.println("connect [IP/HOSTNAME]");
		System.out.println("ls");
		System.out.println("cd [DIRECTORY]");
		System.out.println("rm ([RECNO]|{SELECTON})");
		System.out.println("cp ([RECNO]|{SELECTON}) [LOCAL_DSTDIR]");
		System.out.println("arch ([RECNO]|{SELECTON}) [LOCAL_DSTDIR]");
		System.out.println("");
		System.out.println("Examples {Selecton}");
		System.out.println("ls {*} lists all Files in the actual selected directory");
		System.out.println("ls 232 removes only record Number 232 in the actual directory");
		System.out.println("");
		
		return true;
	}

	/*
	 * Remove Files
	 */
	private boolean Rm(String pcCommand) {
		String[] laCommand = new String[] {pcCommand};		
		DvrDirectory loRmColl = FilterDirParser(laCommand, m_oDirectory);
		if(loRmColl!=null) {
			ListIterator<DvrFile> loFileIter = loRmColl.m_oFiles.listIterator();
			while(loFileIter.hasNext()) {
				DvrFile loFile = loFileIter.next();
				m_oProcessor.Rm(loFile);
				m_oDirectory.m_oFiles.remove(loFile);				
			}
			return true;
		}
		return false;
	}

	/*
	 * Copy and Remove Files
	 */
	private boolean Arch(String pcCommand) {
		String[] laCommand = new String[] {pcCommand};		
		DvrDirectory loFileColl = FilterDirParser(laCommand, m_oDirectory);
		if(loFileColl!=null) {
			if(laCommand[0]!=null) {
				ListIterator<DvrFile> loFileIter = loFileColl.m_oFiles.listIterator();
				while(loFileIter.hasNext()) {
					DvrFile loFile = loFileIter.next();
					if(m_oProcessor.Download(loFile,laCommand[0])) {
						m_oProcessor.Rm(loFile);
						m_oDirectory.m_oFiles.remove(loFile);
					}
				}
				return true;
			}
		}
		return false;
	}

	private boolean Ls(String pcCommand) {
		String[] laCommand = new String[] {pcCommand};		
		DvrDirectory loFileColl = FilterDirParser(laCommand, m_oDirectory);
		if(loFileColl!=null)
			Ls(loFileColl);
		return true;
	}

	private boolean Quit() {
		if(m_oProcessor!=null) {
			boolean lbWait=true;
			do {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				m_oProcessor.Lock();
				Logfile.Write("Active Threads "+m_oProcessor.m_nActivePostCopyThreads);
				lbWait = m_oProcessor.m_nActivePostCopyThreads>0;
				m_oProcessor.Unlock();
			} while(lbWait);
			m_oProcessor.Quit();
			try {
				m_oSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		System.exit(0);
		return true;
	}

	private boolean Connect(String pcHost) {
		if(m_oProcessor!=null)
			m_oProcessor.Quit();
		
		try {
			Logfile.Write("Connecting "+pcHost);			
			m_oSocket = new Socket(pcHost, 2376);
			m_oSocket.setTcpNoDelay(true);
			m_oSocket.setSoTimeout(1000);
			m_oSocket.setReceiveBufferSize((1024*1024)*32);
			m_oRead = m_oSocket.getInputStream();
			m_oWrite = m_oSocket.getOutputStream();
			m_oProcessor = new Processor(m_oRead, m_oWrite, m_oProps);
			Logfile.Write("Connected to "+pcHost);
			m_cReceiver = m_oProcessor.GetReceiverInfo();
			return Cd("/");
		} catch (UnknownHostException e) {
			Logfile.Write("Unknown Host "+e.getMessage());
		} catch (IOException e) {
			Logfile.Write("IO Error "+e.getMessage());
		}
		return false;
	}

	private String[] Match(String pcString, String pcRx) {
		Pattern loPattern = Pattern.compile("^"+pcRx+"$", Pattern.CASE_INSENSITIVE);
		Matcher loMatcher = loPattern.matcher(pcString);
		String[] laMatches;
		if(loMatcher.matches()) {
			MatchResult loRes = loMatcher.toMatchResult();
			laMatches = new String[loRes.groupCount()];
			for(int i=0; i<loRes.groupCount(); i++) {
				laMatches[i] = pcString.substring(loRes.start(i+1), loRes.end(i+1));
			}
			return laMatches;
		}
		return null;
	}
	
	private boolean Cp(String pcCommand) {
		String[] laCommand = new String[] {pcCommand};		
		DvrDirectory loFileColl = FilterDirParser(laCommand, m_oDirectory);
		if(loFileColl!=null) {
			if(laCommand[0]!=null) {
				ListIterator<DvrFile> loFileIter = loFileColl.m_oFiles.listIterator();
				while(loFileIter.hasNext()) {
					DvrFile loFile = loFileIter.next();
					if(!m_oProcessor.Download(loFile,laCommand[0]))
						System.out.println("Download Failed");
				}
				return true;
			}
		}
		return false;
	}
	
	private boolean IsConnected() {
		if(m_oProcessor!=null) {
			return true;
		}
		return false;
	}

	InputStream m_oRead = null;
	
	OutputStream m_oWrite = null;
	
	Socket m_oSocket = null;
	
	Processor m_oProcessor = null;
}
