package net.scriptorium.videocutter.ui;

import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Shell;

abstract class Modal {

	protected final Shell parent;

	protected final Shell shell;

	Modal(final Shell parent, final Shell shell) {
		this(parent, shell, "");
	}

	Modal(final Shell parent, final Shell shell, final String title) {
		this.parent = parent;
		this.shell = shell;
		shell.setText(title);
	}

	void show() {
		setup();
		shell.pack();
		center();
		shell.open();
		setFocus();
	}

	abstract void setup();

	protected void setFocus() {
		//
	}

	protected void center() {
		final Rectangle area = parent.getMonitor().getClientArea();
		shell.setLocation(area.x + (area.width - shell.getSize().x) / 2,
				area.y + (area.height - shell.getSize().y) / 2);
	}

	void close() {
		if (shell != null && !shell.isDisposed()) {
			shell.close();
			shell.dispose();
		}
	}
}
