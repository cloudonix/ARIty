package io.cloudonix.arity.helpers;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class Lazy<T> {

	AtomicReference<T> value;
	Supplier<T> resolver;

	public Lazy(Supplier<T> lazyProvider) {
		resolver = () -> {
			T witness = value.compareAndExchange(null, lazyProvider.get());
			resolver = value::get;
			return Objects.nonNull(witness) ? witness : resolver.get();
		};
	}

	public T get() {
		return resolver.get();
	}

}
