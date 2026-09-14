package org.rapla.test.util;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Test-only proxy that implements any single view interface and records
 * every call into a {@link List} of {@link Call}s. Companion to
 * {@link HeadlessPresenterTestSupport} — lets a tier-2 presenter test
 * assert what the presenter asked the view to do without booting Swing.
 * <p>
 * Default behaviour for non-void methods: returns Java's default
 * (null / 0 / false). Override per call site by registering a stub via
 * {@link #stub(String, Object)}.
 *
 * <pre>{@code
 *   RecordingView<ReservationView> view = RecordingView.of(ReservationView.class);
 *   presenter.setView(view.proxy());
 *   ...
 *   view.assertCalled("showWarning");
 *   view.lastCall("showWarning").arg(0);  // → first argument of last showWarning(...)
 * }</pre>
 */
public final class RecordingView<V>
{
    /** One recorded view-method invocation. */
    public record Call(String method, Object[] args)
    {
        public Object arg(int index) { return args[index]; }
        @Override public String toString() { return method + Arrays.toString(args); }
    }

    private final V proxy;
    private final List<Call> calls = new ArrayList<>();
    private final java.util.Map<String, Object> stubs = new java.util.HashMap<>();

    @SuppressWarnings("unchecked")
    public static <V> RecordingView<V> of(Class<V> viewInterface)
    {
        if (!viewInterface.isInterface())
        {
            throw new IllegalArgumentException(viewInterface.getName() + " must be an interface");
        }
        return new RecordingView<>(viewInterface);
    }

    @SuppressWarnings("unchecked")
    private RecordingView(Class<V> viewInterface)
    {
        this.proxy = (V) Proxy.newProxyInstance(
                viewInterface.getClassLoader(),
                new Class<?>[] { viewInterface },
                new Handler());
    }

    /** The recording proxy. Hand this to the presenter under test. */
    public V proxy()
    {
        return proxy;
    }

    /** All calls in the order they happened. */
    public List<Call> calls()
    {
        return List.copyOf(calls);
    }

    /** Filter recorded calls by method name. */
    public List<Call> callsFor(String methodName)
    {
        return calls.stream().filter(c -> c.method.equals(methodName)).collect(Collectors.toList());
    }

    public Call lastCall(String methodName)
    {
        List<Call> list = callsFor(methodName);
        if (list.isEmpty())
        {
            throw new AssertionError("no recorded call to " + methodName + "; recorded=" + calls);
        }
        return list.get(list.size() - 1);
    }

    public Optional<Call> firstCall(String methodName)
    {
        return callsFor(methodName).stream().findFirst();
    }

    /** Assert that {@code methodName} was called at least once. */
    public void assertCalled(String methodName)
    {
        if (callsFor(methodName).isEmpty())
        {
            throw new AssertionError("expected at least one call to " + methodName
                    + " but recorded calls were: " + calls);
        }
    }

    /** Assert that {@code methodName} was never called. */
    public void assertNeverCalled(String methodName)
    {
        List<Call> list = callsFor(methodName);
        if (!list.isEmpty())
        {
            throw new AssertionError("expected no call to " + methodName
                    + " but recorded: " + list);
        }
    }

    public int callCount(String methodName)
    {
        return callsFor(methodName).size();
    }

    public void clearCalls()
    {
        calls.clear();
    }

    /**
     * Register a canned return value for {@code methodName}. The proxy
     * returns this value for every subsequent invocation of that method.
     * Java's default (null / 0 / false) is used when no stub is set.
     */
    public RecordingView<V> stub(String methodName, Object value)
    {
        stubs.put(methodName, value);
        return this;
    }

    private final class Handler implements InvocationHandler
    {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
        {
            // Object methods aren't recorded; behave plausibly.
            switch (method.getName())
            {
                case "equals":   return args != null && args[0] == proxy;
                case "hashCode": return System.identityHashCode(proxy);
                case "toString": return "RecordingView{calls=" + calls.size() + "}";
                default: /* fall through */
            }
            calls.add(new Call(method.getName(), args == null ? new Object[0] : args));
            if (stubs.containsKey(method.getName()))
            {
                return stubs.get(method.getName());
            }
            return defaultFor(method.getReturnType());
        }
    }

    private static Object defaultFor(Class<?> type)
    {
        if (!type.isPrimitive())   return null;
        if (type == boolean.class) return false;
        if (type == void.class)    return null;
        if (type == char.class)    return (char) 0;
        // Numeric primitives — let Java's autoboxing handle conversion.
        return 0;
    }
}
