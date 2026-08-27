package net.scriptorium.videocutter.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;

final class Dialogs {

	static boolean confirm(final Shell parent, final String title, final String question) {
		final MessageBox box = new MessageBox(parent, SWT.ICON_QUESTION | SWT.YES | SWT.NO);
		box.setText(title == null ? "" : title);
		box.setMessage(question);
		return box.open() == SWT.YES;
	}

	static void error(final Shell parent, final String message) {
		final MessageBox box = new MessageBox(parent, SWT.ICON_ERROR | SWT.OK);
		box.setText("");
		box.setMessage(message);
		box.open();
	}

	private Dialogs() {
		//
	}
}
