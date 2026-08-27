package net.scriptorium.videocutter.ui;

import net.scriptorium.videocutter.L10n;
import net.scriptorium.videocutter.media.Player;
import net.scriptorium.videocutter.media.Position;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Scale;

import java.util.List;

final class MediaBar {

	final Composite frame;

	private final Player player;

	private final Button pauseBtn;

	private final Button rewindBtn;

	private final Button refineLeftBtn;

	private final Button refineRightBtn;

	private final Button forwardBtn;

	private final Scale timeScl;

	private final Icons icons;

	private final Runnable onTimeChanged;

	private boolean paused = true;

	private int duration = -1;

	// private int time;

	private final Position position = new Position();

	private List<Integer> keyFrames = List.of();

	private boolean updatingTimeScale;

	private RefineState refine;

	private boolean mediaEnabled;

	MediaBar(final Composite parent, final Icons icons, final Player player, final Runnable onTimeChanged) {
		this.icons = icons;
		this.player = player;
		this.onTimeChanged = onTimeChanged;
		frame = new Composite(parent, SWT.NONE);
		final GridLayout layout = new GridLayout(6, false);
		layout.marginWidth = 10;
		layout.marginHeight = 10;
		frame.setLayout(layout);

		pauseBtn = new Button(frame, SWT.PUSH);
		pauseBtn.setLayoutData(fixedButton());
		pauseBtn.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				togglePause();
			}
		});

		timeScl = new Scale(frame, SWT.HORIZONTAL);
		timeScl.setMinimum(0);
		timeScl.setMaximum(1);
		timeScl.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		timeScl.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				if (!updatingTimeScale) {
					refine = null;
					goTo(timeScl.getSelection());
				}
			}
		});

		rewindBtn = new Button(frame, SWT.PUSH);
		rewindBtn.setImage(icons.rewind);
		rewindBtn.setToolTipText(L10n.t("prevKeyFrame"));
		rewindBtn.setLayoutData(fixedButton());
		rewindBtn.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				rewind();
			}
		});

		refineLeftBtn = new Button(frame, SWT.PUSH);
		refineLeftBtn.setImage(icons.refineLeft);
		refineLeftBtn.setToolTipText(L10n.t("refineLeft"));
		refineLeftBtn.setLayoutData(fixedButton());
		refineLeftBtn.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				refineLeft();
			}
		});

		refineRightBtn = new Button(frame, SWT.PUSH);
		refineRightBtn.setImage(icons.refineRight);
		refineRightBtn.setToolTipText(L10n.t("refineRight"));
		refineRightBtn.setLayoutData(fixedButton());
		refineRightBtn.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				refineRight();
			}
		});

		forwardBtn = new Button(frame, SWT.PUSH);
		forwardBtn.setImage(icons.forward);
		forwardBtn.setToolTipText(L10n.t("nextKeyFrame"));
		forwardBtn.setLayoutData(fixedButton());
		forwardBtn.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				forward();
			}
		});

		switchPauseBtnMode();
		setEnabled(false);
	}

	private static GridData fixedButton() {
		final GridData data = new GridData(SWT.CENTER, SWT.CENTER, false, false);
		data.widthHint = 36;
		data.heightHint = 36;
		return data;
	}

	void requestFocus() {
		frame.setFocus();
		pauseBtn.setFocus();
	}

	private void togglePause() {
		if (paused) {
			refine = null;
			paused = false;
			switchPauseBtnMode();
			player.play();
		} else {
			paused = true;
			switchPauseBtnMode();
			player.pause();
		}
	}

	private void switchPauseBtnMode() {
		if (paused) {
			pauseBtn.setImage(icons.play);
			pauseBtn.setToolTipText(L10n.t("play"));
			rewindBtn.setEnabled(mediaEnabled);
			forwardBtn.setEnabled(mediaEnabled);
			refineLeftBtn.setEnabled(mediaEnabled);
			refineRightBtn.setEnabled(mediaEnabled);
		} else {
			pauseBtn.setImage(icons.pause);
			pauseBtn.setToolTipText(L10n.t("pause"));
			rewindBtn.setEnabled(false);
			forwardBtn.setEnabled(false);
			refineLeftBtn.setEnabled(false);
			refineRightBtn.setEnabled(false);
		}
	}

	void setTime(final int value) {
		position.set(-1, value);
		updatingTimeScale = true;
		timeScl.setSelection(value);
		updatingTimeScale = false;
	}

	void rewind() {
		final Integer prev = previousKeyFrame(Math.min(position.getActual(), position.getRequested()));
		if (prev == null) {
			return;
		}
		refine = null;
		goTo(prev);
	}

	void forward() {
		final Integer next = nextKeyFrame(Math.max(position.getActual(), position.getRequested()));
		if (next == null) {
			return;
		}
		refine = null;
		goTo(next);
	}

	void refineLeft() {
		if (refine == null) {
			final Position r = position.copy();
			final Integer prev = previousKeyFrame(r.getActual());
			final Position l = (prev != null) ? new Position(prev.intValue()) : new Position(0);
			refine = new RefineState(l, r);
		}
		refineToMidpoint(true);
	}

	void refineRight() {
		if (refine == null) {
			final Position l = position.copy();
			final Integer next = nextKeyFrame(l.getActual());
			final Position r = (next != null) ? new Position(next.intValue()) : new Position(duration);
			refine = new RefineState(l, r);
		}
		refineToMidpoint(false);
	}

	/**
	 * Move toward midpoint; clear refine if refined mid lands on an end.
	 */
	private void refineToMidpoint(final boolean onRefineLeft) {
		assert refine != null : "interval is not set";
		final Position startPosition = position.copy();
		assert refine.contains(startPosition) : "interval is not valid";
		if (refine.isDegenerate()) {
			return;
		}
		Position l = onRefineLeft ? refine.l : startPosition;
		Position r = onRefineLeft ? startPosition : refine.r;
		final int c = (l.getRequested() + r.getRequested()) / 2;
		if (l.getRequested() < c) {
			goTo(c);
		} else if (onRefineLeft) {
			goTo(l.getRequested());
			l.setActual(position.getActual());
		} else {
			goTo(r.getRequested());
			r.setActual(position.getActual());
		}
		Position newPosition = position.copy();
		if (startPosition.getActual() == newPosition.getActual()) {
			if (onRefineLeft) {
				goTo(l.getRequested());
				l.setActual(position.getActual());
			} else {
				goTo(r.getRequested());
				r.setActual(position.getActual());
			}
			newPosition = position.copy();
		}
		if (l.getActual() > newPosition.getActual()) {
			l = newPosition;
		}
		if (r.getActual() < newPosition.getActual()) {
			r = newPosition;
		}
		refine = new RefineState(l, r);
	}

	private Integer previousKeyFrame(final int t) {
		if (keyFrames.isEmpty()) {
			return null;
		}
		final int[] interval = getInterval(t);
		final int left = interval[0];
		if (left < 0) {
			return null;
		}
		final int i;
		if (keyFrames.get(left) < t) {
			i = left;
		} else if (left > 0) {
			i = left - 1;
		} else {
			return null;
		}
		return keyFrames.get(i);
	}

	private Integer nextKeyFrame(final int t) {
		if (keyFrames.isEmpty()) {
			return null;
		}
		final int right = getInterval(t)[1];
		if (right < 0) {
			return null;
		}
		return keyFrames.get(right);
	}

	private int[] getInterval(final int t) {
		int right = keyFrames.size() - 1;
		if (right < 0) {
			return new int[]{-1, -1};
		}
		final int rightValue = keyFrames.get(right);
		if (t >= rightValue) {
			return new int[]{right, -1};
		}
		if (right == 0) {
			return new int[]{-1, right};
		}
		int left = 0;
		final int leftValue = keyFrames.get(left);
		if (leftValue > t) {
			return new int[]{-1, left};
		}
		if (leftValue == t) {
			return new int[]{left, left + 1};
		}
		while (right - left > 1) {
			final int middle = (right + left) / 2;
			final int middleValue = keyFrames.get(middle);
			if (middleValue < t) {
				left = middle;
			} else if (middleValue > t) {
				right = middle;
			} else {
				left = middle;
				right = left + 1;
			}
		}
		return new int[]{left, right};
	}

	void goTo(final int millis) {
		if (paused) {
			final int actual = player.goTo(millis);
			position.set(millis, actual);
			updatingTimeScale = true;
			timeScl.setSelection(actual);
			updatingTimeScale = false;
			onTimeChanged.run();
		}
	}

	void reset(final int duration, final boolean isPaused, final List<Integer> frames) {
		this.duration = duration;
		timeScl.setMinimum(0);
		timeScl.setMaximum(Math.max(duration, 1));
		paused = isPaused;
		if (paused) {
			goTo(0);
		} else {
			position.set(0, 0);
			updatingTimeScale = true;
			timeScl.setSelection(position.getActual());
			updatingTimeScale = false;
		}
		keyFrames = frames;
		refine = null;
		mediaEnabled = true;
		switchPauseBtnMode();
	}

	void setEnabled(final boolean value) {
		mediaEnabled = value;
		pauseBtn.setEnabled(value);
		timeScl.setEnabled(value);
		rewindBtn.setEnabled(value && paused);
		forwardBtn.setEnabled(value && paused);
		refineLeftBtn.setEnabled(value && paused);
		refineRightBtn.setEnabled(value && paused);
	}

	int getActualTime() {
		return position.getActual();
	}
}
