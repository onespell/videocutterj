package net.scriptorium.videocutter.ui;

import net.scriptorium.videocutter.L10n;
import net.scriptorium.videocutter.Session;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;

final class ToolBox {

	final Composite frame;

	final ShotBox shotBox;

	final ClipBox clipBox;

	final JobBox jobBox;

	private final Button manualInputBtn;

	ToolBox(final Composite parent, final Session session, final Runnable onSelect) {
		frame = new Composite(parent, SWT.BORDER);
		final GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 5;
		layout.marginHeight = 5;
		frame.setLayout(layout);

		jobBox = new JobBox(frame, session, onSelect);
		shotBox = new ShotBox(frame, jobBox::add, onSelect);
		clipBox = new ClipBox(frame, jobBox::add, onSelect);

		shotBox.frame.moveAbove(jobBox.frame);
		clipBox.frame.moveBelow(shotBox.frame);

		manualInputBtn = new Button(frame, SWT.PUSH);
		manualInputBtn.setText(L10n.t("manualInput"));
		manualInputBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		manualInputBtn.moveAbove(jobBox.frame);
		manualInputBtn.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				new ManualInput(frame.getShell(), L10n.t("add"), jobBox::insertJobs).show();
			}
		});

		shotBox.frame.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		clipBox.frame.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		jobBox.frame.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		frame.layout(true, true);
		setEnabled(false);
	}

	void setEnabled(final boolean value) {
		shotBox.setEnabled(value);
		clipBox.setEnabled(value);
		manualInputBtn.setEnabled(value);
		jobBox.setEnabled(value);
	}
}
