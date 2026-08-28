package net.scriptorium.videocutter.ui;

import net.scriptorium.videocutter.L10n;
import net.scriptorium.videocutter.Session;
import net.scriptorium.videocutter.job.Job;
import net.scriptorium.videocutter.job.execution.JobRunner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

final class JobBox {

	private final Logger log = LogManager.getLogger(getClass());

	final Composite frame;

	private final org.eclipse.swt.widgets.List list;

	private final Button runBtn;

	private final List<Job> items = new ArrayList<>();

	private final Session session;

	private final Runnable onSelect;

	private IntConsumer onJump = millis -> {
		//
	};

	JobBox(final Composite parent, final Session session, final Runnable onSelect) {
		this.session = session;
		this.onSelect = onSelect;
		frame = new Composite(parent, SWT.BORDER);
		frame.setLayout(new GridLayout(1, false));

		list = new org.eclipse.swt.widgets.List(frame, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
		final GridData listData = new GridData(SWT.FILL, SWT.FILL, true, true);
		listData.widthHint = 280;
		listData.heightHint = 240;
		list.setLayoutData(listData);
		list.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(final KeyEvent e) {
				if (e.keyCode == SWT.DEL) {
					onDelete();
				}
			}
		});
		list.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDoubleClick(final MouseEvent e) {
				final int idx = list.getSelectionIndex();
				if (idx >= 0 && idx < items.size()) {
					onJump.accept(items.get(idx).timeMillis());
				}
			}
		});

		runBtn = new Button(frame, SWT.PUSH);
		runBtn.setText(L10n.t("run"));
		runBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		runBtn.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				run();
			}
		});

		setEnabled(false);
	}

	void setOnJump(final IntConsumer onJump) {
		this.onJump = onJump;
	}

	boolean isEmpty() {
		return items.isEmpty();
	}

	void add(final Job job) {
		list.add(job.toDisplayString());
		items.add(job);
		list.setTopIndex(list.getItemCount() - 1);
		log.info(job.marshall());
	}

	void reset() {
		items.clear();
		list.removeAll();
	}

	void insertJobs(final String lines) {
		final Shell shell = frame.getShell();
		for (final String str : lines.split("\n", -1)) {
			if (str.trim().isEmpty()) {
				continue;
			}
			try {
				add(Job.parse(str));
			} catch (final RuntimeException ex) {
				Dialogs.error(shell, L10n.t("invalidJobStr") + ": " + str);
			}
		}
	}

	void setEnabled(final boolean value) {
		list.setEnabled(value);
		runBtn.setEnabled(value);
	}

	private void onDelete() {
		if (list.getSelectionCount() == 0) {
			return;
		}
		if (!Dialogs.confirm(frame.getShell(), L10n.t("confirmDeleteTitle"), L10n.t("confirmDeleteQuestion"))) {
			return;
		}
		final int[] selected = list.getSelectionIndices();
		for (int i = selected.length - 1; i >= 0; i--) {
			final int idx = selected[i];
			list.remove(idx);
			items.remove(idx);
		}
		onSelect.run();
	}

	private void run() {
		if (items.isEmpty() || session.filePath() == null) {
			return;
		}
		final List<Job> sorted = JobRunner.sorted(items);
		final int numOfJobs = items.size();
		final Shell shell = frame.getShell();
		final Display display = shell.getDisplay();
		final ProgressSplash splash = new ProgressSplash(shell, L10n.t("executing"), sorted.size());
		splash.show();
		final Thread worker = new Thread(() -> {
			boolean failed = false;
			for (final Job job : sorted) {
				try {
					if (!JobRunner.execute(job, session.filePath(), numOfJobs)) {
						failed = true;
						break;
					}
				} catch (final Exception ex) {
					failed = true;
					break;
				}
				display.asyncExec(splash::increment);
			}
			final boolean fail = failed;
			display.asyncExec(() -> {
				splash.close();
				if (fail) {
					Dialogs.error(shell, L10n.t("jobFail"));
				} else {
					reset();
				}
			});
		}, "vc-jobs");
		worker.start();
		while (!splash.isDisposed() && !display.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
	}
}
