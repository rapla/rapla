package org.rapla.scheduler;

import org.reactivestreams.Subscriber;

public interface Subject<T> extends Observable<T>,Subscriber<T> {
}
