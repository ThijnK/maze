package nl.uu.tests.maze;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.util.List;
import java.util.function.Predicate;
import java.util.LinkedList;


/**
 * This is not a test class :) It provides an interceptor for MAZE logger,
 * for the purpose of testing MAZE (so we can check on log messages as oracles).
 */
public class LoggerInterceptor extends AppenderBase<ILoggingEvent> {
	
	public List<ILoggingEvent> logs = new LinkedList<>();
	
	@Override
    public void append(ILoggingEvent eventObject) {
        logs.add(eventObject);
    }
	
	public void clear() {
		logs.clear();
	}
	
	public boolean anyMatch(Predicate<String> P) {
		//System.out.println(">>>> " + logs.size()) ;
		return logs.stream().anyMatch(event -> P.test(event.getFormattedMessage())) ;
	}

}
