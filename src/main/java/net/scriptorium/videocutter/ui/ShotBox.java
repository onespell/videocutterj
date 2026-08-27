package net.scriptorium.videocutter.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import net.scriptorium.videocutter.L10n;
import net.scriptorium.videocutter.Settings;
import net.scriptorium.videocutter.TimeUtil;
import net.scriptorium.videocutter.job.Job;
import net.scriptorium.videocutter.job.ShotJob;

final class ShotBox {

    private static final String TIMECODE_SAMPLE = "00:00:00.000";

    final Composite frame;
    private final Combo formatBox;
    private final Button freezeBtn;
    private final Label timeLbl;
    private int time = -1;

    ShotBox(Composite parent, java.util.function.Consumer<Job> addJob, Runnable onSelect) {
        frame = new Composite(parent, SWT.BORDER);
        frame.setLayout(new GridLayout(1, false));

        formatBox = new Combo(frame, SWT.READ_ONLY | SWT.DROP_DOWN);
        for (String format : Settings.imageFormats()) {
            formatBox.add(format);
        }
        formatBox.select(0);
        formatBox.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        formatBox.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onSelect.run();
            }
        });

        Composite row = new Composite(frame, SWT.NONE);
        row.setLayout(new GridLayout(2, false));
        row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        freezeBtn = new Button(row, SWT.PUSH);
        freezeBtn.setText(L10n.t("freezeFrame"));
        freezeBtn.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                addJob.accept(new ShotJob(time, formatBox.getText()));
            }
        });

        timeLbl = new Label(row, SWT.NONE);
        timeLbl.setText(TIMECODE_SAMPLE);
        GridData timeData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        timeData.widthHint = timeLbl.computeSize(SWT.DEFAULT, SWT.DEFAULT).x;
        timeLbl.setLayoutData(timeData);
        timeLbl.setText("");

        setEnabled(false);
    }

    void setEnabled(boolean value) {
        formatBox.setEnabled(value);
        freezeBtn.setEnabled(value);
    }

    void setTime(int value) {
        if (value == time) {
            return;
        }
        time = value;
        String text = TimeUtil.toTimeCode(value);
        int oldLen = timeLbl.getText().length();
        timeLbl.setText(text);
        if (oldLen != text.length()) {
            timeLbl.getParent().layout(true);
        }
    }

    void reset() {
        formatBox.select(0);
        time = -1;
        if (!timeLbl.getText().isEmpty()) {
            timeLbl.setText("");
            timeLbl.getParent().layout(true);
        }
    }
}
