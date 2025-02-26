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

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Date;

import io.realm.entities.AllTypes;
import io.realm.entities.pojo.AllTypesRealmModel;
import io.realm.entities.pojo.InvalidRealmModel;
import io.realm.entities.pojo.PojoWithRealmListOfRealmObject;
import io.realm.entities.pojo.RealmModelWithRealmListOfRealmModel;
import io.realm.entities.pojo.RealmModelWithRealmModelField;
import io.realm.entities.pojo.RealmObjectWithRealmListOfRealmModel;
import io.realm.entities.pojo.RealmObjectWithRealmModelField;
import io.realm.exceptions.RealmException;
import io.realm.rule.RunInLooperThread;
import io.realm.rule.RunTestInLooperThread;
import io.realm.rule.TestRealmConfigurationFactory;

import static io.realm.internal.test.ExtraTests.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

// tests API methods when using a model class implementing RealmModel instead
// of extending RealmObject.
@RunWith(AndroidJUnit4.class)
public class RealmModelTests {
    private final static int TEST_DATA_SIZE = 10;

    private Context context;
    private Realm realm;

    @Rule
    public final TestRealmConfigurationFactory configFactory = new TestRealmConfigurationFactory();

    @Rule
    public final RunInLooperThread looperThread = new RunInLooperThread();

    @Before
    public void setUp() {
        // Injecting the Instrumentation instance is required
        // for your test to run with AndroidJUnitRunner.
        context = InstrumentationRegistry.getInstrumentation().getContext();
        RealmConfiguration realmConfig = configFactory.createConfiguration();
        realm = Realm.getInstance(realmConfig);
    }

    @After
    public void tearDown() {
        if (realm != null) {
            realm.close();
        }
    }

    private void populateTestRealm(Realm realm, int objects) {
        realm.beginTransaction();
        realm.delete(AllTypesRealmModel.class);
        for (int i = 0; i < objects; ++i) {
            AllTypesRealmModel allTypes = new AllTypesRealmModel();
            allTypes.columnLong = i;
            allTypes.columnBoolean = (i % 3) == 0;
            allTypes.columnBinary = new byte[]{1, 2, 3};
            allTypes.columnDate = new Date();
            allTypes.columnDouble = 3.1415 + i;
            allTypes.columnFloat = 1.234567f;
            allTypes.columnString = "test data ";
            allTypes.columnByte = 0b0010_1010;
            realm.copyToRealm(allTypes);
        }
        realm.commitTransaction();
    }

    @Test
    public void createObject() {
        for (int i = 1; i < 43; i++) { // using i = 0 as PK will crash subsequent createObject
                                       // since createObject uses default values
            realm.beginTransaction();
            AllTypesRealmModel allTypesRealmModel = realm.createObject(AllTypesRealmModel.class);
            allTypesRealmModel.columnLong = i;
            realm.commitTransaction();
        }

        RealmResults<AllTypesRealmModel> resultList = realm.allObjects(AllTypesRealmModel.class);
        assertEquals("Realm.get is returning wrong result set", 42, resultList.size());
    }

    @Test
    public void copyToRealm() {
        populateTestRealm(realm, TEST_DATA_SIZE);
        RealmResults<AllTypesRealmModel> resultList = realm.allObjects(AllTypesRealmModel.class);
        assertEquals("Realm.get is returning wrong result set", TEST_DATA_SIZE, resultList.size());
    }


    @Test
    public void copyFromRealm() {
        populateTestRealm(realm, TEST_DATA_SIZE);

        AllTypesRealmModel realmObject = realm.where(AllTypesRealmModel.class).findAllSorted(AllTypesRealmModel.FIELD_LONG).first();
        AllTypesRealmModel standaloneObject = realm.copyFromRealm(realmObject);
        assertArrayEquals(realmObject.columnBinary, standaloneObject.columnBinary);
        assertEquals(realmObject.columnString, standaloneObject.columnString);
        assertEquals(realmObject.columnLong, standaloneObject.columnLong);
        assertEquals(realmObject.columnFloat, standaloneObject.columnFloat, 0.00000000001);
        assertEquals(realmObject.columnDouble, standaloneObject.columnDouble, 0.00000000001);
        assertEquals(realmObject.columnBoolean, standaloneObject.columnBoolean);
        assertEquals(realmObject.columnDate, standaloneObject.columnDate);
        assertEquals(realmObject.hashCode(), standaloneObject.hashCode());

    }

