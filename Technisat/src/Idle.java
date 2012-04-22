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
			
			m_oProcessor.Idle();
			
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
