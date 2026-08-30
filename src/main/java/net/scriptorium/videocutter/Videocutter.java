package net.scriptorium.videocutter;

import net.scriptorium.videocutter.ui.MainWindow;
import org.eclipse.swt.widgets.Display;

import java.nio.file.Path;

public final class Videocutter {

	public static void main(final String[] args) {
		Util.init();
		final Path filePath = Util.getPathArgument(args);
		L10n.init(Settings.instance().locale());
		final Display display = new Display();
		final MainWindow window = new MainWindow(display);
		if (filePath != null) {
			window.loadFile(filePath);
		}
		while (!window.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
		display.dispose();
	}
}
