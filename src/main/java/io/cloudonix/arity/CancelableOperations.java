package io.cloudonix.arity;
import ch.loway.oss.ari4java.ARI;

public abstract class CancelableOperations extends Operation {

	public CancelableOperations(String chanId, ARIty s, ARI a) {
		
		super(chanId, s, a);
	}
	
	abstract void cancel ();

}
