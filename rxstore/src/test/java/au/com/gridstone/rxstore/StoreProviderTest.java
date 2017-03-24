/*
 * Copyright (C) GRIDSTONE 2016
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.com.gridstone.rxstore;

import au.com.gridstone.rxstore.StoreProvider.ValueStore;
import au.com.gridstone.rxstore.StoreProvider.ListStore;
import au.com.gridstone.rxstore.events.ListStoreEvent;
import au.com.gridstone.rxstore.events.StoreEvent;
import au.com.gridstone.rxstore.testutil.RecordingObserver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.reactivex.Notification;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.schedulers.Schedulers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;

public final class StoreProviderTest {
  @Rule public TemporaryFolder tempDir = new TemporaryFolder();

  private StoreProvider storeProvider;

  @Before public void setup() throws IOException {
    storeProvider = StoreProvider.with(tempDir.newFolder("rxStoreTest"))
        .schedulingWith(Schedulers.trampoline())
        .using(new TestConverter());
  }

  @Test(expected = ItemNotFoundException.class)
  public void putAndClear() {
    ValueStore<TestData> store = storeProvider.valueStore("testValue", TestData.class);
    TestData value = new TestData("Test", 1);
    store.put(value);
    assertThat(store.getBlocking()).isEqualTo(value);

    store.clear();
    store.getBlocking();
  }

  @Test(expected = ItemNotFoundException.class)
  public void getOnEmptyThrowsException() {
    ValueStore<TestData> store = storeProvider.valueStore("testValue", TestData.class);
    store.getBlocking();
  }

  @Test public void interactionsWithDeletedFail() {
    ValueStore<TestData> store = storeProvider.valueStore("testValue", TestData.class);
    TestData value = new TestData("Test", 1);
    store.put(value);
    store.delete();

    String expectedMessage = "This store has been deleted!";

    Throwable getError = store.get()
        .toObservable()
        .materialize()
        .filter(new Predicate<Notification<TestData>>() {
          @Override public boolean test(@NonNull Notification<TestData> notification)
              throws Exception {
            return notification.isOnError();
          }
        })
        .map(new Function<Notification<TestData>, Throwable>() {
          @Override
          public Throwable apply(@NonNull Notification<TestData> notification) throws Exception {
            return notification.getError();
          }
        })
        .blockingSingle();

    assertThat(getError).hasMessage(expectedMessage);

    Throwable putError = store.observePut(new TestData("Test2", 2))
        .toObservable()
        .materialize()
        .filter(new Predicate<Notification<TestData>>() {
          @Override public boolean test(@NonNull Notification<TestData> notification)
              throws Exception {
            return notification.isOnError();
          }
        })
        .map(new Function<Notification<TestData>, Throwable>() {
          @Override
          public Throwable apply(@NonNull Notification<TestData> notification) throws Exception {
            return notification.getError();
          }
        })
        .blockingSingle();

    assertThat(putError).hasMessage(expectedMessage);

    Throwable clearError = store.observeClear()
        .toObservable()
        .materialize()
        .filter(new Predicate<Notification<Object>>() {
          @Override public boolean test(@NonNull Notification<Object> notification)
              throws Exception {
            return notification.isOnError();
          }
        })
        .map(new Function<Notification<Object>, Throwable>() {
          @Override
          public Throwable apply(@NonNull Notification<Object> notification) throws Exception {
            return notification.getError();
          }
        })
        .blockingSingle();

    assertThat(clearError).hasMessage(expectedMessage);
  }

  @Test public void updatesTriggerObservable() {
    ValueStore<TestData> store = storeProvider.valueStore("testValue", TestData.class);
    RecordingObserver<StoreEvent> observer = new RecordingObserver<StoreEvent>();
    TestData value = new TestData("Test", 1);

    store.asObservable().subscribe(observer);
    observer.takeSubscribe();

    store.put(value);
    StoreEvent event = observer.takeNext();
    assertThat(event.getType()).isEqualTo(StoreEvent.Type.ITEM_PUT);
    assertThat(event.getItem()).isEqualTo(value);

    TestData value2 = new TestData("Test2", 2);
    store.put(value2);
    event = observer.takeNext();
    assertThat(event.getType()).isEqualTo(StoreEvent.Type.ITEM_PUT);
    assertThat(event.getItem()).isEqualTo(value2);

    store.clear();
    event = observer.takeNext();
    assertThat(event.getType()).isEqualTo(StoreEvent.Type.STORE_CLEARED);

    observer.assertNoMoreEvents();
    store.delete();
    event = observer.takeNext();
    assertThat(event.getType()).isEqualTo(StoreEvent.Type.STORE_DELETED);
    observer.assertOnCompleted();
  }

  @Test public void observePutProducesItem() {
    TestData value = new TestData("Test", 1);
    ValueStore<TestData> store = storeProvider.valueStore("testValue", TestData.class);

    TestData updatedValue = store.observePut(value).timeout(1, SECONDS).blockingGet();
    assertThat(updatedValue).isEqualTo(value);
  }

  @Test public void observeClearProducesResult() {
    TestData value = new TestData("Test", 1);
    ValueStore<TestData> store = storeProvider.valueStore("testValue", TestData.class);

    store.put(value);
    assertThat(store.getBlocking()).isEqualTo(value);

    Object ignore = store.observeClear().timeout(1, SECONDS).blockingGet();
    assertThat(ignore).isNotNull();
  }

  @Test public void observeDeleteProducesItem() {
    TestData value = new TestData("Test", 1);
    ValueStore<TestData> store = storeProvider.valueStore("testValue", TestData.class);

    store.put(value);
    assertThat(store.getBlocking()).isEqualTo(value);

    Object ignore = store.observeDelete().timeout(1, SECONDS).blockingGet();
    assertThat(ignore).isNotNull();
  }

  @Test public void putAndClearList() {
    ListStore<TestData> store = storeProvider.listStore("testValues", TestData.class);
    List<TestData> list = Arrays.asList(new TestData("Test1", 1), new TestData("Test2", 2));
    store.put(list);
    assertThat(store.getBlocking()).isEqualTo(list);

    store.clear();
    assertThat(store.getBlocking()).isEmpty();
  }

  @Test public void getOnEmptyListReturnsEmptyList() {
    ListStore<TestData> store = storeProvider.listStore("testValues", TestData.class);
    assertThat(store.getBlocking()).isEmpty();
  }

  @Test public void addToEmptyList() {
    ListStore<TestData> store = storeProvider.listStore("testValues", TestData.class);
    TestData value = new TestData("Test1", 1);
    store.addToList(value);
    assertThat(store.getBlocking()).containsExactly(value);
  }

  @Test public void addToExistingList() {
    ListStore<TestData> store = storeProvider.listStore("testValues", TestData.class);
    List<TestData> list = Arrays.asList(new TestData("Test1", 1), new TestData("Test2", 2));
    store.put(list);
    assertThat(store.getBlocking()).isEqualTo(list);

    TestData newValue = new TestData("TestAddition", 123);
    store.addToList(newValue);

    List<TestData> listPlusNewValue = new ArrayList<TestData>(list);
    listPlusNewValue.add(newValue);

    assertThat(store.getBlocking()).containsExactlyElementsIn(listPlusNewValue);
  }

  @Test public void removeFromList() {
    ListStore<TestData> store = storeProvider.listStore("testValues", TestData.class);
    List<TestData> list = Arrays.asList(new TestData("Test1", 1), new TestData("Test2", 2));
    store.put(list);

    store.removeFromList(new TestData("Test1", 1));
    assertThat(store.getBlocking()).containsExactly(new TestData("Test2", 2));
  }

  @Test public void removeFromListWithPredicate_oneItemRemoved() {
    ListStore<TestData> store = storeProvider.listStore("testValues", TestData.class);
    List<TestData> list = Arrays.asList(new TestData("Test1", 1), new TestData("Test2", 2));
    store.put(list);

    store.removeFromList(new StoreProvider.RemovePredicateFunc<TestData>() {
      @Override public boolean shouldRemove(TestData value) {
        return value.integer == 1;
      }
    });

    assertThat(store.getBlocking()).containsExactly(new TestData("Test2", 2));
  }

  @Test public void removeFromListWithPredicate_noItemRemoved() {
    ListStore<TestData> store = storeProvider.listStore("testValues", TestData.class);
    List<TestData> list = Arrays.asList(new TestData("Test1", 1), new TestData("Test2", 2));
    store.put(list);

    store.removeFromList(new StoreProvider.RemovePredicateFunc<TestData>() {
      @Override public boolean shouldRemove(TestData value) {
        return value.integer == 1 && !value.string.contains("1");
      }
    });

    assertThat(store.getBlocking()).isEqualTo(list);
  }

  @Test public void removeFromListByIndex() {
    ListStore<TestData> store = storeProvider.listStore("testValues", TestData.class);
    List<TestData> list = Arrays.asList(new TestData("Test1", 1), new TestData("Test2", 2));
    store.put(list);

    store.removeFromList(0);
    assertThat(store.getBlocking()).containsExactly(new TestData("Test2", 2));
  }

  @Test public void replaceInList() {
    ListStore<TestData> store = storeProvider.listStore("testValues", TestData.class);
    List<TestData> list = Arrays.asList(new TestData("Test1", 1), new TestData("Test2", 2));
    store.put(list);

    store.replace(new TestData("Test3", 3), new StoreProvider.ReplacePredicateFunc<TestData>() {
      @Override public boolean shouldReplace(TestData value) {
        return value.integer == 2;
      }
    });

    assertThat(store.getBlocking()).containsExactly(new TestData("Test1", 1),
        new TestData("Test3", 3));
  }

  @Test public void addOrReplace_itemAdded() {
    ListStore<TestData> store = storeProvider.listStore("testValues", TestData.class);
    List<TestData> list = Arrays.asList(new TestData("Test1", 1), new TestData("Test2", 2));
    store.put(list);

    store.addOrReplace(new TestData("Test3", 3),
        new StoreProvider.ReplacePredicateFunc<TestData>() {
          @Override public boolean shouldReplace(TestData value) {
            return value.integer == 3;
          }
        });

    assertThat(store.getBlocking()).containsExactly(new TestData("Test1", 1),
        new TestData("Test2", 2), new TestData("Test3", 3));
  }

  @Test public void addOrReplace_itemReplaced() {
    ListStore<TestData> store = storeProvider.listStore("testValues", TestData.class);
    List<TestData> list = Arrays.asList(new TestData("Test1", 1), new TestData("Test2", 2));
    store.put(list);

    store.replace(new TestData("Test3", 3), new StoreProvider.ReplacePredicateFunc<TestData>() {
      @Override public boolean shouldReplace(TestData value) {
        return value.integer == 2;
      }
    });

    assertThat(store.getBlocking()).containsExactly(new TestData("Test1", 1),
        new TestData("Test3", 3));
  }

  @Test public void updateToListTriggerObservable() {
    ListStore<TestData> store = storeProvider.listStore("testValues", TestData.class);
    RecordingObserver<ListStoreEvent> observer = new RecordingObserver<ListStoreEvent>();
    List<TestData> list = Arrays.asList(new TestData("Test1", 1), new TestData("Test2", 2));
    store.asObservable().subscribe(observer);
    observer.takeSubscribe();

    store.put(list);
    ListStoreEvent event = observer.takeNext();
    assertThat(event.getType()).isEqualTo(ListStoreEvent.Type.LIST_PUT);

    TestData newValue = new TestData("Test3", 3);
    store.addToList(newValue);
    List<TestData> expectedList = new ArrayList<TestData>(list);
    expectedList.add(newValue);
    event = observer.takeNext();
    assertThat(event.getType()).isEqualTo(ListStoreEvent.Type.ITEM_ADDED);
    assertThat(event.getList()).isEqualTo(expectedList);

    store.clear();
    event = observer.takeNext();
    assertThat(event.getType()).isEqualTo(ListStoreEvent.Type.STORE_CLEARED);

    observer.assertNoMoreEvents();
    store.delete();
    event = observer.takeNext();
    assertThat(event.getType()).isEqualTo(ListStoreEvent.Type.STORE_DELETED);
    observer.assertOnCompleted();
  }

  @Test public void interactionsWithDeletedListFail() {
    ListStore<TestData> store = storeProvider.listStore("testValues", TestData.class);
    List<TestData> values = Arrays.asList(new TestData("Test1", 1), new TestData("Test2", 2));
    store.put(values);
    store.delete();

    String expectedMessage = "This store has been deleted!";

    Throwable getError = store.get()
        .toObservable()
        .materialize()
        .filter(new Predicate<Notification<List<TestData>>>() {
          @Override public boolean test(@NonNull Notification<List<TestData>> notification)
              throws Exception {
            return notification.isOnError();
          }
        })
        .map(new Function<Notification<List<TestData>>, Throwable>() {
          @Override
          public Throwable apply(@NonNull Notification<List<TestData>> notification)
              throws Exception {
            return notification.getError();
          }
        })
        .blockingSingle();

    assertThat(getError).hasMessage(expectedMessage);

    Throwable putError = store.observePut(Collections.singletonList(new TestData("Test3", 3)))
        .toObservable()
        .materialize()
        .filter(new Predicate<Notification<List<TestData>>>() {
          @Override public boolean test(@NonNull Notification<List<TestData>> notification)
              throws Exception {
            return notification.isOnError();
          }
        })
        .map(new Function<Notification<List<TestData>>, Throwable>() {
          @Override
          public Throwable apply(@NonNull Notification<List<TestData>> notification)
              throws Exception {
            return notification.getError();
          }
        })
        .blockingSingle();

    assertThat(putError).hasMessage(expectedMessage);

    Throwable clearError = store.observeClear()
        .toObservable()
        .materialize()
        .filter(new Predicate<Notification<List<TestData>>>() {
          @Override public boolean test(@NonNull Notification<List<TestData>> notification)
              throws Exception {
            return notification.isOnError();
          }
        })
        .map(new Function<Notification<List<TestData>>, Throwable>() {
          @Override
          public Throwable apply(@NonNull Notification<List<TestData>> notification)
              throws Exception {
            return notification.getError();
          }
        })
        .blockingSingle();

    assertThat(clearError).hasMessage(expectedMessage);
  }

  @Test public void observePutListProducesItem() {
    ListStore<TestData> store = storeProvider.listStore("testValues", TestData.class);
    List<TestData> list = Arrays.asList(new TestData("Test1", 1), new TestData("Test2", 2));

    List<TestData> updatedList = store.observePut(list).timeout(1, SECONDS).blockingGet();
    assertThat(updatedList).isEqualTo(list);
  }

  @Test public void observeAddToListProducesItem() {
    ListStore<TestData> store = storeProvider.listStore("testValues", TestData.class);
    TestData value = new TestData("Test1", 1);

    List<TestData> modifiedList = store.observeAddToList(value).timeout(1, SECONDS).blockingGet();
    assertThat(modifiedList).containsExactly(value);
  }

  @Test public void observeReplaceInListProducesItem() {
    ListStore<TestData> store = storeProvider.listStore("testValues", TestData.class);
    List<TestData> list = Arrays.asList(new TestData("Test1", 1), new TestData("Test2", 2));
    store.put(list);

    List<TestData> modifiedList = store.observeReplace(new TestData("Test3", 3),
        new StoreProvider.ReplacePredicateFunc<TestData>() {
          @Override public boolean shouldReplace(TestData value) {
            return value.integer == 2;
          }
        }).timeout(1, SECONDS).blockingGet();

    assertThat(modifiedList).containsExactly(new TestData("Test1", 1),
        new TestData("Test3", 3));
  }

  @Test public void observeAddOrReplace_itemAdded() {
    ListStore<TestData> store = storeProvider.listStore("testValues", TestData.class);
    List<TestData> list = Arrays.asList(new TestData("Test1", 1), new TestData("Test2", 2));
    store.put(list);

    List<TestData> modifiedList = store.observeAddOrReplace(new TestData("Test3", 3),
        new StoreProvider.ReplacePredicateFunc<TestData>() {
          @Override public boolean shouldReplace(TestData value) {
            return value.integer == 3;
          }
        }).timeout(1, SECONDS).blockingGet();

    assertThat(modifiedList).containsExactly(new TestData("Test1", 1), new TestData("Test2", 2),
        new TestData("Test3", 3));
  }

  @Test public void observeAddOrReplace_itemReplaced() {
    ListStore<TestData> store = storeProvider.listStore("testValues", TestData.class);
    List<TestData> list = Arrays.asList(new TestData("Test1", 1), new TestData("Test2", 2));
    store.put(list);

    List<TestData> modifiedList = store.observeAddOrReplace(new TestData("Test3", 3),
        new StoreProvider.ReplacePredicateFunc<TestData>() {
          @Override public boolean shouldReplace(TestData value) {
            return value.integer == 2;
          }
        }).timeout(1, SECONDS).blockingGet();

    assertThat(modifiedList).containsExactly(new TestData("Test1", 1),
        new TestData("Test3", 3));
  }

  @Test public void observeRemoveFromListProducesItem() {
    ListStore<TestData> store = storeProvider.listStore("testValues", TestData.class);
    List<TestData> list = Arrays.asList(new TestData("Test1", 1), new TestData("Test2", 2));

    store.put(list);
    assertThat(store.getBlocking()).isEqualTo(list);

    List<TestData> modifiedList = store.observeRemoveFromList(new TestData("Test1", 1))
        .timeout(1, SECONDS)
        .blockingGet();

    assertThat(modifiedList).containsExactly(new TestData("Test2", 2));
  }

  @Test public void observeRemoveFromListByIndexProducesItem() {
    ListStore<TestData> store = storeProvider.listStore("testValues", TestData.class);
    List<TestData> list = Arrays.asList(new TestData("Test1", 1), new TestData("Test2", 2));

    store.put(list);
    assertThat(store.getBlocking()).isEqualTo(list);

    List<TestData> modifiedList = store.observeRemoveFromList(0)
        .timeout(1, SECONDS)
        .blockingGet();

    assertThat(modifiedList).containsExactly(new TestData("Test2", 2));
  }

  @Test public void observeRemoveFromListWithPredicate_oneItemRemoved() {
    ListStore<TestData> store = storeProvider.listStore("testValues", TestData.class);
    List<TestData> list = Arrays.asList(new TestData("Test1", 1), new TestData("Test2", 2));

    store.put(list);
    assertThat(store.getBlocking()).isEqualTo(list);

    List<TestData> modifiedList =
        store.observeRemoveFromList(new StoreProvider.RemovePredicateFunc<TestData>() {
          @Override public boolean shouldRemove(TestData value) {
            return value.integer == 1;
          }
        }).timeout(1, SECONDS).blockingGet();

    assertThat(modifiedList).containsExactly(new TestData("Test2", 2));
  }

  @Test public void observeRemoveFromListWithPredicate_noItemRemoved() {
    ListStore<TestData> store = storeProvider.listStore("testValues", TestData.class);
    List<TestData> list = Arrays.asList(new TestData("Test1", 1), new TestData("Test2", 2));

    store.put(list);
    assertThat(store.getBlocking()).isEqualTo(list);

    List<TestData> modifiedList =
        store.observeRemoveFromList(new StoreProvider.RemovePredicateFunc<TestData>() {
          @Override public boolean shouldRemove(TestData value) {
            return value.integer == 1 && !value.string.contains("1");
          }
        }).timeout(1, SECONDS).blockingGet();

    assertThat(modifiedList).isEqualTo(list);
  }

  @Test public void observeClearListProducesItem() {
    ListStore<TestData> store = storeProvider.listStore("testValues", TestData.class);
    List<TestData> list = Arrays.asList(new TestData("Test1", 1), new TestData("Test2", 2));

    store.put(list);
    assertThat(store.getBlocking()).isEqualTo(list);

    List<TestData> updatedList = store.observeClear().timeout(1, SECONDS).blockingGet();
    assertThat(updatedList).isEmpty();
  }

  @Test public void observeDeleteListProducesItem() {
    ListStore<TestData> store = storeProvider.listStore("testValues", TestData.class);
    List<TestData> list = Arrays.asList(new TestData("Test1", 1), new TestData("Test2", 2));

    store.put(list);
    assertThat(store.getBlocking()).isEqualTo(list);

    List<TestData> updatedList = store.observeClear().timeout(1, SECONDS).blockingGet();
    assertThat(updatedList).isEmpty();
  }

  private static class TestData {
    final String string;
    final int integer;

    TestData(String string, int integer) {
      this.string = string;
      this.integer = integer;
    }

    @Override public boolean equals(Object o) {
      if (!(o instanceof TestData)) {
        return false;
      }

      TestData otherData = (TestData) o;

      if (string != null) {
        return string.equals(otherData.string) && integer == otherData.integer;
      }

      return otherData.string == null && integer == otherData.integer;
    }

    @Override public String toString() {
      return string + "," + integer;
    }

    static TestData fromString(String string) {
      String[] splitString = string.split(",");
      return new TestData(splitString[0], Integer.parseInt(splitString[1]));
    }
  }

  private static class TestConverter implements Converter {
    @Override public <T> void write(T data, Type type, File file) throws ConverterException {
      try {
        Writer writer = new FileWriter(file);

        if (data == null) {
          writer.write("");
          writer.close();
        } else if (data instanceof TestData) {
          writer.write(data.toString());
          writer.close();
        } else if (data instanceof List) {
          @SuppressWarnings("unchecked") List<TestData> dataList = (List<TestData>) data;

          for (int i = 0, n = dataList.size(); i < n; i++) {
            if (i != 0) {
              // Separate each TestData instance by a "~" character.
              writer.write("~");
            }

            writer.write(dataList.get(i).toString());
          }

          writer.close();
        }
      } catch (Exception e) {
        throw new ConverterException(e);
      }
    }

    @Override public <T> T read(File file, Type type) throws ConverterException {
      try {
        String storedString = new BufferedReader(new FileReader(file)).readLine();

        if (isBlank(storedString)) return null;

        if (type instanceof StoreProvider.ListType) {
          // Stored string contains each TestData separated by a "~" character.
          String[] splitString = storedString.split("~");
          List<TestData> list = new ArrayList<TestData>(splitString.length);

          for (String itemString : splitString) {
            list.add(TestData.fromString(itemString));
          }

          //noinspection unchecked
          return (T) list;
        } else {
          //noinspection unchecked
          return (T) TestData.fromString(storedString);
        }
      } catch (Exception e) {
        throw new ConverterException(e);
      }
    }
  }

  private static boolean isBlank(String string) {
    return string == null || string.trim().length() == 0;
  }
}
