package net.scriptorium.videocutter;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public final class L10n {

	private static ResourceBundle bundle =
			ResourceBundle.getBundle("net.scriptorium.videocutter.l10n.messages", Locale.ENGLISH);

	public static void init(final String locale) {
		final Locale loc = (locale == null || locale.isBlank()) ? Locale.ENGLISH : Locale.forLanguageTag(locale);
		bundle = ResourceBundle.getBundle("net.scriptorium.videocutter.l10n.messages", loc);
	}

	public static String t(final String key) {
		try {
			return bundle.getString(key);
		} catch (final MissingResourceException e) {
			return key;
		}
	}

	private L10n() {
		//
	}
}
