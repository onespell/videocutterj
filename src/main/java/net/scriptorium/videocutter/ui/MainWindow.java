package net.scriptorium.videocutter.ui;

import net.scriptorium.videocutter.L10n;
import net.scriptorium.videocutter.Session;
import net.scriptorium.videocutter.Settings;
import net.scriptorium.videocutter.UncheckedException;
import net.scriptorium.videocutter.media.Analysis;
import net.scriptorium.videocutter.media.FrameSink;
import net.scriptorium.videocutter.media.MediaInfo;
import net.scriptorium.videocutter.media.Player;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class MainWindow {

	private final Logger log = LogManager.getLogger(getClass());

	private final Display display;

	private final Shell shell;

	private final Session session;

	private final Icons icons;

	private final Player player;

	private final Viewer viewer;

	private final ToolBox toolBox;

	public MainWindow(final Display display) {
		this.display = display;
		final Settings settings = Settings.instance();
		session = new Session(settings.initialDir());
		icons = new Icons(display);
		{
			shell = new Shell(display);
			shell.setText("videocutter");
			{
				final GridLayout layout = new GridLayout(2, false);
				layout.marginWidth = 4;
				layout.marginHeight = 4;
				shell.setLayout(layout);
			}
			shell.setMenuBar(createMenu());
			shell.setSize(1100, 700);
		}
		{
			// viewer needs player; player needs viewer as frame sink — create player with deferred sink
			final Viewer[] viewerRef = new Viewer[1];
			player = new Player(display, new FrameSink() {
				@Override
				public void present(final ImageData image, final int w, final int h) {
					if (viewerRef[0] != null) {
						viewerRef[0].present(image, w, h);
					}
				}

				@Override
				public void clear() {
					if (viewerRef[0] != null) {
						viewerRef[0].clear();
					}
				}
			}, this::onPlayerTime);
			viewer = new Viewer(shell, icons, player, () -> {
				//
			});
			viewer.frame.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			viewerRef[0] = viewer;
		}
		{
			toolBox = new ToolBox(shell, session, viewer.mediaBar::requestFocus);
			final GridData gridData = new GridData(SWT.FILL, SWT.FILL, false, true);
			gridData.widthHint = 320;
			toolBox.frame.setLayoutData(gridData);
		}
		toolBox.jobBox.setOnJump(ms -> {
			final int p = viewer.mediaBar.goTo(ms).getActual();
			toolBox.shotBox.setTime(p);
			toolBox.clipBox.setTime(p);
		});
		bindKeys();
		shell.addShellListener(new ShellAdapter() {

			@Override
			public void shellClosed(final ShellEvent event) {
				event.doit = confirmQuit();
				if (event.doit) {
					shutdown();
				}
			}
		});
		shell.open();
	}

	private Menu createMenu() {
		final Menu menuBar = new Menu(shell, SWT.BAR);
		final Menu fileMenu;
		{
			fileMenu = new Menu(shell, SWT.DROP_DOWN);
			final MenuItem item = new MenuItem(menuBar, SWT.CASCADE);
			item.setText(L10n.t("file"));
			item.setMenu(fileMenu);
		}
		{
			final MenuItem item = new MenuItem(fileMenu, SWT.PUSH);
			item.setText(L10n.t("open") + "\tCtrl+O");
			item.setAccelerator(SWT.MOD1 | 'O');
			item.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(final SelectionEvent e) {
					openFile();
				}
			});
		}
		{
			final MenuItem item = new MenuItem(fileMenu, SWT.PUSH);
			item.setText(L10n.t("close") + "\tCtrl+W");
			item.setAccelerator(SWT.MOD1 | 'W');
			item.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(final SelectionEvent e) {
					player.closeSession();
					viewer.clear();
					viewer.mediaBar.setEnabled(false);
					toolBox.setEnabled(false);
				}
			});
		}
		{
			final MenuItem item = new MenuItem(fileMenu, SWT.PUSH);
			item.setText(L10n.t("quit") + "\tCtrl+Q");
			item.setAccelerator(SWT.MOD1 | 'Q');
			item.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(final SelectionEvent e) {
					if (confirmQuit()) {
						shutdown();
						shell.dispose();
					}
				}
			});
		}
		return menuBar;
	}

	private void bindKeys() {
		display.addFilter(SWT.KeyDown, e -> {
			if (!shell.isDisposed()) {
				if (e.widget instanceof org.eclipse.swt.widgets.Text) {
					return;
				}
				switch (e.keyCode) {
					case SWT.ARROW_DOWN:
						viewer.mediaBar.rewind();
						e.doit = false;
						break;
					case SWT.ARROW_UP:
						viewer.mediaBar.forward();
						e.doit = false;
						break;
					case SWT.ARROW_LEFT:
						viewer.mediaBar.refineLeft();
						e.doit = false;
						break;
					case SWT.ARROW_RIGHT:
						viewer.mediaBar.refineRight();
						e.doit = false;
						break;
					default:
				}
			}
		});
	}

	private void openFile() {
		final FileDialog dialog = new FileDialog(shell, SWT.OPEN);
		dialog.setFilterPath(session.workingDir().toString());
		dialog.setFilterNames(Settings.fileFilterNames());
		dialog.setFilterExtensions(Settings.fileFilterExtensions());
		final String chosen = dialog.open();
		if (StringUtils.isNotBlank(chosen)) {
			final Path path = Path.of(chosen);
			session.setWorkingDir((path.getParent() == null) ? session.workingDir() : path.getParent());
			loadFile(path);
		}
	}

	public void loadFile(final Path filePath) {
		player.closeSession();
		viewer.clear();
		viewer.mediaBar.setEnabled(false);
		toolBox.setEnabled(false);
		final WaitSplash splash = new WaitSplash(shell, L10n.t("loading"));
		splash.show();
		CompletableFuture.supplyAsync(() -> {
			try {
				return Analysis.mediaInfo(filePath);
			} catch (final Exception e) {
				throw new UncheckedException(e);
			}
		}).whenComplete((info, error) -> display.asyncExec(() -> {
			splash.close();
			if (!shell.isDisposed()) {
				if (error != null) {
					final Throwable cause = error.getCause() == null ? error : error.getCause();
					Dialogs.error(shell, L10n.t("loadError") + ": " + cause.getMessage());
					return;
				}
				applyLoadedFile(filePath, info);
			}
		}));
	}

	private void applyLoadedFile(final Path filePath, final MediaInfo info) {
		session.setFilePath(filePath);
		viewer.setSize(info.getWidth(), info.getHeight());
		CompletableFuture.runAsync(() -> {
			try {
				player.load(filePath.toString());
			} catch (final Exception e) {
				throw new UncheckedException(e);
			}
		}).whenComplete((ignored, error) -> display.asyncExec(() -> {
			if (shell.isDisposed()) {
				return;
			}
			if (error != null) {
				final Throwable cause = (error.getCause() == null) ? error : error.getCause();
				Dialogs.error(shell, L10n.t("loadError") + ": " + cause.getMessage());
				return;
			}
			viewer.mediaBar.reset(info.getDurationMillis(), player.isPaused(), info.getKeyFrames());
			toolBox.clipBox.reset(info.getFormat(), info.getDurationMillis(), info.getSizes(), info.getVideo(), info.getAudio());
			toolBox.shotBox.reset();
			toolBox.jobBox.reset();
			viewer.mediaBar.setEnabled(true);
			toolBox.setEnabled(true);
			log.info("open {}", filePath);
			// viewer.mediaBar.setTime(0);
			final int actualTime = viewer.mediaBar.getActualTime();
			toolBox.shotBox.setTime(actualTime);
			toolBox.clipBox.setTime(actualTime);
			if (info.getKeyFrames().isEmpty()) {
				Dialogs.error(shell, L10n.t("noKeyFrames"));
			}
		}));
	}

	private boolean confirmQuit() {
		if (!toolBox.jobBox.isEmpty()) {
			return Dialogs.confirm(shell, "", L10n.t("confirmExitQuestion"));
		}
		return true;
	}

	public boolean isDisposed() {
		return shell.isDisposed();
	}

	private void onPlayerTime(final int ms) {
		viewer.mediaBar.setTime(ms);
		toolBox.shotBox.setTime(ms);
		toolBox.clipBox.setTime(ms);
	}

	private void shutdown() {
		player.closeSession();
		icons.dispose();
	}
}
