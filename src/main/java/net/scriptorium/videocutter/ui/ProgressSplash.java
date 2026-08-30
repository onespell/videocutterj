package net.scriptorium.videocutter.ui;

import net.scriptorium.videocutter.L10n;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;

class ProgressSplash extends Splash {

	private final int max;

	private final Runnable onCancel;

	private ProgressBar bar;

	ProgressSplash(final Shell parent, final String title, final int max) {
		this(parent, title, max, null);
	}

	ProgressSplash(final Shell parent, final String title, final int max, final Runnable onCancel) {
		super(parent, title);
		this.max = max;
		this.onCancel = onCancel;
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
		if (onCancel != null) {
			final Button cancel = new Button(shell, SWT.PUSH);
			cancel.setText(L10n.t("cancel"));
			cancel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			cancel.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(final SelectionEvent e) {
					onCancel.run();
					cancel.setEnabled(false);
				}
			});
		}
	}
}
