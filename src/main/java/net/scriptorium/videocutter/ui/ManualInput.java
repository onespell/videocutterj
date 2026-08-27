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

public class ManualInput extends ModalWindow {

	private final String okCaption;

	private final java.util.function.Consumer<String> onOk;

	private Text text;

	ManualInput(final Shell parent, final String okCaption,
			final java.util.function.Consumer<String> onOk) {
		super(parent);
		this.okCaption = okCaption;
		this.onOk = onOk;
	}

	@Override
	void setup() {
		shell.setLayout(new GridLayout(2, true));
		text = new Text(shell, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.WRAP);
		final GridData textData = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
		textData.widthHint = 480;
		textData.heightHint = 240;
		text.setLayoutData(textData);
		final Button cancel = new Button(shell, SWT.PUSH);
		cancel.setText(L10n.t("cancel"));
		cancel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		cancel.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				shell.close();
			}
		});
		final Button ok = new Button(shell, SWT.PUSH);
		ok.setText(okCaption);
		ok.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		ok.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				final String value = text.getText();
				shell.close();
				onOk.accept(value);
			}
		});
		shell.setDefaultButton(ok);
	}

	@Override
	protected void setFocus() {
		text.setFocus();
	}
}
