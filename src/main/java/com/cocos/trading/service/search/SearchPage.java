package com.cocos.trading.service.search;

public final class SearchPage {

	private final int number;

	private final int size;

	private SearchPage(int number, int size) {
		this.number = number;
		this.size = size;
	}

	public static SearchPage of(int number, Integer requestedSize, SearchLimits limits) {
		int size = requestedSize == null ? limits.defaultPageSize() : requestedSize;
		rejectNegative(number);
		rejectSizeOutsideLimits(size, limits);
		return new SearchPage(number, size);
	}

	public int number() {
		return number;
	}

	public int size() {
		return size;
	}

	private static void rejectNegative(int number) {
		if (number < 0) {
			throw new InvalidSearchRequestException("The page number must not be negative");
		}
	}

	private static void rejectSizeOutsideLimits(int size, SearchLimits limits) {
		if (size < 1 || size > limits.maxPageSize()) {
			throw new InvalidSearchRequestException("The page size must be between 1 and " + limits.maxPageSize());
		}
	}

}
