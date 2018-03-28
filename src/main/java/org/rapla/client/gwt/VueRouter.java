package org.rapla.client.gwt;

import jsinterop.annotations.JsType;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

@JsType(isNative = true,
        name = "$router",
        namespace = "raplaVue")
public class VueRouter {

  /**
   * This method pushes a new entry into the history stack, so when the user clicks the browser back button they will be taken to the previous URL.
   * <p>Examples:</p>
   * <pre>
   *   // literal string path
   * router.push('home')
   *
   * // object
   * router.push({ path: 'home' })
   *
   * // named route
   * router.push({ name: 'user', params: { userId: 123 }})
   *
   * // with query, resulting in /register?plan=private
   * router.push({ path: 'register', query: { plan: 'private' }})
   * </pre>
   *
   * @param onComplete callback if change completed
   * @param onAbort    callback if change aborted
   */
  public static native void push(
      String location,
      @Nullable Consumer<Void> onComplete,
      @Nullable Consumer<Void> onAbort
  );

  /**
   * It acts like router.push, the only difference is that it navigates without pushing a new history entry, as its name suggests - it replaces the current entry
   *
   * @param location
   * @param onComplete
   * @param onAbort
   */
  public static native void replace(
      String location,
      @Nullable Consumer<Void> onComplete,
      @Nullable Consumer<Void> onAbort
  );

  /**
   * This method takes a single integer as parameter that indicates by how many steps to go forwards or go backwards
   * in the history stack, similar to window.history.go(n).
   * <p> Examples:</p>
   * <p>
   * <pre>
   * // go forward by one record, the same as history.forward()
   * router.go(1)
   *
   * // go back by one record, the same as history.back()
   * router.go(-1)
   *
   * // go forward by 3 records
   * router.go(3)
   *
   * // fails silently if there aren't that many records.
   * router.go(-100)
   * router.go(100)
   * </pre>
   */
  public static native void go(int n);

}
