import java.io.IOException;

/*
 * Diese Klasse sendet Idle Pakete damit der Receiver
 * die Verbindung nicht abbaut.
 */
public class Idle extends Thread {
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
