/*
 * Copyright 2016 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm;

import android.os.Handler;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.realm.entities.AllTypes;
import io.realm.entities.Cat;
import io.realm.entities.Dog;
import io.realm.entities.DogPrimaryKey;
import io.realm.entities.Owner;
import io.realm.entities.PrimaryKeyAsBoxedByte;
import io.realm.entities.PrimaryKeyAsBoxedInteger;
import io.realm.entities.PrimaryKeyAsBoxedLong;
import io.realm.entities.PrimaryKeyAsBoxedShort;
import io.realm.entities.PrimaryKeyAsString;
import io.realm.internal.log.RealmLog;
import io.realm.rule.RunInLooperThread;
import io.realm.rule.RunTestInLooperThread;
import io.realm.rule.TestRealmConfigurationFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class DynamicRealmTests {

    @Rule
    public final RunInLooperThread looperThread = new RunInLooperThread();

    @Rule
    public final TestRealmConfigurationFactory configFactory = new TestRealmConfigurationFactory();

    private RealmConfiguration defaultConfig;
    private DynamicRealm realm;

    @Before
    public void setUp() {
        defaultConfig = configFactory.createConfiguration();

        // Initialize schema. DynamicRealm will not do that, so let a normal Realm create the file first.
        Realm.getInstance(defaultConfig).close();
        realm = DynamicRealm.getInstance(defaultConfig);
    }

    @After
    public void tearDown() {
        if (realm != null) {
            realm.close();
        }
    }

    private void populateTestRealm(DynamicRealm realm, int objects) {
        boolean autoRefreshEnabled = realm.isAutoRefresh();
        if (autoRefreshEnabled) {
            realm.setAutoRefresh(false);
        }
        realm.beginTransaction();
        realm.deleteAll();
        for (int i = 0; i < objects; ++i) {
            DynamicRealmObject allTypes = realm.createObject(AllTypes.CLASS_NAME);
            allTypes.setBoolean(AllTypes.FIELD_BOOLEAN, (i % 3) == 0);
            allTypes.setBlob(AllTypes.FIELD_BINARY, new byte[]{1, 2, 3});
            allTypes.setDate(AllTypes.FIELD_DATE, new Date());
            allTypes.setDouble(AllTypes.FIELD_DOUBLE, 3.1415D + i);
            allTypes.setFloat(AllTypes.FIELD_FLOAT, 1.234567F + i);
            allTypes.setString(AllTypes.FIELD_STRING, "test data " + i);
            allTypes.setLong(AllTypes.FIELD_LONG, i);
            allTypes.getList(AllTypes.FIELD_REALMLIST).add(realm.createObject(Dog.CLASS_NAME));
            allTypes.getList(AllTypes.FIELD_REALMLIST).add(realm.createObject(Dog.CLASS_NAME));
        }
        realm.commitTransaction();
        if (autoRefreshEnabled) {
            realm.setAutoRefresh(true);
        }
    }

    // Test that the SharedGroupManager is not reused across Realm/DynamicRealm on the same thread.
    // This is done by starting a write transaction in one Realm and verifying that none of the data
    // written (but not committed) is available in the other Realm.
    @Test
    public void separateSharedGroups() {
        Realm typedRealm = Realm.getInstance(defaultConfig);
        DynamicRealm dynamicRealm = DynamicRealm.getInstance(defaultConfig);

        assertEquals(0, typedRealm.where(AllTypes.class).count());
        assertEquals(0, dynamicRealm.where(AllTypes.CLASS_NAME).count());

        typedRealm.beginTransaction();
        try {
            typedRealm.createObject(AllTypes.class);
            assertEquals(1, typedRealm.where(AllTypes.class).count());
            assertEquals(0, dynamicRealm.where(AllTypes.CLASS_NAME).count());
            typedRealm.cancelTransaction();
        } finally {
            typedRealm.close();
            dynamicRealm.close();
        }
    }

    // Test that Realms can only be deleted after all Typed and Dynamic instances are closed
    @Test
    public void deleteRealm_ThrowsIfDynamicRealmIsOpen() {
        realm.close(); // Close Realm opened in setUp();
        Realm typedRealm = Realm.getInstance(defaultConfig);
        DynamicRealm dynamicRealm = DynamicRealm.getInstance(defaultConfig);

        typedRealm.close();
        try {
            Realm.deleteRealm(defaultConfig);
            fail();
        } catch (IllegalStateException ignored) {
        }

        dynamicRealm.close();
        assertTrue(Realm.deleteRealm(defaultConfig));
    }

    // Test that Realms can only be deleted after all Typed and Dynamic instances are closed.
    @Test
    public void deleteRealm_throwsIfTypedRealmIsOpen() {
        realm.close(); // Close Realm opened in setUp();
        Realm typedRealm = Realm.getInstance(defaultConfig);
        DynamicRealm dynamicRealm = DynamicRealm.getInstance(defaultConfig);

        dynamicRealm.close();
        try {
            Realm.deleteRealm(defaultConfig);
            fail();
        } catch (IllegalStateException ignored) {
        }

        typedRealm.close();
        assertTrue(Realm.deleteRealm(defaultConfig));
    }

    @Test
    public void createObject() {
        realm.beginTransaction();
        DynamicRealmObject obj = realm.createObject(AllTypes.CLASS_NAME);
        realm.commitTransaction();
        assertTrue(obj.isValid());
    }

    @Test
    public void createObject_withPrimaryKey() {
        realm.beginTransaction();
        DynamicRealmObject dog = realm.createObject(DogPrimaryKey.CLASS_NAME, 42);
        assertEquals(42, dog.getLong("id"));
        realm.cancelTransaction();
    }

    @Test
    public void createObject_withNullStringPrimaryKey() {
        realm.beginTransaction();
        realm.createObject(PrimaryKeyAsString.CLASS_NAME, (String) null);
        realm.commitTransaction();

        assertEquals(1, realm.where(PrimaryKeyAsString.CLASS_NAME).equalTo(PrimaryKeyAsString.FIELD_PRIMARY_KEY, (String) null).count());
    }

    @Test
    public void createObject_withNullBytePrimaryKey() {
        realm.beginTransaction();
        realm.createObject(PrimaryKeyAsBoxedByte.CLASS_NAME, (Byte) null);
        realm.commitTransaction();

        assertEquals(1, realm.where(PrimaryKeyAsBoxedByte.CLASS_NAME).equalTo(PrimaryKeyAsBoxedByte.FIELD_PRIMARY_KEY, (Byte) null).count());
    }

    @Test
    public void createObject_withNullShortPrimaryKey() {
        realm.beginTransaction();
        realm.createObject(PrimaryKeyAsBoxedShort.CLASS_NAME, (Short) null);
        realm.commitTransaction();

        assertEquals(1, realm.where(PrimaryKeyAsBoxedShort.CLASS_NAME).equalTo(PrimaryKeyAsBoxedShort.FIELD_PRIMARY_KEY, (Short) null).count());
    }

    @Test
    public void createObject_withNullIntegerPrimaryKey() {
        realm.beginTransaction();
        realm.createObject(PrimaryKeyAsBoxedInteger.CLASS_NAME, (Integer) null);
        realm.commitTransaction();

        assertEquals(1, realm.where(PrimaryKeyAsBoxedInteger.CLASS_NAME).equalTo(PrimaryKeyAsBoxedInteger.FIELD_PRIMARY_KEY, (Integer) null).count());
    }

    @Test
    public void createObject_withNullLongPrimaryKey() {
        realm.beginTransaction();
        realm.createObject(PrimaryKeyAsBoxedLong.CLASS_NAME, (Long) null);
        realm.commitTransaction();

        assertEquals(1, realm.where(PrimaryKeyAsBoxedLong.CLASS_NAME).equalTo(PrimaryKeyAsBoxedLong.FIELD_PRIMARY_KEY, (Long) null).count());
    }

    @Test(expected = IllegalArgumentException.class)
    public void createObject_illegalPrimaryKeyValue() {
        realm.beginTransaction();
        realm.createObject(DogPrimaryKey.CLASS_NAME, "bar");
    }

    @Test
    public void where() {
        realm.beginTransaction();
        realm.createObject(AllTypes.CLASS_NAME);
        realm.commitTransaction();

        RealmResults<DynamicRealmObject> results = realm.where(AllTypes.CLASS_NAME).findAll();
        assertEquals(1, results.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void delete_type_invalidName() {
        realm.beginTransaction();
        realm.delete("I don't exist");
    }

    @Test(expected = IllegalStateException.class)
    public void delete_type_outsideTransactionClearOutsideTransactionThrows() {
        realm.delete(AllTypes.CLASS_NAME);
    }

    @Test
    public void delete_type() {
        realm.beginTransaction();
        realm.createObject(AllTypes.CLASS_NAME);
        realm.commitTransaction();

        assertEquals(1, realm.where(AllTypes.CLASS_NAME).count());
        realm.beginTransaction();
        realm.delete(AllTypes.CLASS_NAME);
        realm.commitTransaction();
        assertEquals(0, realm.where(AllTypes.CLASS_NAME).count());
    }

    @Test(expected = IllegalArgumentException.class)
    public void executeTransaction_null() {
        realm.executeTransaction(null);
    }

    @Test
    public void executeTransaction() {
        assertEquals(0, realm.where(Owner.CLASS_NAME).count());
        realm.executeTransaction(new DynamicRealm.Transaction() {
            @Override
            public void execute(DynamicRealm realm) {
                DynamicRealmObject owner = realm.createObject(Owner.CLASS_NAME);
                owner.setString("name", "Owner");
            }
        });

        RealmResults<DynamicRealmObject> allObjects = realm.where(Owner.CLASS_NAME).findAll();
        assertEquals(1, allObjects.size());
        assertEquals("Owner", allObjects.get(0).getString("name"));
    }

    @Test
    public void executeTransaction_cancelled() {
        final AtomicReference<RuntimeException> thrownException = new AtomicReference<>(null);

        assertEquals(0, realm.where(Owner.CLASS_NAME).count());
        try {
            realm.executeTransaction(new DynamicRealm.Transaction() {
                @Override
                public void execute(DynamicRealm realm) {
                    DynamicRealmObject owner = realm.createObject(Owner.CLASS_NAME);
                    owner.setString("name", "Owner");
                    thrownException.set(new RuntimeException("Boom"));
                    throw thrownException.get();
                }
            });
        } catch (RuntimeException e) {
            //noinspection ThrowableResultOfMethodCallIgnored
            assertTrue(e == thrownException.get());
        }
        assertEquals(0, realm.where(Owner.CLASS_NAME).count());
    }

    @Test
    public void executeTransaction_warningIfManuallyCancelled() {
        assertEquals(0, realm.where("Owner").count());
        TestHelper.TestLogger testLogger = new TestHelper.TestLogger();
        try {
            RealmLog.add(testLogger);
            realm.executeTransaction(new DynamicRealm.Transaction() {
                @Override
                public void execute(DynamicRealm realm) {
                    DynamicRealmObject owner = realm.createObject("Owner");
                    owner.setString("name", "Owner");
                    realm.cancelTransaction();
                    throw new RuntimeException("Boom");
                }
            });
        } catch (RuntimeException ignored) {
            // Ensure that we pass a valuable error message to the logger for developers.
            assertEquals(testLogger.message, "Could not cancel transaction, not currently in a transaction.");
        } finally {
            RealmLog.remove(testLogger);
        }
        assertEquals(0, realm.where("Owner").count());
    }

    @Test
    @RunTestInLooperThread
    public void findFirstAsync() {
        final DynamicRealm dynamicRealm = initializeDynamicRealm();
        final DynamicRealmObject allTypes = dynamicRealm.where(AllTypes.CLASS_NAME)
                .between(AllTypes.FIELD_LONG, 4, 9)
                .findFirstAsync();
        assertFalse(allTypes.isLoaded());
        allTypes.addChangeListener(new RealmChangeListener<DynamicRealmObject>() {
            @Override
            public void onChange(DynamicRealmObject object) {
                assertEquals("test data 4", allTypes.getString(AllTypes.FIELD_STRING));
                dynamicRealm.close();
                looperThread.testComplete();
            }
        });
    }

    @Test
    @RunTestInLooperThread
    public void findAllAsync() {
        final DynamicRealm dynamicRealm = initializeDynamicRealm();
        final RealmResults<DynamicRealmObject> allTypes = dynamicRealm.where(AllTypes.CLASS_NAME)
                .between(AllTypes.FIELD_LONG, 4, 9)
                .findAllAsync();

        assertFalse(allTypes.isLoaded());
        assertEquals(0, allTypes.size());

        allTypes.addChangeListener(new RealmChangeListener<RealmResults<DynamicRealmObject>>() {
            @Override
            public void onChange(RealmResults<DynamicRealmObject> object) {
                assertEquals(6, allTypes.size());
                for (int i = 0; i < allTypes.size(); i++) {
                    assertEquals("test data " + (4 + i), allTypes.get(i).getString(AllTypes.FIELD_STRING));
                }
                dynamicRealm.close();
                looperThread.testComplete();
            }
        });
    }

    @Test
    @RunTestInLooperThread
    public void findAllSortedAsync() {
        final DynamicRealm dynamicRealm = initializeDynamicRealm();
        final RealmResults<DynamicRealmObject> allTypes = dynamicRealm.where(AllTypes.CLASS_NAME)
                .between(AllTypes.FIELD_LONG, 0, 4)
                .findAllSortedAsync(AllTypes.FIELD_STRING, Sort.DESCENDING);
        assertFalse(allTypes.isLoaded());
        assertEquals(0, allTypes.size());

        allTypes.addChangeListener(new RealmChangeListener<RealmResults<DynamicRealmObject>>() {
            @Override
            public void onChange(RealmResults<DynamicRealmObject> object) {
                assertEquals(5, allTypes.size());
                for (int i = 0; i < 5; i++) {
                    int iteration = (4 - i);
                    assertEquals("test data " + iteration, allTypes.get(4 - iteration).getString(AllTypes.FIELD_STRING));
                }
                dynamicRealm.close();
                looperThread.testComplete();
            }
        });
    }

    // Initialize a Dynamic Realm used by the *Async tests.
    private DynamicRealm initializeDynamicRealm() {
        RealmConfiguration defaultConfig = looperThread.realmConfiguration;
        final DynamicRealm dynamicRealm = DynamicRealm.getInstance(defaultConfig);
        populateTestRealm(dynamicRealm, 10);
        return dynamicRealm;
    }

    @Test
    @RunTestInLooperThread
    public void findAllSortedAsync_usingMultipleFields() {
        final DynamicRealm dynamicRealm = initializeDynamicRealm();

        dynamicRealm.setAutoRefresh(false);
        dynamicRealm.beginTransaction();
        dynamicRealm.delete(AllTypes.CLASS_NAME);
        for (int i = 0; i < 5; ) {
            DynamicRealmObject allTypes = dynamicRealm.createObject(AllTypes.CLASS_NAME);
            allTypes.set(AllTypes.FIELD_LONG, i);
            allTypes.set(AllTypes.FIELD_STRING, "data " + i % 3);

            allTypes = dynamicRealm.createObject(AllTypes.CLASS_NAME);
            allTypes.set(AllTypes.FIELD_LONG, i);
            allTypes.set(AllTypes.FIELD_STRING, "data " + (++i % 3));
        }
        dynamicRealm.commitTransaction();
        dynamicRealm.setAutoRefresh(true);

        // Sort first set by using: String[ASC], Long[DESC]
        final RealmResults<DynamicRealmObject> realmResults1 = dynamicRealm.where(AllTypes.CLASS_NAME)
                .findAllSortedAsync(
                        new String[]{AllTypes.FIELD_STRING, AllTypes.FIELD_LONG},
                        new Sort[]{Sort.ASCENDING, Sort.DESCENDING}
                );

        // Sort second set by using: String[DESC], Long[ASC]
        final RealmResults<DynamicRealmObject> realmResults2 = dynamicRealm.where(AllTypes.CLASS_NAME)
                .between(AllTypes.FIELD_LONG, 0, 5)
                .findAllSortedAsync(
                        new String[]{AllTypes.FIELD_STRING, AllTypes.FIELD_LONG},
                        new Sort[]{Sort.DESCENDING, Sort.ASCENDING}
                );

        final Runnable signalCallbackDone = new Runnable() {
            final AtomicInteger callbacksDone = new AtomicInteger(2);
            @Override
            public void run() {
                if (callbacksDone.decrementAndGet() == 0) {
                    dynamicRealm.close();
                    looperThread.testComplete();
                }
            }
        };

        realmResults1.addChangeListener(new RealmChangeListener<RealmResults<DynamicRealmObject>>() {
            @Override
            public void onChange(RealmResults<DynamicRealmObject> object) {
                assertEquals("data 0", realmResults1.get(0).get(AllTypes.FIELD_STRING));
                assertEquals(3L, realmResults1.get(0).get(AllTypes.FIELD_LONG));
                assertEquals("data 0", realmResults1.get(1).get(AllTypes.FIELD_STRING));
                assertEquals(2L, realmResults1.get(1).get(AllTypes.FIELD_LONG));
                assertEquals("data 0", realmResults1.get(2).get(AllTypes.FIELD_STRING));
                assertEquals(0L, realmResults1.get(2).get(AllTypes.FIELD_LONG));

                assertEquals("data 1", realmResults1.get(3).get(AllTypes.FIELD_STRING));
                assertEquals(4L, realmResults1.get(3).get(AllTypes.FIELD_LONG));
                assertEquals("data 1", realmResults1.get(4).get(AllTypes.FIELD_STRING));
                assertEquals(3L, realmResults1.get(4).get(AllTypes.FIELD_LONG));
                assertEquals("data 1", realmResults1.get(5).get(AllTypes.FIELD_STRING));
                assertEquals(1L, realmResults1.get(5).get(AllTypes.FIELD_LONG));
                assertEquals("data 1", realmResults1.get(6).get(AllTypes.FIELD_STRING));
                assertEquals(0L, realmResults1.get(6).get(AllTypes.FIELD_LONG));

                assertEquals("data 2", realmResults1.get(7).get(AllTypes.FIELD_STRING));
                assertEquals(4L, realmResults1.get(7).get(AllTypes.FIELD_LONG));
                assertEquals("data 2", realmResults1.get(8).get(AllTypes.FIELD_STRING));
                assertEquals(2L, realmResults1.get(8).get(AllTypes.FIELD_LONG));
                assertEquals("data 2", realmResults1.get(9).get(AllTypes.FIELD_STRING));
                assertEquals(1L, realmResults1.get(9).get(AllTypes.FIELD_LONG));

                signalCallbackDone.run();
            }
        });

        realmResults2.addChangeListener(new RealmChangeListener<RealmResults<DynamicRealmObject>>() {
            @Override
            public void onChange(RealmResults<DynamicRealmObject> object) {
                assertEquals("data 2", realmResults2.get(0).get(AllTypes.FIELD_STRING));
                assertEquals(1L, realmResults2.get(0).get(AllTypes.FIELD_LONG));
                assertEquals("data 2", realmResults2.get(1).get(AllTypes.FIELD_STRING));
                assertEquals(2L, realmResults2.get(1).get(AllTypes.FIELD_LONG));
                assertEquals("data 2", realmResults2.get(2).get(AllTypes.FIELD_STRING));
                assertEquals(4L, realmResults2.get(2).get(AllTypes.FIELD_LONG));

                assertEquals("data 1", realmResults2.get(3).get(AllTypes.FIELD_STRING));
                assertEquals(0L, realmResults2.get(3).get(AllTypes.FIELD_LONG));
                assertEquals("data 1", realmResults2.get(4).get(AllTypes.FIELD_STRING));
                assertEquals(1L, realmResults2.get(4).get(AllTypes.FIELD_LONG));
                assertEquals("data 1", realmResults2.get(5).get(AllTypes.FIELD_STRING));
                assertEquals(3L, realmResults2.get(5).get(AllTypes.FIELD_LONG));
                assertEquals("data 1", realmResults2.get(6).get(AllTypes.FIELD_STRING));
                assertEquals(4L, realmResults2.get(6).get(AllTypes.FIELD_LONG));

                assertEquals("data 0", realmResults2.get(7).get(AllTypes.FIELD_STRING));
                assertEquals(0L, realmResults2.get(7).get(AllTypes.FIELD_LONG));
                assertEquals("data 0", realmResults2.get(8).get(AllTypes.FIELD_STRING));
                assertEquals(2L, realmResults2.get(8).get(AllTypes.FIELD_LONG));
                assertEquals("data 0", realmResults2.get(9).get(AllTypes.FIELD_STRING));
                assertEquals(3L, realmResults2.get(9).get(AllTypes.FIELD_LONG));

                signalCallbackDone.run();
            }
        });
    }

    @Test
    @RunTestInLooperThread
    public void accessingDynamicRealmObjectBeforeAsyncQueryCompleted() {
        final DynamicRealm dynamicRealm = initializeDynamicRealm();
        final DynamicRealmObject[] dynamicRealmObject = new DynamicRealmObject[1];

        // Intercept completion of the async DynamicRealmObject query
        Handler handler = new HandlerProxy(dynamicRealm.handlerController) {
            @Override
            public boolean onInterceptInMessage(int what) {
                switch (what) {
                    case HandlerController.COMPLETED_ASYNC_REALM_OBJECT: {
                        post(new Runnable() {
                            @Override
                            public void run() {
                                assertFalse(dynamicRealmObject[0].isLoaded());
                                assertFalse(dynamicRealmObject[0].isValid());
                                try {
                                    dynamicRealmObject[0].getObject(AllTypes.FIELD_BINARY);
                                    fail("trying to access a DynamicRealmObject property should throw");
                                } catch (IllegalStateException ignored) {

                                } finally {
                                    dynamicRealm.close();
                                    looperThread.testComplete();
                                }
                            }
                        });
                        return true;
                    }
                }
                return false;
            }
        };

        dynamicRealm.setHandler(handler);
        dynamicRealmObject[0] = dynamicRealm.where(AllTypes.CLASS_NAME)
                .between(AllTypes.FIELD_LONG, 4, 9)
                .findFirstAsync();
    }

    @Test
    public void deleteAll() {
        realm.beginTransaction();
        realm.createObject(AllTypes.CLASS_NAME);
        DynamicRealmObject cat = realm.createObject(Cat.CLASS_NAME);
        DynamicRealmObject owner = realm.createObject(Owner.CLASS_NAME);
        owner.setObject("cat", cat);
        realm.getSchema().create("TestRemoveAll").addField("Field1", String.class);
        realm.createObject("TestRemoveAll");
        realm.commitTransaction();

        assertEquals(1, realm.where(AllTypes.CLASS_NAME).count());
        assertEquals(1, realm.where(Owner.CLASS_NAME).count());
        assertEquals(1, realm.where(Cat.CLASS_NAME).count());
        assertEquals(1, realm.where("TestRemoveAll").count());

        realm.beginTransaction();
        realm.deleteAll();
        realm.commitTransaction();

        assertEquals(0, realm.where(AllTypes.CLASS_NAME).count());
        assertEquals(0, realm.where(Owner.CLASS_NAME).count());
        assertEquals(0, realm.where(Cat.CLASS_NAME).count());
        assertEquals(0, realm.where("TestRemoveAll").count());
        assertTrue(realm.isEmpty());
    }

    @Test
    public void realmListRemoveAllFromRealm() {
        populateTestRealm(realm, 1);
        RealmList<DynamicRealmObject> list = realm.where(AllTypes.CLASS_NAME).findFirst().getList(AllTypes.FIELD_REALMLIST);
        assertEquals(2, list.size());

        realm.beginTransaction();
        list.deleteAllFromRealm();
        realm.commitTransaction();

        assertEquals(0, list.size());
        assertEquals(0, realm.where(Dog.CLASS_NAME).count());
    }
}
