package net.scriptorium.videocutter;

public class UncheckedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public UncheckedException(final Throwable cause) {
		super(cause);
	}

	public Throwable unwrap() {
		Throwable result = getCause();
		if (result != null) {
			result = (result instanceof UncheckedException) ? ((UncheckedException) result).unwrap() : result;
		} else {
			result = this;
		}
		return result;
	}

}
