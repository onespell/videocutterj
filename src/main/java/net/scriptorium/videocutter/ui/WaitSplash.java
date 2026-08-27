package net.scriptorium.videocutter.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;

public class WaitSplash extends Splash {

	WaitSplash(final Shell parent, final String title) {
		super(parent, title);
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
		final ProgressBar bar = new ProgressBar(shell, SWT.INDETERMINATE);
		final GridData gridData;
		{
			gridData = new GridData(SWT.FILL, SWT.CENTER, true, false);
			gridData.widthHint = 280;
		}
		bar.setLayoutData(gridData);
	}
}
