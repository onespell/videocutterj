package net.scriptorium.videocutter.ui;

import net.scriptorium.videocutter.L10n;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class LogWindow extends ModalWindow {

	private final String msg;

	LogWindow(final Shell parent, final String msg) {
		super(parent);
		this.msg = msg;
	}

	@Override
	void setup() {
		shell.setLayout(new GridLayout(1, false));
		final Text text = new Text(shell, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.WRAP | SWT.READ_ONLY);
		final GridData textData = new GridData(SWT.FILL, SWT.FILL, true, true);
		textData.widthHint = 560;
		textData.heightHint = 320;
		text.setLayoutData(textData);
		text.setText(msg);
		final Button close = new Button(shell, SWT.PUSH);
		close.setText(L10n.t("closeButton"));
		close.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		close.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				shell.close();
			}
		});
		shell.setDefaultButton(close);
	}
}
