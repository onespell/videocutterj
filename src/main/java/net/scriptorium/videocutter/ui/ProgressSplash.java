package net.scriptorium.videocutter.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;

class ProgressSplash extends Splash {

	private final int max;

	private ProgressBar bar;

	ProgressSplash(final Shell parent, final String title, final int max) {
		super(parent, title);
		this.max = max;
	}

	void increment() {
		if (!bar.isDisposed()) {
			bar.setSelection(bar.getSelection() + 1);
		}
	}

	boolean isDisposed() {
		return shell.isDisposed();
	}

	@Override
	void setup() {
		final GridLayout layout;
		{
			layout = new GridLayout(1, false);
			layout.marginWidth = 16;
			layout.marginHeight = 16;
		}
		shell.setLayout(layout);
		bar = new ProgressBar(shell, SWT.SMOOTH);
		bar.setMaximum(Math.max(max, 1));
		bar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		((GridData) bar.getLayoutData()).widthHint = 280;
	}
}
