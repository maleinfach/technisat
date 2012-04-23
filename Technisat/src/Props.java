import java.io.PrintStream;
import java.util.Properties;

public class Props {
	private static Properties m_oGlobalProps = new Properties();
	
	public static void Set(String pcParam, String pcValue) {
		m_oGlobalProps.setProperty(pcParam.toUpperCase(), pcValue);
	}
	
	public static String Get(String pcParam) {
		return m_oGlobalProps.getProperty(pcParam);
	}
	
	public static boolean TestProp(String pcProp, String pcValue) {
		return m_oGlobalProps.getProperty(pcProp.toUpperCase()).equals(pcValue);
	}

	public static void List(PrintStream poOut) {
		m_oGlobalProps.list(poOut);
	}
}
