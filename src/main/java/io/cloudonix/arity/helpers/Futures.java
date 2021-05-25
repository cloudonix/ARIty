package io.cloudonix.arity.helpers;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Futures {

	static class Thrower {
		static Throwable except;

		Thrower() throws Throwable {
			throw except;
		}

		@SuppressWarnings("deprecation")
		public static void spit(Throwable t) {
			except = t;
			try {
				Thrower.class.newInstance();
			} catch (InstantiationException | IllegalAccessException e) {
			}
		}
	}

	@FunctionalInterface
	public interface ThrowingFunction<U,V> {
		V apply(U value) throws Throwable;
	}

	public static <T, E extends Throwable> Function<Throwable, ? extends T> on(Class<E> errType,
			ThrowingFunction<E, ? extends T> fn) {
		return t -> {
			Throwable cause = t;
			while (Objects.nonNull(cause)) {
				if (errType.isInstance(cause))
					break;
				cause = cause.getCause();
			}
			if (Objects.isNull(cause))
				Thrower.spit(t);
			@SuppressWarnings("unchecked")
			E e = (E) cause;
			try {
				return fn.apply(e);
			} catch (Throwable e1) {
				Thrower.spit(e1);
				return null; // won't actually run, but java can't detect that spit() throws
			}
		};
	}

	/**
	 * Executes CompletableFuture's allOf on a stream instead of an array
	 *
	 * @param <G> Value type of the stream's promises
	 * @param futures
	 *            the stream to execute allOf on
	 * @return a CompletableFuture that will complete when all completableFutures in
	 *         the stream are completed
	 */
	public static <G> CompletableFuture<Void> allOf(Stream<CompletableFuture<G>> futures) {
		return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
	}

	private static class Holder<T> {
		T value;
		public Holder(T val) {
			value = val;
		}
	}

	/**
	 * wait for all of the futures to complete and return a list of their results
	 *
	 * @param <G> Value type of the stream's promises
	 * @param futures
	 *            the stream to execute allOf on
	 * @return a CompletableFuture that will complete when all completableFutures in
	 *         the stream are completed and contains a list of their results
	 */
	public static <G> CompletableFuture<List<G>> resolveAll(Stream<CompletableFuture<G>> futures) {
		ConcurrentSkipListMap<Integer, Holder<G>> out = new ConcurrentSkipListMap<>();
		AtomicInteger i = new AtomicInteger(0);
		return allOf(futures.map(f -> {
			int index = i.getAndIncrement();
			return f.thenAccept(v -> out.put(index, new Holder<>(v)));
		}))
		.thenApply(v -> out.values().stream().map(h -> h.value).collect(Collectors.toList()));
	}

	/**
	 * Create a stream collector to help resolve a stream of promises for values to a promise to a list of values
	 * @param <G> The type of futures resolved by this stream
	 * @return a collector that collects a stream of futures to a future list
	 */
	public static <G> Collector<CompletableFuture<G>, Collection<CompletableFuture<G>>, CompletableFuture<List<G>>> resolvingCollector() {
		return new Collector<CompletableFuture<G>, Collection<CompletableFuture<G>>, CompletableFuture<List<G>>>(){
			@Override
			public Supplier<Collection<CompletableFuture<G>>> supplier() {
				return ConcurrentLinkedQueue<CompletableFuture<G>>::new;
			}

			@Override
			public BiConsumer<Collection<CompletableFuture<G>>, CompletableFuture<G>> accumulator() {
				return Collection::add;
			}

			@Override
			public BinaryOperator<Collection<CompletableFuture<G>>> combiner() {
				return (a,b) -> { a.addAll(b); return a; };
			}

			@Override
			public Function<Collection<CompletableFuture<G>>, CompletableFuture<List<G>>> finisher() {
				return q -> resolveAll(q.stream());
			}

			@Override
			public Set<Characteristics> characteristics() {
				return Collections.singleton(Characteristics.CONCURRENT);
			}

		};
	}
	
	/**
	 * Generate a CompletableFuture composition function that delays the return of an arbitrary value
	 * @param <T> Value type of the promise
	 * @param delay delay in milliseconds to impart on the value
	 * @return A function to be used in @{link {@link CompletableFuture#thenCompose(Function)}
	 */
	public static <T> Function<T, CompletableFuture<T>> delay(long delay) {
		CompletableFuture<T> future = new CompletableFuture<>();
		return value -> {
			Timers.schedule(() -> future.complete(value), delay);
			return future;
		};
	}

}
