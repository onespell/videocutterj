package net.scriptorium.videocutter.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import net.scriptorium.videocutter.FrameSize;
import net.scriptorium.videocutter.L10n;
import net.scriptorium.videocutter.MediaStream;
import net.scriptorium.videocutter.Settings;
import net.scriptorium.videocutter.TimeUtil;
import net.scriptorium.videocutter.job.ClipJob;
import net.scriptorium.videocutter.job.Job;
import net.scriptorium.videocutter.media.Analysis;

final class ClipBox {

    final Composite frame;
    private final Button aBtn;
    private final Button bBtn;
    private final Label aLbl;
    private final Label bLbl;
    private final Combo sizeBox;
    private final Combo audioBox;
    private final Combo formatBox;
    private final Button reencodeChk;
    private final Button cutBtn;

    private String defaultSize = "";
    private final Map<String, FrameSize> sizes = new LinkedHashMap<>();
    private MediaStream videoStream;
    private String defaultAudio = "";
    private final Map<String, MediaStream> audioStreams = new LinkedHashMap<>();
    private int duration = -1;
    private int time = -1;
    private int a = -1;
    private int b = -1;

    ClipBox(Composite parent, java.util.function.Consumer<Job> addJob, Runnable onSelect) {
        frame = new Composite(parent, SWT.BORDER);
        frame.setLayout(new GridLayout(1, false));

        Composite frameA = new Composite(frame, SWT.NONE);
        frameA.setLayout(new GridLayout(2, false));
        frameA.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        aBtn = new Button(frameA, SWT.PUSH);
        aBtn.setText("A [");
        aBtn.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                setA(time);
            }
        });
        aLbl = new Label(frameA, SWT.NONE);
        aLbl.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Composite frameB = new Composite(frame, SWT.NONE);
        frameB.setLayout(new GridLayout(2, false));
        frameB.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        bBtn = new Button(frameB, SWT.PUSH);
        bBtn.setText("] B");
        bBtn.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                setB(time);
            }
        });
        bLbl = new Label(frameB, SWT.NONE);
        bLbl.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        sizeBox = new Combo(frame, SWT.READ_ONLY | SWT.DROP_DOWN);
        sizeBox.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        sizeBox.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onSizeSelect();
                onSelect.run();
            }
        });

        audioBox = new Combo(frame, SWT.READ_ONLY | SWT.DROP_DOWN);
        audioBox.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        audioBox.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onSelect.run();
            }
        });

        formatBox = new Combo(frame, SWT.READ_ONLY | SWT.DROP_DOWN);
        for (String format : Settings.videoFormats()) {
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

        reencodeChk = new Button(frame, SWT.CHECK);
        reencodeChk.setText(L10n.t("reencode"));

        cutBtn = new Button(frame, SWT.PUSH);
        cutBtn.setText(L10n.t("cut"));
        cutBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        cutBtn.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (a >= 0) {
                    addJob.accept(newJob());
                }
            }
        });

        setEnabled(false);
    }

    private ClipJob newJob() {
        FrameSize pSize = null;
        String selectedSize = sizeBox.getText();
        if (!selectedSize.equals(defaultSize) || reencodeChk.getSelection()) {
            pSize = sizes.get(selectedSize);
        }
        MediaStream pAudio = null;
        String selectedAudio = audioBox.getText();
        if (!selectedAudio.isEmpty() && !selectedAudio.equals(defaultAudio)) {
            pAudio = audioStreams.get(selectedAudio);
        }
        return new ClipJob(a, b, formatBox.getText(), pSize, videoStream, pAudio);
    }

    private void setA(int value) {
        a = value;
        aLbl.setText(TimeUtil.toTimeCode(a));
        aLbl.getParent().layout(true);
        if (b < a) {
            setB(duration);
        }
    }

    private void setB(int value) {
        b = value;
        bLbl.setText(TimeUtil.toTimeCode(b));
        bLbl.getParent().layout(true);
        if (b < a) {
            setA(0);
        }
    }

    void setTime(int value) {
        time = value;
    }

    private void onSizeSelect() {
        if (sizeBox.getText().equals(defaultSize)) {
            reencodeChk.setSelection(false);
            reencodeChk.setEnabled(true);
        } else {
            reencodeChk.setSelection(true);
            reencodeChk.setEnabled(false);
        }
    }

    void setEnabled(boolean value) {
        aBtn.setEnabled(value);
        bBtn.setEnabled(value);
        sizeBox.setEnabled(value);
        audioBox.setEnabled(value);
        formatBox.setEnabled(value);
        reencodeChk.setEnabled(value);
        cutBtn.setEnabled(value);
    }

    void reset(String format, int durationMillis, List<FrameSize> frameSizes, MediaStream video, List<MediaStream> audio) {
        a = -1;
        aLbl.setText("");
        b = -1;
        bLbl.setText("");
        time = -1;
        duration = durationMillis;
        selectFormat(format);
        setSizes(frameSizes);
        videoStream = video;
        setAudioStreams(audio);
    }

    private void selectFormat(String format) {
        int idx = 0;
        for (int i = 0; i < formatBox.getItemCount(); i++) {
            if (formatBox.getItem(i).equalsIgnoreCase(format)) {
                idx = i;
                break;
            }
        }
        formatBox.select(idx);
    }

    private void setSizes(List<FrameSize> value) {
        sizes.clear();
        sizeBox.removeAll();
        defaultSize = "";
        for (FrameSize size : value) {
            String caption = size.toString();
            sizes.put(caption, size);
            sizeBox.add(caption);
            if (defaultSize.isEmpty()) {
                defaultSize = caption;
            }
        }
        if (sizeBox.getItemCount() > 0) {
            sizeBox.select(0);
        }
        reencodeChk.setSelection(false);
        reencodeChk.setEnabled(true);
    }

    private void setAudioStreams(List<MediaStream> value) {
        audioStreams.clear();
        audioBox.removeAll();
        defaultAudio = "";
        MediaStream nosound = MediaStream.audio("", Analysis.noSoundCaption());
        audioStreams.put(nosound.caption(), nosound);
        audioBox.add(nosound.caption());
        for (MediaStream stream : value) {
            audioStreams.put(stream.caption(), stream);
            audioBox.add(stream.caption());
            if (defaultAudio.isEmpty()) {
                defaultAudio = stream.caption();
            }
        }
        if (defaultAudio.isEmpty()) {
            audioBox.select(0);
        } else {
            int idx = audioBox.indexOf(defaultAudio);
            audioBox.select(Math.max(idx, 0));
        }
    }
}
