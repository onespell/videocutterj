package net.scriptorium.videocutter;

import net.scriptorium.videocutter.ui.MainWindow;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.swt.widgets.Display;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Videocutter {

	public static void main(final String[] args) {
		final Path confPath = getConfPath();
		final Path log4j = confPath.resolve("log4j2.xml");
		if (Files.isRegularFile(log4j)) {
			System.setProperty("log4j2.configurationFile", log4j.toUri().toString());
		}
		final Settings settings = Settings.load(confPath);
		String fileArg;
		{
			fileArg = null;
			for (final String arg : args) {
				if (!arg.startsWith("-")) {
					fileArg = arg;
				}
			}
		}
		L10n.init(settings.locale());
		final Display display = new Display();
		final MainWindow window = new MainWindow(display);
		if (StringUtils.isNotBlank(fileArg)) {
			window.loadFile(Path.of(fileArg).toAbsolutePath());
		}
		while (!window.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
		display.dispose();
	}

	private static Path getConfPath() {
		final Path appDir = Util.getPath(Videocutter.class).getParent();
		return appDir.resolve("conf");
	}
}