    @Test
    public void copyToRealmOrUpdate() {
        realm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                AllTypesRealmModel obj = new AllTypesRealmModel();
                obj.columnLong = 1;
                realm.copyToRealm(obj);

                AllTypesRealmModel obj2 = new AllTypesRealmModel();
                obj2.columnLong = 1;
                obj2.columnString = "Foo";
                realm.copyToRealmOrUpdate(obj2);
            }
        });

        assertEquals(1, realm.allObjects(AllTypesRealmModel.class).size());

        AllTypesRealmModel obj = realm.allObjects(AllTypesRealmModel.class).first();
        assertEquals("Foo", obj.columnString);
    }

    @Test
    public void createOrUpdateAllFromJson() throws IOException {
        realm.beginTransaction();
        realm.createOrUpdateAllFromJson(AllTypesRealmModel.class, TestHelper.loadJsonFromAssets(context, "list_alltypes_primarykey.json"));
        realm.commitTransaction();

        assertEquals(1, realm.allObjects(AllTypesRealmModel.class).size());
        AllTypesRealmModel obj = realm.allObjects(AllTypesRealmModel.class).first();
        assertEquals("Bar", obj.columnString);
        assertEquals(2.23F, obj.columnFloat, 0.000000001);
        assertEquals(2.234D, obj.columnDouble, 0.000000001);
        assertEquals(true, obj.columnBoolean);
        assertArrayEquals(new byte[]{1, 2, 3}, obj.columnBinary);
        assertEquals(new Date(2000), obj.columnDate);
        assertEquals("Dog4", obj.columnRealmObject.getName());
        assertEquals(2, obj.columnRealmList.size());
        assertEquals("Dog5", obj.columnRealmList.get(0).getName());
    }

    // where with filed selection
    @Test
    public void query() {
        populateTestRealm(realm, TEST_DATA_SIZE);

        assertEquals(5, realm.where(AllTypesRealmModel.class).greaterThanOrEqualTo(AllTypesRealmModel.FIELD_DOUBLE, 8.1415).count());
    }

    // async where with filed selection
    @Test
    @RunTestInLooperThread
    public void async_query() {
        populateTestRealm(looperThread.realm, TEST_DATA_SIZE);

        final RealmResults<AllTypesRealmModel> allTypesRealmModels = looperThread.realm.distinctAsync(AllTypesRealmModel.class, AllTypesRealmModel.FIELD_STRING);
        allTypesRealmModels.addChangeListener(new RealmChangeListener<RealmResults<AllTypesRealmModel>>() {
            @Override
            public void onChange(RealmResults<AllTypesRealmModel> object) {
                assertEquals(1, allTypesRealmModels.size());
                looperThread.testComplete();
            }
        });
    }

    @Test
    public void dynamicObject() {
        populateTestRealm(realm, TEST_DATA_SIZE);

        AllTypesRealmModel typedObj = realm.allObjects(AllTypesRealmModel.class).first();
        DynamicRealmObject dObj = new DynamicRealmObject(typedObj);

        realm.beginTransaction();
        dObj.setLong(AllTypesRealmModel.FIELD_LONG, 42L);
        assertEquals(42, dObj.getLong(AllTypesRealmModel.FIELD_LONG));
        assertEquals(42, typedObj.columnLong);

        dObj.setBlob(AllTypesRealmModel.FIELD_BINARY, new byte[]{1, 2, 3});
        Assert.assertArrayEquals(new byte[]{1, 2, 3}, dObj.getBlob(AllTypesRealmModel.FIELD_BINARY));
        Assert.assertArrayEquals(new byte[]{1, 2, 3}, typedObj.columnBinary);
        realm.cancelTransaction();
    }

    @Test
    @RunTestInLooperThread
    public void dynamicRealm() {
        populateTestRealm(looperThread.realm, TEST_DATA_SIZE);
        final DynamicRealm dynamicRealm = DynamicRealm.getInstance(looperThread.realmConfiguration);

        dynamicRealm.beginTransaction();
        DynamicRealmObject dog = dynamicRealm.createObject(AllTypesRealmModel.CLASS_NAME, 42);
        assertEquals(42, dog.getLong(AllTypesRealmModel.FIELD_LONG));
        dynamicRealm.commitTransaction();

        RealmResults<DynamicRealmObject> allAsync = dynamicRealm.where(AllTypesRealmModel.CLASS_NAME).equalTo(AllTypesRealmModel.FIELD_LONG, 42).findAll();
        allAsync.load();
        assertTrue(allAsync.isLoaded());
        assertEquals(1, allAsync.size());

        dynamicRealm.beginTransaction();
        allAsync.deleteAllFromRealm();
        dynamicRealm.commitTransaction();

        RealmResults<DynamicRealmObject> results = dynamicRealm.where(AllTypesRealmModel.CLASS_NAME).findAll();
        assertEquals(TEST_DATA_SIZE, results.size());
        for (int i = 0; i < TEST_DATA_SIZE; i++) {
            assertEquals(3.1415 + i, results.get(i).getDouble(AllTypesRealmModel.FIELD_DOUBLE), 0.0000001);
            assertEquals((i % 3) == 0, results.get(i).getBoolean(AllTypesRealmModel.FIELD_BOOLEAN));
        }
        dynamicRealm.close();
        looperThread.testComplete();
    }

    // exception expected when using in schema model not annotated
    // a valid model need to implement the interface RealmModel and annotate the class with @RealmClass
    // we expect in this test a runtime exception 'InvalidRealmModel is not part of the schema for this Realm.'
    @Test(expected = RealmException.class)
    public void invalidModelDefinition() {
        realm.beginTransaction();
        realm.createObject(InvalidRealmModel.class);
        realm.commitTransaction();
    }

    // Test the behaviour of a RealmModel, containing a RealmList
    // of other RealmModel, in managed and un-managed mode
    @Test
    public void realmModelWithRealmListOfRealmModel() {
        RealmList<AllTypesRealmModel> allTypesRealmModels = new RealmList<AllTypesRealmModel>();
        AllTypesRealmModel allTypePojo;
        for (int i = 0; i < 10; i++) {
            allTypePojo = new AllTypesRealmModel();
            allTypePojo.columnLong = i;
            allTypesRealmModels.add(allTypePojo);
        }
        AllTypesRealmModel pojo1 = allTypesRealmModels.get(1);
        assertEquals(1, pojo1.columnLong);
        allTypesRealmModels.move(1, 0);
        assertEquals(0, allTypesRealmModels.indexOf(pojo1));

        RealmModelWithRealmListOfRealmModel model = new RealmModelWithRealmListOfRealmModel();
        model.setColumnRealmList(allTypesRealmModels);

        realm.beginTransaction();
        realm.copyToRealm(model);
        realm.commitTransaction();

        RealmResults<RealmModelWithRealmListOfRealmModel> all = realm.where(RealmModelWithRealmListOfRealmModel.class).findAll();
        assertEquals(1, all.size());
        assertEquals(10, all.first().getColumnRealmList().size());
        assertEquals(1, all.first().getColumnRealmList().first().columnLong);
    }

    // Test the behaviour of a RealmModel, containing a RealmList
    // of RealmObject, in managed and un-managed mode
    @Test
    public void realmModelWithRealmListOfRealmObject() {
        RealmList<AllTypes> allTypes = new RealmList<AllTypes>();
        AllTypes allType;
        for (int i = 0; i < 10; i++) {
            allType = new AllTypes();
            allType.setColumnLong(i);
            allTypes.add(allType);
        }
        AllTypes pojo1 = allTypes.get(1);
        assertEquals(1, pojo1.getColumnLong());
        allTypes.move(1, 0);
        assertEquals(0, allTypes.indexOf(pojo1));

        PojoWithRealmListOfRealmObject model = new PojoWithRealmListOfRealmObject();
        model.setColumnRealmList(allTypes);

        realm.beginTransaction();
        realm.copyToRealm(model);
        realm.commitTransaction();

        RealmResults<PojoWithRealmListOfRealmObject> all = realm.where(PojoWithRealmListOfRealmObject.class).findAll();
        assertEquals(1, all.size());
        assertEquals(10, all.first().getColumnRealmList().size());
        assertEquals(1, all.first().getColumnRealmList().first().getColumnLong());
    }

    // Test the behaviour of a RealmObject, containing a RealmList
    // of RealmModel, in managed and un-managed mode
    @Test
    public void realmObjectWithRealmListOfRealmModel() {
        RealmList<AllTypesRealmModel> allTypesRealmModel = new RealmList<AllTypesRealmModel>();
        AllTypesRealmModel allTypePojo;
        for (int i = 0; i < 10; i++) {
            allTypePojo = new AllTypesRealmModel();
            allTypePojo.columnLong = i;
            allTypesRealmModel.add(allTypePojo);
        }
        AllTypesRealmModel pojo1 = allTypesRealmModel.get(1);
        assertEquals(1, pojo1.columnLong);
        allTypesRealmModel.move(1, 0);
        assertEquals(0, allTypesRealmModel.indexOf(pojo1));

        RealmObjectWithRealmListOfRealmModel model = new RealmObjectWithRealmListOfRealmModel();
        model.setColumnRealmList(allTypesRealmModel);

        realm.beginTransaction();
        realm.copyToRealm(model);
        realm.commitTransaction();

        RealmResults<RealmObjectWithRealmListOfRealmModel> all = realm.where(RealmObjectWithRealmListOfRealmModel.class).findAll();
        assertEquals(1, all.size());
        assertEquals(10, all.first().getColumnRealmList().size());
        assertEquals(1, all.first().getColumnRealmList().first().columnLong);
    }

    // Test the behaviour of a RealmModel, containing a RealmModel field
    @Test
    public void realmModelWithRealmModelField() {
        RealmModelWithRealmModelField realmModelWithRealmModelField = new RealmModelWithRealmModelField();
        AllTypesRealmModel allTypePojo = new AllTypesRealmModel();
        allTypePojo.columnLong = 42;
        realmModelWithRealmModelField.setAllTypesRealmModel(allTypePojo);

        realm.beginTransaction();
        realm.copyToRealm(realmModelWithRealmModelField);
        realm.commitTransaction();

        RealmResults<RealmModelWithRealmModelField> all = realm.where(RealmModelWithRealmModelField.class).findAll();
        assertEquals(1, all.size());
        assertEquals(42, all.first().getAllTypesRealmModel().columnLong);
    }

    // Test the behaviour of a RealmObject, containing a RealmModel field
    @Test
    public void realmObjectWithRealmModelField() {
        RealmObjectWithRealmModelField realmObjectWithRealmModelField = new RealmObjectWithRealmModelField();
        AllTypesRealmModel allTypePojo = new AllTypesRealmModel();
        allTypePojo.columnLong = 42;
        realmObjectWithRealmModelField.setAllTypesRealmModel(allTypePojo);

        realm.beginTransaction();
        realm.copyToRealm(realmObjectWithRealmModelField);
        realm.commitTransaction();

        RealmResults<RealmObjectWithRealmModelField> all = realm.where(RealmObjectWithRealmModelField.class).findAll();
        assertEquals(1, all.size());
        assertEquals(42, all.first().getAllTypesRealmModel().columnLong);
    }
}

