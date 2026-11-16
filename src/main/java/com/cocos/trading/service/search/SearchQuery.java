package com.cocos.trading.service.search;

public final class SearchQuery {

	private final String text;

	private SearchQuery(String text) {
		this.text = text;
	}

	public static SearchQuery of(String raw, SearchLimits limits) {
		String trimmed = trimmed(raw);
		rejectBlank(trimmed);
		rejectTooLong(trimmed, limits);
		return new SearchQuery(trimmed);
	}

	public String text() {
		return text;
	}

	private static String trimmed(String raw) {
		return raw == null ? "" : raw.trim();
	}

	private static void rejectBlank(String trimmed) {
		if (trimmed.isEmpty()) {
			throw new InvalidSearchRequestException("The search query must not be blank");
		}
	}

	private static void rejectTooLong(String trimmed, SearchLimits limits) {
		if (trimmed.length() > limits.maxQueryLength()) {
			throw new InvalidSearchRequestException(
					"The search query must be at most " + limits.maxQueryLength() + " characters");
		}
	}

}
