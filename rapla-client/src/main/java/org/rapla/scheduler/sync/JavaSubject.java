package org.rapla.scheduler.sync;

import io.reactivex.rxjava3.processors.FlowableProcessor;
import org.rapla.scheduler.Subject;
import org.reactivestreams.Subscription;

import java.util.concurrent.Executor;


public class JavaSubject<T> extends  JavaObservable<T> implements Subject<T> {

    public JavaSubject(FlowableProcessor<T> observable, Executor executor) {
        super(observable, executor);
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        ((FlowableProcessor<T>)observable).onSubscribe(subscription);
    }

    @Override
    public void onNext(T t) {
        ((FlowableProcessor<T>)observable).onNext(t);
    }

    @Override
    public void onError(Throwable e) {
        ((FlowableProcessor<T>)observable).onError(e);
    }

    @Override
    public void onComplete() {
        ((FlowableProcessor<T>)observable).onComplete();
    }
}
