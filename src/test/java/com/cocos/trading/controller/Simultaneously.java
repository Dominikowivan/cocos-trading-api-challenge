package com.cocos.trading.controller;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class Simultaneously {

	private static final int DEADLINE_SECONDS = 30;

	private Simultaneously() {
	}

	public static <T> List<T> theseTwo(Callable<T> first, Callable<T> second) {
		ExecutorService threads = Executors.newFixedThreadPool(2);
		try {
			return bothOutcomesOf(threads, first, second);
		}
		finally {
			threads.shutdownNow();
		}
	}

	private static <T> List<T> bothOutcomesOf(ExecutorService threads, Callable<T> first, Callable<T> second) {
		CountDownLatch start = new CountDownLatch(1);
		Future<T> one = threads.submit(heldUntil(start, first));
		Future<T> other = threads.submit(heldUntil(start, second));
		start.countDown();
		return List.of(outcomeOf(one), outcomeOf(other));
	}

	private static <T> Callable<T> heldUntil(CountDownLatch start, Callable<T> work) {
		return () -> {
			start.await();
			return work.call();
		};
	}

	private static <T> T outcomeOf(Future<T> running) {
		try {
			return running.get(DEADLINE_SECONDS, TimeUnit.SECONDS);
		}
		catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("interrupted while waiting for a simultaneous request", interrupted);
		}
		catch (ExecutionException | TimeoutException failed) {
			throw new IllegalStateException("a simultaneous request did not answer", failed);
		}
	}

}
