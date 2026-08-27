package net.scriptorium.videocutter.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;

abstract class Splash extends Modal {

	Splash(final Shell parent, final String title) {
		super(parent, new Shell(parent, SWT.TITLE | SWT.BORDER | SWT.APPLICATION_MODAL), title);
	}
}
